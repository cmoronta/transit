const TRANSIT_V4_PLAN_URL = "https://external.transitapp.com/v4/public/plan";

export interface TransitBikePlanParams {
  from_lat: number;
  from_lon: number;
  to_lat: number;
  to_lon: number;
  num_result?: string;
  locale?: string;
}

export type TransitBikePlanResult =
  | { ok: true; data: string }
  | { ok: false; status: number; details: string };

/**
 * Calls Transit API v4 (/v4/public/plan) with personal_bike mode, safety analysis, and turn directions.
 */
export async function fetchTransitBikePlan(
  apiKey: string,
  params: TransitBikePlanParams,
  upstreamFetch: typeof fetch = fetch
): Promise<TransitBikePlanResult> {
  const queryParams = new URLSearchParams({
    mode: "personal_bike",
    from_lat: String(params.from_lat),
    from_lon: String(params.from_lon),
    to_lat: String(params.to_lat),
    to_lon: String(params.to_lon),
    should_include_directions: "true",
    should_include_safety: "true",
    num_result: params.num_result ?? "3",
  });

  if (params.locale) {
    queryParams.set("locale", params.locale);
  }

  const response = await upstreamFetch(`${TRANSIT_V4_PLAN_URL}?${queryParams.toString()}`, {
    method: "GET",
    headers: {
      apiKey,
      Accept: "application/json",
    },
  });

  const responseText = await response.text();

  if (!response.ok) {
    return {
      ok: false,
      status: response.status,
      details: responseText,
    };
  }

  return {
    ok: true,
    data: responseText,
  };
}
