import { describe, it, expect, beforeAll } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { handleWorkerRequest } from "../src/index.js";
import { QuotaEngine, SimpleStorage } from "../src/quota-do.js";
import { Env } from "../src/types.js";
import { JwkKey, JwksFetcher } from "../src/auth.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const fixtureJson = fs.readFileSync(
  path.resolve(__dirname, "../../shared/src/test/resources/plan_v4_atlanta_bike.json"),
  "utf8"
);

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function jsonToBase64Url(obj: unknown): string {
  return bytesToBase64Url(new TextEncoder().encode(JSON.stringify(obj)));
}

class MemoryKV {
  private map = new Map<string, string>();
  async get(key: string): Promise<string | null> {
    return this.map.get(key) ?? null;
  }
  async put(key: string, value: string): Promise<void> {
    this.map.set(key, value);
  }
}

class MemorySimpleStorage implements SimpleStorage {
  private map = new Map<string, unknown>();
  async get<T = number>(key: string): Promise<T | undefined> {
    return this.map.get(key) as T | undefined;
  }
  async put<T = number>(key: string, value: T): Promise<void> {
    this.map.set(key, value);
  }
}

describe("Cloudflare Worker Proxy + QuotaLimiterDO + Transit v4", () => {
  let privateKey: CryptoKey;
  let publicJwk: JwkKey;
  const kid = "test-key-1";
  const projectId = "transit-bike-test";
  const projectNumber = "1234567890";
  const fixedNowMs = 1761753600 * 1000; // 2025-10-29T16:00:00Z

  beforeAll(async () => {
    const pair = await crypto.subtle.generateKey(
      {
        name: "RSASSA-PKCS1-v1_5",
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: "SHA-256",
      },
      true,
      ["sign", "verify"]
    );
    privateKey = pair.privateKey;
    const exported = (await crypto.subtle.exportKey("jwk", pair.publicKey)) as JwkKey;
    publicJwk = { ...exported, kid, alg: "RS256", use: "sig" };
  });

  async function signJwt(payload: Record<string, unknown>): Promise<string> {
    const headerB64 = jsonToBase64Url({ alg: "RS256", kid, typ: "JWT" });
    const payloadB64 = jsonToBase64Url(payload);
    const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
    const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, data);
    return `${headerB64}.${payloadB64}.${bytesToBase64Url(new Uint8Array(sig))}`;
  }

  async function makeAuthHeaders(uid = "user-123", isAdmin = false): Promise<Record<string, string>> {
    const nowSec = Math.floor(fixedNowMs / 1000);
    const idToken = await signJwt({
      iss: `https://securetoken.google.com/${projectId}`,
      aud: projectId,
      sub: uid,
      email: "rider@example.com",
      admin: isAdmin,
      iat: nowSec - 60,
      exp: nowSec + 3600,
    });
    const appCheckToken = await signJwt({
      iss: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
      aud: `projects/${projectNumber}`,
      sub: "1:1234567890:android:abcdef",
      iat: nowSec - 60,
      exp: nowSec + 3600,
    });
    return {
      Authorization: `Bearer ${idToken}`,
      "X-Firebase-AppCheck": appCheckToken,
    };
  }

  const mockJwksFetcher: JwksFetcher = async () => ({
    keys: [publicJwk],
  });

  function createTestEnv(monthlyLimit = 1480, minuteLimit = 4, userDailyLimit = 40): {
    env: Env;
    kv: MemoryKV;
  } {
    const kv = new MemoryKV();
    const storage = new MemorySimpleStorage();
    const engine = new QuotaEngine(storage, monthlyLimit, minuteLimit, userDailyLimit);

    const doStub = {
      async fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
        const req = input instanceof Request ? input : new Request(String(input), init);
        const u = new URL(req.url);
        if (req.method === "POST" && u.pathname === "/consume") {
          const body = (await req.json()) as { uid: string; nowMs?: number };
          const d = await engine.consume(body.uid, body.nowMs);
          return new Response(JSON.stringify(d), { status: d.allowed ? 200 : 429 });
        }
        if (req.method === "GET" && u.pathname === "/status") {
          const s = await engine.status(Number(u.searchParams.get("nowMs") ?? fixedNowMs));
          return new Response(JSON.stringify(s), { status: 200 });
        }
        return new Response("Not Found", { status: 404 });
      },
    };

    const env: Env = {
      TRANSIT_API_KEY: "test_secret_transit_key",
      FIREBASE_PROJECT_ID: projectId,
      FIREBASE_PROJECT_NUMBER: projectNumber,
      MONTHLY_QUOTA_LIMIT: String(monthlyLimit),
      PER_MINUTE_RATE_LIMIT: String(minuteLimit),
      PER_USER_DAILY_LIMIT: String(userDailyLimit),
      ROUTE_KV: kv as unknown as KVNamespace,
      QUOTA_DO: {
        idFromName: () => ({} as DurableObjectId),
        get: () => doStub as unknown as DurableObjectStub,
      } as unknown as DurableObjectNamespace,
    };

    return { env, kv };
  }

  it("rejects unauthenticated requests to /v1/plan with 401", async () => {
    const { env } = createTestEnv();
    const req = new Request(
      "https://worker.test/v1/plan?from_lat=33.7795&from_lon=-84.3834&to_lat=33.7725&to_lon=-84.3656"
    );
    const resp = await handleWorkerRequest(req, env, {
      jwksFetcher: mockJwksFetcher,
      nowMs: () => fixedNowMs,
    });
    expect(resp.status).toBe(401);
  });

  it("proxies /v1/plan to Transit API v4 with personal_bike + safety + directions and caches in KV", async () => {
    const { env } = createTestEnv();
    const headers = await makeAuthHeaders("rider-atl", false);
    let upstreamCalls = 0;
    let capturedUrl = "";
    let capturedApiKey = "";

    const upstreamFetch: typeof fetch = async (input, init) => {
      upstreamCalls++;
      capturedUrl = String(input);
      capturedApiKey = new Headers(init?.headers).get("apiKey") ?? "";
      return new Response(fixtureJson, { status: 200 });
    };

    // 1st request: Cache MISS -> calls Transit v4
    const req1 = new Request(
      "https://worker.test/v1/plan?from_lat=33.77911&from_lon=-84.38311&to_lat=33.77211&to_lon=-84.36511",
      { headers }
    );
    const resp1 = await handleWorkerRequest(req1, env, {
      jwksFetcher: mockJwksFetcher,
      upstreamFetch,
      nowMs: () => fixedNowMs,
    });

    expect(resp1.status).toBe(200);
    expect(resp1.headers.get("X-Cache")).toBe("MISS");
    expect(resp1.headers.get("X-Quota-Used")).toBe("1");
    expect(upstreamCalls).toBe(1);
    expect(capturedApiKey).toBe("test_secret_transit_key");
    expect(capturedUrl).toContain("mode=personal_bike");
    expect(capturedUrl).toContain("should_include_directions=true");
    expect(capturedUrl).toContain("should_include_safety=true");

    // 2nd request within same ~100m grid cell: Cache HIT -> 0 upstream calls, 0 additional quota!
    const req2 = new Request(
      "https://worker.test/v1/plan?from_lat=33.77939&from_lon=-84.38339&to_lat=33.77239&to_lon=-84.36539",
      { headers }
    );
    const resp2 = await handleWorkerRequest(req2, env, {
      jwksFetcher: mockJwksFetcher,
      upstreamFetch,
      nowMs: () => fixedNowMs,
    });

    expect(resp2.status).toBe(200);
    expect(resp2.headers.get("X-Cache")).toBe("HIT");
    expect(upstreamCalls).toBe(1); // Still 1!
  });

  it("enforces the 4 req/min rate limit via QuotaLimiterDO", async () => {
    const { env } = createTestEnv(1480, 2, 40); // minuteLimit = 2 for test
    const headers = await makeAuthHeaders("rider-1", false);
    const upstreamFetch: typeof fetch = async () => new Response(fixtureJson, { status: 200 });

    const r1 = await handleWorkerRequest(
      new Request("https://worker.test/v1/plan?from_lat=33.71&from_lon=-84.31&to_lat=33.72&to_lon=-84.32", { headers }),
      env,
      { jwksFetcher: mockJwksFetcher, upstreamFetch, nowMs: () => fixedNowMs }
    );
    const r2 = await handleWorkerRequest(
      new Request("https://worker.test/v1/plan?from_lat=33.73&from_lon=-84.33&to_lat=33.74&to_lon=-84.34", { headers }),
      env,
      { jwksFetcher: mockJwksFetcher, upstreamFetch, nowMs: () => fixedNowMs }
    );
    const r3 = await handleWorkerRequest(
      new Request("https://worker.test/v1/plan?from_lat=33.75&from_lon=-84.35&to_lat=33.76&to_lon=-84.36", { headers }),
      env,
      { jwksFetcher: mockJwksFetcher, upstreamFetch, nowMs: () => fixedNowMs }
    );

    expect(r1.status).toBe(200);
    expect(r2.status).toBe(200);
    expect(r3.status).toBe(429);
    const errBody = (await r3.json()) as { error: string };
    expect(errBody.error).toBe("RATE_LIMIT_MINUTE");
  });

  it("blocks non-admin users from /v1/admin/quota (403) and allows admin users (200)", async () => {
    const { env } = createTestEnv();
    const regularHeaders = await makeAuthHeaders("regular-user", false);
    const adminHeaders = await makeAuthHeaders("cmoronta-admin", true);

    const resForbidden = await handleWorkerRequest(
      new Request("https://worker.test/v1/admin/quota", { headers: regularHeaders }),
      env,
      { jwksFetcher: mockJwksFetcher, nowMs: () => fixedNowMs }
    );
    expect(resForbidden.status).toBe(403);

    const resAdmin = await handleWorkerRequest(
      new Request("https://worker.test/v1/admin/quota", { headers: adminHeaders }),
      env,
      { jwksFetcher: mockJwksFetcher, nowMs: () => fixedNowMs }
    );
    expect(resAdmin.status).toBe(200);
    const data = (await resAdmin.json()) as { adminUid: string; quota: { monthlyLimit: number } };
    expect(data.adminUid).toBe("cmoronta-admin");
    expect(data.quota.monthlyLimit).toBe(1480);
  });
});
