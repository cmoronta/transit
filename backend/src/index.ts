import { authenticateRequest, defaultJwksFetcher, JwksFetcher } from "./auth.js";
import { buildRouteCacheKey, getCachedRoute, putCachedRoute } from "./cache.js";
import { QuotaLimiterDO } from "./quota-do.js";
import { Env, QuotaDecision, QuotaSnapshot } from "./types.js";

export { QuotaLimiterDO };

export interface WorkerDeps {
  jwksFetcher?: JwksFetcher;
  upstreamFetch?: typeof fetch;
  nowMs?: () => number;
}

const TRANSIT_V4_PLAN_URL = "https://external.transitapp.com/v4/public/plan";

function jsonResponse(body: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...extraHeaders,
    },
  });
}

export async function handleWorkerRequest(
  request: Request,
  env: Env,
  deps: WorkerDeps = {}
): Promise<Response> {
  const url = new URL(request.url);
  const nowMs = deps.nowMs ? deps.nowMs() : Date.now();
  const nowSeconds = Math.floor(nowMs / 1000);
  const jwksFetcher = deps.jwksFetcher ?? defaultJwksFetcher;
  const upstreamFetch = deps.upstreamFetch ?? fetch;

  if (request.method === "GET" && url.pathname === "/v1/health") {
    return jsonResponse({ ok: true, service: "transit-bike-proxy", version: "0.1.0" });
  }

  // All other routes require Firebase Auth + Firebase App Check (Play Integrity)
  let caller;
  try {
    caller = await authenticateRequest(request, env, jwksFetcher, nowSeconds);
  } catch (err) {
    return jsonResponse(
      { error: "UNAUTHORIZED", message: err instanceof Error ? err.message : "Authentication failed" },
      401
    );
  }

  const doId = env.QUOTA_DO.idFromName("global-quota");
  const quotaStub = env.QUOTA_DO.get(doId);

  // Admin-only diagnostics endpoint (requires custom claim { admin: true })
  if (request.method === "GET" && url.pathname === "/v1/admin/quota") {
    if (!caller.isAdmin) {
      return jsonResponse(
        { error: "FORBIDDEN", message: "Admin custom claim required" },
        403
      );
    }

    const doResp = await quotaStub.fetch(`https://quota-do/status?nowMs=${nowMs}`);
    const snapshot = (await doResp.json()) as QuotaSnapshot;
    return jsonResponse({
      adminUid: caller.uid,
      quota: snapshot,
    });
  }

  // Main bike route planning endpoint proxying Transit API v4 (/v4/public/plan)
  if (request.method === "GET" && url.pathname === "/v1/plan") {
    const fromLat = Number(url.searchParams.get("from_lat"));
    const fromLon = Number(url.searchParams.get("from_lon"));
    const toLat = Number(url.searchParams.get("to_lat"));
    const toLon = Number(url.searchParams.get("to_lon"));

    if (
      !Number.isFinite(fromLat) ||
      !Number.isFinite(fromLon) ||
      !Number.isFinite(toLat) ||
      !Number.isFinite(toLon)
    ) {
      return jsonResponse(
        { error: "BAD_REQUEST", message: "Missing or invalid from_lat, from_lon, to_lat, to_lon" },
        400
      );
    }

    // 1. Check 24h edge KV cache first (~100m grid key -> 0 quota consumed on cache hit!)
    const cacheKey = buildRouteCacheKey(fromLat, fromLon, toLat, toLon, "personal_bike");
    const cachedJson = await getCachedRoute(env.ROUTE_KV, cacheKey);
    if (cachedJson) {
      return new Response(cachedJson, {
        status: 200,
        headers: {
          "Content-Type": "application/json",
          "X-Cache": "HIT",
          "X-Cache-Key": cacheKey,
        },
      });
    }

    // 2. Cache miss -> Atomically check & increment quota in QuotaLimiterDO
    const quotaResp = await quotaStub.fetch("https://quota-do/consume", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ uid: caller.uid, nowMs }),
    });
    const decision = (await quotaResp.json()) as QuotaDecision;

    if (!decision.allowed) {
      return jsonResponse(
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
      from_lat: String(fromLat),
      from_lon: String(fromLon),
      to_lat: String(toLat),
      to_lon: String(toLon),
      should_include_directions: "true",
      should_include_safety: "true",
      num_result: url.searchParams.get("num_result") ?? "3",
    });

    const locale = url.searchParams.get("locale");
    if (locale) {
      upstreamParams.set("locale", locale);
    }

    const upstreamResp = await upstreamFetch(`${TRANSIT_V4_PLAN_URL}?${upstreamParams.toString()}`, {
      method: "GET",
      headers: {
        apiKey: env.TRANSIT_API_KEY,
        Accept: "application/json",
      },
    });

    const upstreamText = await upstreamResp.text();
    if (!upstreamResp.ok) {
      return jsonResponse(
        {
          error: "UPSTREAM_TRANSIT_ERROR",
          upstreamStatus: upstreamResp.status,
          details: upstreamText,
        },
        502
      );
    }

    // 4. Store in 24h KV cache and return with quota headers
    await putCachedRoute(env.ROUTE_KV, cacheKey, upstreamText);

    return new Response(upstreamText, {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "X-Cache": "MISS",
        "X-Cache-Key": cacheKey,
        "X-Quota-Used": String(decision.monthlyUsed),
        "X-Quota-Limit": String(decision.monthlyLimit),
        "X-User-Daily-Used": String(decision.userDailyUsed),
      },
    });
  }

  return jsonResponse({ error: "NOT_FOUND" }, 404);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handleWorkerRequest(request, env);
  },
};
