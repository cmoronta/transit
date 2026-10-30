import { Context } from "hono";
import { HonoEnv } from "../types.js";

/**
 * Public health check endpoint for monitoring uptime and deployment version.
 */
export function handleHealth(c: Context<HonoEnv>) {
  return c.json({
    ok: true,
    service: "transit-bike-proxy",
    version: "0.1.0",
  });
}
