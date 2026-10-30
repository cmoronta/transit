import { Hono } from "hono";
import { QuotaLimiterDO } from "./quota-do.js";
import { authMiddleware, requireAdmin } from "./middleware/auth.js";
import { handleHealth } from "./controllers/health.js";
import { handleAdminQuota } from "./controllers/admin.js";
import { handlePlanRoute, validatePlanQuery } from "./controllers/plan.js";
import { Env, HonoEnv, WorkerDeps } from "./types.js";

export { QuotaLimiterDO };
export type { WorkerDeps };

/**
 * Creates and configures the Hono application with all middleware and routes.
 */
export function createApp(defaultDeps: WorkerDeps = {}) {
  const app = new Hono<HonoEnv>();

  // 1. Dependency injection middleware (allows mocking auth/fetch/clock in tests)
  app.use("*", async (c, next) => {
    c.set("deps", defaultDeps);
    await next();
  });

  // 2. Public health check
  app.get("/v1/health", handleHealth);

  // 3. Express-style Auth middleware for protected /v1/* endpoints
  app.use("/v1/*", authMiddleware);

  // 4. Admin quota diagnostics (requires Firebase { admin: true } custom claim)
  app.get("/v1/admin/quota", requireAdmin, handleAdminQuota);

  // 5. Bike route planning (24h KV cache + Quota DO + Transit v4 proxy)
  app.get("/v1/plan", validatePlanQuery, handlePlanRoute);

  return app;
}

/**
 * Cloudflare Worker request handler entrypoint.
 */
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
