import { Env, VerifiedCaller } from "./types.js";

export interface JwkKey {
  kid: string;
  kty: string;
  alg?: string;
  use?: string;
  n: string;
  e: string;
}

export interface JwksResponse {
  keys: JwkKey[];
}

export type JwksFetcher = (jwksUrl: string) => Promise<JwksResponse>;

const FIREBASE_AUTH_JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const FIREBASE_APPCHECK_JWKS_URL =
  "https://firebaseappcheck.googleapis.com/v1/jwks";

const jwksMemoryCache = new Map<string, { expiresAt: number; jwks: JwksResponse }>();

export const defaultJwksFetcher: JwksFetcher = async (jwksUrl: string) => {
  const now = Date.now();
  const cached = jwksMemoryCache.get(jwksUrl);
  if (cached && cached.expiresAt > now) {
    return cached.jwks;
  }
  const resp = await fetch(jwksUrl);
  if (!resp.ok) {
    throw new Error(`Failed to fetch JWKS from ${jwksUrl}: ${resp.status}`);
  }
  const jwks = (await resp.json()) as JwksResponse;
  jwksMemoryCache.set(jwksUrl, {
    expiresAt: now + 60 * 60 * 1000, // 1 hour
    jwks,
  });
  return jwks;
};

function base64UrlToUint8Array(base64Url: string): Uint8Array {
  const padding = "=".repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

function decodeJwtPart<T>(part: string): T {
  const bytes = base64UrlToUint8Array(part);
  const text = new TextDecoder().decode(bytes);
  return JSON.parse(text) as T;
}

export interface JwtHeader {
  alg: string;
  kid?: string;
  typ?: string;
}

export interface JwtPayload {
  iss?: string;
  aud?: string | string[];
  sub?: string;
  exp?: number;
  iat?: number;
  email?: string;
  admin?: boolean;
  [key: string]: unknown;
}

export async function verifyRs256Jwt(
  token: string,
  jwksUrl: string,
  expectedIssuer: string,
  expectedAudience: string,
  jwksFetcher: JwksFetcher = defaultJwksFetcher,
  nowSeconds: number = Math.floor(Date.now() / 1000)
): Promise<JwtPayload> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Malformed JWT");
  }

  const [headerB64, payloadB64, signatureB64] = parts;
  const header = decodeJwtPart<JwtHeader>(headerB64);
  if (header.alg !== "RS256" || !header.kid) {
    throw new Error("Unsupported JWT algorithm or missing kid");
  }

  const payload = decodeJwtPart<JwtPayload>(payloadB64);
  if (!payload.exp || payload.exp <= nowSeconds) {
    throw new Error("JWT expired");
  }
  if (payload.iss !== expectedIssuer) {
    throw new Error(`Invalid issuer: ${payload.iss}`);
  }

  const audMatch = Array.isArray(payload.aud)
    ? payload.aud.includes(expectedAudience)
    : payload.aud === expectedAudience;
  if (!audMatch) {
    throw new Error(`Invalid audience: ${JSON.stringify(payload.aud)}`);
  }

  const jwks = await jwksFetcher(jwksUrl);
  const jwk = jwks.keys.find((k) => k.kid === header.kid);
  if (!jwk) {
    throw new Error(`Matching JWK not found for kid=${header.kid}`);
  }

  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const signedContent = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signatureBytes = base64UrlToUint8Array(signatureB64);

  const isValid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    signatureBytes,
    signedContent
  );

  if (!isValid) {
    throw new Error("Invalid JWT signature");
  }

  return payload;
}

/**
 * Verifies both:
 * 1. Authorization: Bearer <Firebase-ID-Token>
 * 2. X-Firebase-AppCheck: <Play-Integrity-AppCheck-Token>
 */
export async function authenticateRequest(
  request: Request,
  env: Env,
  jwksFetcher: JwksFetcher = defaultJwksFetcher,
  nowSeconds: number = Math.floor(Date.now() / 1000)
): Promise<VerifiedCaller> {
  const authHeader = request.headers.get("Authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new Error("Missing Authorization Bearer token");
  }
  const idToken = authHeader.slice("Bearer ".length).trim();

  const appCheckToken = request.headers.get("X-Firebase-AppCheck")?.trim();
  if (!appCheckToken) {
    throw new Error("Missing X-Firebase-AppCheck header");
  }

  const authPayload = await verifyRs256Jwt(
    idToken,
    FIREBASE_AUTH_JWKS_URL,
    `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    env.FIREBASE_PROJECT_ID,
    jwksFetcher,
    nowSeconds
  );

  if (!authPayload.sub) {
    throw new Error("Firebase ID token missing sub (UID)");
  }

  const appCheckPayload = await verifyRs256Jwt(
    appCheckToken,
    FIREBASE_APPCHECK_JWKS_URL,
    `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
    `projects/${env.FIREBASE_PROJECT_NUMBER}`,
    jwksFetcher,
    nowSeconds
  );

  return {
    uid: authPayload.sub,
    email: typeof authPayload.email === "string" ? authPayload.email : undefined,
    isAdmin: authPayload.admin === true,
    appId: String(appCheckPayload.sub ?? ""),
  };
}
