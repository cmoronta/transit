import { QuotaDecision, QuotaSnapshot } from "../types.js";

const GLOBAL_QUOTA_NAME = "global-quota";

/**
 * Returns the Durable Object stub for the singleton global quota manager.
 */
export function getQuotaStub(namespace: DurableObjectNamespace): DurableObjectStub {
  const id = namespace.idFromName(GLOBAL_QUOTA_NAME);
  return namespace.get(id);
}

/**
 * Atomically checks and increments rate limits (per-minute, per-user daily, monthly)
 * via the QuotaLimiterDO Durable Object.
 */
export async function consumeQuota(
  namespace: DurableObjectNamespace,
  uid: string,
  nowMs: number
): Promise<QuotaDecision> {
  const stub = getQuotaStub(namespace);
  const response = await stub.fetch("https://quota-do/consume", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uid, nowMs }),
  });
  return (await response.json()) as QuotaDecision;
}

/**
 * Fetches current quota and rate limit telemetry from the QuotaLimiterDO Durable Object.
 */
export async function getQuotaStatus(
  namespace: DurableObjectNamespace,
  nowMs: number
): Promise<QuotaSnapshot> {
  const stub = getQuotaStub(namespace);
  const response = await stub.fetch(`https://quota-do/status?nowMs=${nowMs}`);
  return (await response.json()) as QuotaSnapshot;
}
