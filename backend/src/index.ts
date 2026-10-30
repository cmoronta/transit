import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { authenticateRequest, AuthVerifyOptions } from "./auth.js";
import { buildRouteCacheKey, getCachedRoute, putCachedRoute } from "./cache.js";
import { QuotaLimiterDO } from "./quota-do.js";
import { Env, QuotaDecision, QuotaSnapshot, VerifiedCaller } from "./types.js";

export { QuotaLimiterDO };

export interface WorkerDeps {
  authOptions?: AuthVerifyOptions;
  upstreamFetch?: typeof fetch;
  nowMs?: () => number;
}

type HonoEnv = {
  Bindings: Env;
  Variables: {
    caller: VerifiedCaller;
    deps: WorkerDeps;
  };
};

const TRANSIT_V4_PLAN_URL = "https://external.transitapp.com/v4/public/plan";

const planQuerySchema = z.object({
  from_lat: z.coerce.number().min(-90).max(90),
  from_lon: z.coerce.number().min(-180).max(180),
  to_lat: z.coerce.number().min(-90).max(90),
  to_lon: z.coerce.number().min(-180).max(180),
  num_result: z.string().optional().default("3"),
  locale: z.string().optional(),
});

export function createApp(defaultDeps: WorkerDeps = {}) {
  const app = new Hono<HonoEnv>();

  // Inject dependencies (for testability)
  app.use("*", async (c, next) => {
    c.set("deps", defaultDeps);
    await next();
  });

  // Public health endpoint
  app.get("/v1/health", (c) => {
    return c.json({ ok: true, service: "transit-bike-proxy", version: "0.1.0" });
  });

  // Express-style auth middleware for all protected /v1/* routes
  app.use("/v1/*", async (c, next) => {
    if (c.req.path === "/v1/health") {
      return next();
    }
    const deps = c.get("deps");
    const nowMs = deps.nowMs ? deps.nowMs() : Date.now();
    try {
      const caller = await authenticateRequest(c.req.raw, c.env, {
        ...deps.authOptions,
        currentDate: deps.authOptions?.currentDate ?? new Date(nowMs),
      });
      c.set("caller", caller);
      await next();
    } catch (err) {
      return c.json(
        {
          error: "UNAUTHORIZED",
          message: err instanceof Error ? err.message : "Authentication failed",
        },
        401
      );
    }
  });

  // Admin-only diagnostics endpoint (requires custom claim { admin: true })
  app.get("/v1/admin/quota", async (c) => {
    const caller = c.get("caller");
    if (!caller.isAdmin) {
      return c.json({ error: "FORBIDDEN", message: "Admin custom claim required" }, 403);
    }

    const deps = c.get("deps");
    const nowMs = deps.nowMs ? deps.nowMs() : Date.now();
    const doId = c.env.QUOTA_DO.idFromName("global-quota");
    const quotaStub = c.env.QUOTA_DO.get(doId);

    const doResp = await quotaStub.fetch(`https://quota-do/status?nowMs=${nowMs}`);
    const snapshot = (await doResp.json()) as QuotaSnapshot;

    return c.json({
      adminUid: caller.uid,
      quota: snapshot,
    });
  });

  // Main bike route planning endpoint proxying Transit API v4 (/v4/public/plan)
  app.get(
    "/v1/plan",
    zValidator("query", planQuerySchema, (result, c) => {
      if (!result.success) {
        return c.json(
          { error: "BAD_REQUEST", message: "Invalid query parameters", issues: result.error.issues },
          400
        );
      }
    }),
    async (c) => {
      const { from_lat, from_lon, to_lat, to_lon, num_result, locale } = c.req.valid("query");
      const caller = c.get("caller");
      const deps = c.get("deps");
      const nowMs = deps.nowMs ? deps.nowMs() : Date.now();
      const upstreamFetch = deps.upstreamFetch ?? fetch;

      // 1. Check 24h edge KV cache first (~100m grid key -> 0 quota consumed on cache hit!)
      const cacheKey = buildRouteCacheKey(from_lat, from_lon, to_lat, to_lon, "personal_bike");
      const cachedJson = await getCachedRoute(c.env.ROUTE_KV, cacheKey);
      if (cachedJson) {
        return c.body(cachedJson, 200, {
          "Content-Type": "application/json",
          "X-Cache": "HIT",
          "X-Cache-Key": cacheKey,
        });
      }

      // 2. Cache miss -> Atomically check & increment quota in QuotaLimiterDO
      const doId = c.env.QUOTA_DO.idFromName("global-quota");
      const quotaStub = c.env.QUOTA_DO.get(doId);
      const quotaResp = await quotaStub.fetch("https://quota-do/consume", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uid: caller.uid, nowMs }),
      });
      const decision = (await quotaResp.json()) as QuotaDecision;

      if (!decision.allowed) {
        return c.json(
          {
            error: decision.reason ?? "QUOTA_EXCEEDED",
            monthlyUsed: decision.monthlyUsed,
            monthlyLimit: decision.monthlyLimit,
            minuteUsed: decision.minuteUsed,
            minuteLimit: decision.minuteLimit,
            userDailyUsed: decision.userDailyUsed,
            userDailyLimit: decision.userDailyLimit,
          },
          429,
          {
            "X-Quota-Used": String(decision.monthlyUsed),
            "X-Quota-Limit": String(decision.monthlyLimit),
            "X-User-Daily-Used": String(decision.userDailyUsed),
          }
        );
      }

      // 3. Call upstream Transit API v4 (/v4/public/plan) with mode=personal_bike + safety + directions
      const upstreamParams = new URLSearchParams({
        mode: "personal_bike",
        from_lat: String(from_lat),
        from_lon: String(from_lon),
        to_lat: String(to_lat),
        to_lon: String(to_lon),
        should_include_directions: "true",
        should_include_safety: "true",
        num_result,
      });

      if (locale) {
        upstreamParams.set("locale", locale);
      }

      const upstreamResp = await upstreamFetch(
        `${TRANSIT_V4_PLAN_URL}?${upstreamParams.toString()}`,
        {
          method: "GET",
          headers: {
            apiKey: c.env.TRANSIT_API_KEY,
            Accept: "application/json",
          },
        }
      );

      const upstreamText = await upstreamResp.text();
      if (!upstreamResp.ok) {
        return c.json(
          {
            error: "UPSTREAM_TRANSIT_ERROR",
            upstreamStatus: upstreamResp.status,
            details: upstreamText,
          },
          502
        );
      }

      // 4. Store in 24h KV cache and return with quota headers
      await putCachedRoute(c.env.ROUTE_KV, cacheKey, upstreamText);

      return c.body(upstreamText, 200, {
        "Content-Type": "application/json",
        "X-Cache": "MISS",
        "X-Cache-Key": cacheKey,
        "X-Quota-Used": String(decision.monthlyUsed),
        "X-Quota-Limit": String(decision.monthlyLimit),
        "X-User-Daily-Used": String(decision.userDailyUsed),
      });
    }
  );

  return app;
}

export async function handleWorkerRequest(
  request: Request,
  env: Env,
  deps: WorkerDeps = {}
): Promise<Response> {
  const app = createApp(deps);
  return app.fetch(request, env);
}

const defaultApp = createApp();
export default defaultApp;
