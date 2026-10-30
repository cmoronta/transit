import { describe, it, expect } from "vitest";
import { fetchTransitBikePlan } from "../src/services/transit.js";

describe("Transit API v4 Client Service", () => {
  it("builds correct URLSearchParams with personal_bike, safety, and directions", async () => {
    let capturedUrl = "";
    let capturedApiKey = "";

    const mockFetch: typeof fetch = async (input, init) => {
      capturedUrl = String(input);
      capturedApiKey = new Headers(init?.headers).get("apiKey") ?? "";
      return new Response(JSON.stringify({ plan: { results: [] } }), { status: 200 });
    };

    const result = await fetchTransitBikePlan(
      "test-api-key-123",
      {
        from_lat: 33.7795,
        from_lon: -84.3834,
        to_lat: 33.7725,
        to_lon: -84.3656,
        num_result: "3",
        locale: "en",
      },
      mockFetch
    );

    expect(result.ok).toBe(true);
    expect(capturedApiKey).toBe("test-api-key-123");
    expect(capturedUrl).toContain("mode=personal_bike");
    expect(capturedUrl).toContain("should_include_directions=true");
    expect(capturedUrl).toContain("should_include_safety=true");
    expect(capturedUrl).toContain("num_result=3");
    expect(capturedUrl).toContain("locale=en");
  });

  it("handles upstream error responses gracefully", async () => {
    const mockFetch: typeof fetch = async () => {
      return new Response("Transit API rate limit exceeded", { status: 429 });
    };

    const result = await fetchTransitBikePlan(
      "test-key",
      {
        from_lat: 33.7,
        from_lon: -84.3,
        to_lat: 33.8,
        to_lon: -84.4,
      },
      mockFetch
    );

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(429);
      expect(result.details).toContain("rate limit exceeded");
    }
  });
});
