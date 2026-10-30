import { createRemoteJWKSet, jwtVerify, JWTVerifyGetKey } from "jose";
import { AuthVerifyOptions, Env, KeyResolver, VerifiedCaller } from "./types.js";

export type { KeyResolver, AuthVerifyOptions };

const FIREBASE_AUTH_JWKS = createRemoteJWKSet(
  new URL(
    "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
  )
);

const FIREBASE_APPCHECK_JWKS = createRemoteJWKSet(
  new URL("https://firebaseappcheck.googleapis.com/v1/jwks")
);

/**
 * Verifies both:
 * 1. Authorization: Bearer <Firebase-ID-Token>
 * 2. X-Firebase-AppCheck: <Play-Integrity-AppCheck-Token>
 * using the standard `jose` library.
 */
export async function authenticateRequest(
  request: Request,
  env: Env,
  options: AuthVerifyOptions = {}
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

  const authKey = options.authKeyResolver ?? FIREBASE_AUTH_JWKS;
  const appCheckKey = options.appCheckKeyResolver ?? FIREBASE_APPCHECK_JWKS;

  const { payload: authPayload } = await jwtVerify(idToken, authKey as JWTVerifyGetKey, {
    issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    audience: env.FIREBASE_PROJECT_ID,
    algorithms: ["RS256"],
    currentDate: options.currentDate,
  });

  const { payload: appCheckPayload } = await jwtVerify(
    appCheckToken,
    appCheckKey as JWTVerifyGetKey,
    {
      issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
      audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`,
      algorithms: ["RS256"],
      currentDate: options.currentDate,
    }
  );

  const uid = typeof authPayload.sub === "string" ? authPayload.sub : "";
  if (!uid) {
    throw new Error("Token payload missing subject UID");
  }

  const isAdmin = authPayload.admin === true;
  const email = typeof authPayload.email === "string" ? authPayload.email : undefined;
  const appId = typeof appCheckPayload.sub === "string" ? appCheckPayload.sub : "";

  return { uid, email, isAdmin, appId };
}
