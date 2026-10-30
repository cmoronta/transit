import { Context } from "hono";
import { getQuotaStatus } from "../services/quota.js";
import { HonoEnv } from "../types.js";

/**
 * Admin diagnostics endpoint returning live monthly and minute quota telemetry.
 * Protected by requireAdmin middleware.
 */
export async function handleAdminQuota(c: Context<HonoEnv>) {
  const caller = c.get("caller");
  const deps = c.get("deps");
  const nowMs = deps?.nowMs ? deps.nowMs() : Date.now();

  const snapshot = await getQuotaStatus(c.env.QUOTA_DO, nowMs);

  return c.json({
    adminUid: caller.uid,
    quota: snapshot,
  });
}
