import { MiddlewareHandler } from "hono";
import { authenticateRequest } from "../auth.js";
import { HonoEnv } from "../types.js";

/**
 * Authentication middleware for /v1/* endpoints.
 * Validates:
 * 1. Firebase Auth ID Token (RS256 via Google JWKS)
 * 2. Firebase App Check Token (Play Integrity RS256 via App Check JWKS)
 * Skips authentication for /v1/health.
 */
export const authMiddleware: MiddlewareHandler<HonoEnv> = async (c, next) => {
  if (c.req.path === "/v1/health") {
    return next();
  }

  const deps = c.get("deps");
  const nowMs = deps?.nowMs ? deps.nowMs() : Date.now();

  try {
    const caller = await authenticateRequest(c.req.raw, c.env, {
      ...deps?.authOptions,
      currentDate: deps?.authOptions?.currentDate ?? new Date(nowMs),
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
};

/**
 * Guard middleware requiring the Firebase custom claim { "admin": true }.
 * Returns 403 Forbidden if not satisfied.
 */
export const requireAdmin: MiddlewareHandler<HonoEnv> = async (c, next) => {
  const caller = c.get("caller");
  if (!caller?.isAdmin) {
    return c.json(
      { error: "FORBIDDEN", message: "Admin custom claim required" },
      403
    );
  }
  await next();
};
