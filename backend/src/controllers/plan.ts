import { Context } from "hono";
import { z } from "zod";
import { zValidator } from "@hono/zod-validator";
import { buildRouteCacheKey, getCachedRoute, putCachedRoute } from "../cache.js";
import { consumeQuota } from "../services/quota.js";
import { fetchTransitBikePlan } from "../services/transit.js";
import { HonoEnv, QuotaDecision } from "../types.js";

export const planQuerySchema = z.object({
  from_lat: z.coerce.number().min(-90).max(90),
  from_lon: z.coerce.number().min(-180).max(180),
  to_lat: z.coerce.number().min(-90).max(90),
  to_lon: z.coerce.number().min(-180).max(180),
  num_result: z.string().optional().default("3"),
  locale: z.string().optional(),
});

export type PlanQueryParams = z.infer<typeof planQuerySchema>;

/**
 * Validates query parameters for bike route planning.
 */
export const validatePlanQuery = zValidator("query", planQuerySchema, (result, c) => {
  if (!result.success) {
    return c.json(
      {
        error: "BAD_REQUEST",
        message: "Invalid query parameters",
        issues: result.error.issues,
      },
      400
    );
  }
});

/**
 * Builds standard quota rate-limiting headers for client visibility.
 */
export function buildQuotaHeaders(decision: QuotaDecision): Record<string, string> {
  return {
    "X-Quota-Used": String(decision.monthlyUsed),
    "X-Quota-Limit": String(decision.monthlyLimit),
    "X-User-Daily-Used": String(decision.userDailyUsed),
  };
}

/**
 * Controller for GET /v1/plan:
 * 1. Check 24h KV edge cache (~100m grid cell) -> 0 quota on HIT
 * 2. Check & consume quota in QuotaLimiterDO -> 429 if exceeded
 * 3. Fetch bike route from Transit API v4 (/v4/public/plan) -> 502 on upstream failure
 * 4. Cache response in KV and return with quota headers
 */
export async function handlePlanRoute(c: Context<HonoEnv>) {
  const query = c.req.valid("query" as never) as PlanQueryParams;
  const caller = c.get("caller");
  const deps = c.get("deps");
  const nowMs = deps?.nowMs ? deps.nowMs() : Date.now();
  const upstreamFetch = deps?.upstreamFetch ?? fetch;

  const cacheKey = buildRouteCacheKey(
    query.from_lat,
    query.from_lon,
    query.to_lat,
    query.to_lon,
    "personal_bike"
  );

  // 1. Check 24h edge KV cache
  const cachedJson = await getCachedRoute(c.env.ROUTE_KV, cacheKey);
  if (cachedJson) {
    return c.body(cachedJson, 200, {
      "Content-Type": "application/json",
      "X-Cache": "HIT",
      "X-Cache-Key": cacheKey,
    });
  }

  // 2. Cache miss: check and consume quota
  const decision = await consumeQuota(c.env.QUOTA_DO, caller.uid, nowMs);
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
      buildQuotaHeaders(decision)
    );
  }

  // 3. Request bike route with safety analysis from upstream Transit API v4
  const upstreamResult = await fetchTransitBikePlan(
    c.env.TRANSIT_API_KEY,
    query,
    upstreamFetch
  );

  if (!upstreamResult.ok) {
    return c.json(
      {
        error: "UPSTREAM_TRANSIT_ERROR",
        upstreamStatus: upstreamResult.status,
        details: upstreamResult.details,
      },
      502
    );
  }

  // 4. Cache response in KV for 24 hours and return
  await putCachedRoute(c.env.ROUTE_KV, cacheKey, upstreamResult.data);

  return c.body(upstreamResult.data, 200, {
    "Content-Type": "application/json",
    "X-Cache": "MISS",
    "X-Cache-Key": cacheKey,
    ...buildQuotaHeaders(decision),
  });
}
