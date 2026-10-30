/**
 * Normalizes a latitude or longitude to 3 decimal places (~110m grid in Atlanta)
 * so nearby starting points reuse the same 24-hour cached bike route.
 */
export function roundToGrid100m(coord: number): string {
  return (Math.round(coord * 1000) / 1000).toFixed(3);
}

export function buildRouteCacheKey(
  fromLat: number,
  fromLon: number,
  toLat: number,
  toLon: number,
  mode = "personal_bike"
): string {
  const fromGrid = `${roundToGrid100m(fromLat)},${roundToGrid100m(fromLon)}`;
  const toGrid = `${roundToGrid100m(toLat)},${roundToGrid100m(toLon)}`;
  return `route:v4:${mode}:${fromGrid}:${toGrid}`;
}

export const ROUTE_CACHE_TTL_SECONDS = 86400; // 24 hours

export async function getCachedRoute(
  kv: KVNamespace,
  cacheKey: string
): Promise<string | null> {
  return await kv.get(cacheKey, "text");
}

export async function putCachedRoute(
  kv: KVNamespace,
  cacheKey: string,
  jsonPayload: string
): Promise<void> {
  await kv.put(cacheKey, jsonPayload, {
    expirationTtl: ROUTE_CACHE_TTL_SECONDS,
  });
}
