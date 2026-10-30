import type { JWTVerifyGetKey } from "jose";

export interface Env {
  TRANSIT_API_KEY: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  MONTHLY_QUOTA_LIMIT?: string;
  PER_MINUTE_RATE_LIMIT?: string;
  PER_USER_DAILY_LIMIT?: string;
  ROUTE_KV: KVNamespace;
  QUOTA_DO: DurableObjectNamespace;
}

export interface VerifiedCaller {
  uid: string;
  email?: string;
  isAdmin: boolean;
  appId: string;
}

export interface QuotaDecision {
  allowed: boolean;
  reason?: "RATE_LIMIT_MINUTE" | "USER_DAILY_LIMIT" | "MONTHLY_QUOTA_EXCEEDED";
  monthlyUsed: number;
  monthlyLimit: number;
  minuteUsed: number;
  minuteLimit: number;
  userDailyUsed: number;
  userDailyLimit: number;
}

export interface QuotaSnapshot {
  monthKey: string;
  monthlyUsed: number;
  monthlyLimit: number;
  minuteUsed: number;
  minuteLimit: number;
  perUserDailyLimit: number;
}

export type KeyResolver = CryptoKey | Uint8Array | JWTVerifyGetKey;

export interface AuthVerifyOptions {
  authKeyResolver?: KeyResolver;
  appCheckKeyResolver?: KeyResolver;
  currentDate?: Date;
}

export interface WorkerDeps {
  authOptions?: AuthVerifyOptions;
  upstreamFetch?: typeof fetch;
  nowMs?: () => number;
}

export type HonoEnv = {
  Bindings: Env;
  Variables: {
    caller: VerifiedCaller;
    deps: WorkerDeps;
  };
};
