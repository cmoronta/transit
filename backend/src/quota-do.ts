import { Env, QuotaDecision, QuotaSnapshot } from "./types.js";

export interface SimpleStorage {
  get<T = number>(key: string): Promise<T | undefined>;
  put<T = number>(key: string, value: T): Promise<void>;
}

export function formatYearMonth(epochMs: number): string {
  const d = new Date(epochMs);
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}

export function formatYearMonthDay(epochMs: number): string {
  const d = new Date(epochMs);
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export class QuotaEngine {
  constructor(
    private readonly storage: SimpleStorage,
    private readonly monthlyLimit: number = 1480,
    private readonly minuteLimit: number = 4,
    private readonly userDailyLimit: number = 40
  ) {}

  async consume(uid: string, nowMs: number = Date.now()): Promise<QuotaDecision> {
    const monthKey = `quota:${formatYearMonth(nowMs)}`;
    const minuteBucket = Math.floor(nowMs / 60000);
    const minuteKey = `ratelimit:${minuteBucket}`;
    const userDayKey = `user:${uid}:${formatYearMonthDay(nowMs)}`;

    const [monthlyUsedRaw, minuteUsedRaw, userDailyUsedRaw] = await Promise.all([
      this.storage.get<number>(monthKey),
      this.storage.get<number>(minuteKey),
      this.storage.get<number>(userDayKey),
    ]);

    const monthlyUsed = monthlyUsedRaw ?? 0;
    const minuteUsed = minuteUsedRaw ?? 0;
    const userDailyUsed = userDailyUsedRaw ?? 0;

    if (monthlyUsed >= this.monthlyLimit) {
      return {
        allowed: false,
        reason: "MONTHLY_QUOTA_EXCEEDED",
        monthlyUsed,
        monthlyLimit: this.monthlyLimit,
        minuteUsed,
        minuteLimit: this.minuteLimit,
        userDailyUsed,
        userDailyLimit: this.userDailyLimit,
      };
    }

    if (minuteUsed >= this.minuteLimit) {
      return {
        allowed: false,
        reason: "RATE_LIMIT_MINUTE",
        monthlyUsed,
        monthlyLimit: this.monthlyLimit,
        minuteUsed,
        minuteLimit: this.minuteLimit,
        userDailyUsed,
        userDailyLimit: this.userDailyLimit,
      };
    }

    if (userDailyUsed >= this.userDailyLimit) {
      return {
        allowed: false,
        reason: "USER_DAILY_LIMIT",
        monthlyUsed,
        monthlyLimit: this.monthlyLimit,
        minuteUsed,
        minuteLimit: this.minuteLimit,
        userDailyUsed,
        userDailyLimit: this.userDailyLimit,
      };
    }

    const nextMonthly = monthlyUsed + 1;
    const nextMinute = minuteUsed + 1;
    const nextUserDaily = userDailyUsed + 1;

    await Promise.all([
      this.storage.put(monthKey, nextMonthly),
      this.storage.put(minuteKey, nextMinute),
      this.storage.put(userDayKey, nextUserDaily),
    ]);

    return {
      allowed: true,
      monthlyUsed: nextMonthly,
      monthlyLimit: this.monthlyLimit,
      minuteUsed: nextMinute,
      minuteLimit: this.minuteLimit,
      userDailyUsed: nextUserDaily,
      userDailyLimit: this.userDailyLimit,
    };
  }

  async status(nowMs: number = Date.now()): Promise<QuotaSnapshot> {
    const ym = formatYearMonth(nowMs);
    const monthKey = `quota:${ym}`;
    const minuteBucket = Math.floor(nowMs / 60000);
    const minuteKey = `ratelimit:${minuteBucket}`;

    const [monthlyUsedRaw, minuteUsedRaw] = await Promise.all([
      this.storage.get<number>(monthKey),
      this.storage.get<number>(minuteKey),
    ]);

    return {
      monthKey: ym,
      monthlyUsed: monthlyUsedRaw ?? 0,
      monthlyLimit: this.monthlyLimit,
      minuteUsed: minuteUsedRaw ?? 0,
      minuteLimit: this.minuteLimit,
      perUserDailyLimit: this.userDailyLimit,
    };
  }
}

/**
 * Cloudflare Durable Object that serializes all quota checks & increments
 * so concurrent requests can never race past the 4/min or 1,480/month limits.
 */
export class QuotaLimiterDO {
  private readonly engine: QuotaEngine;

  constructor(state: DurableObjectState, env: Env) {
    const monthlyLimit = Number(env.MONTHLY_QUOTA_LIMIT ?? "1480");
    const minuteLimit = Number(env.PER_MINUTE_RATE_LIMIT ?? "4");
    const userDailyLimit = Number(env.PER_USER_DAILY_LIMIT ?? "40");
    this.engine = new QuotaEngine(state.storage, monthlyLimit, minuteLimit, userDailyLimit);
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/consume") {
      const body = (await request.json()) as { uid: string; nowMs?: number };
      const decision = await this.engine.consume(body.uid, body.nowMs);
      return new Response(JSON.stringify(decision), {
        status: decision.allowed ? 200 : 429,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (request.method === "GET" && url.pathname === "/status") {
      const nowMs = url.searchParams.get("nowMs")
        ? Number(url.searchParams.get("nowMs"))
        : Date.now();
      const snapshot = await this.engine.status(nowMs);
      return new Response(JSON.stringify(snapshot), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response("Not Found", { status: 404 });
  }
}
