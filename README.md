# Transit Bike Navigator (Wear OS 4+ & Android Companion)

Turn-by-turn bike navigation for **Wear OS 4+ (API 33+)** and **Android (API 26+)**, powered by **Transit API v4 (`/v4/public/plan`)**, **MapLibre + OpenFreeMap (OSM)**, and a **Cloudflare Worker + Durable Object + KV** edge proxy.

> 📋 **Switching computers or continuing work?** See the full [**Project Status & Developer Handoff Guide**](docs/PROJECT_STATUS_AND_HANDOFF.md) for architectural details, environment setup, and code references.

---

## Architecture & Documentation

- [**Developer Handoff & Status Guide**](docs/PROJECT_STATUS_AND_HANDOFF.md) — Comprehensive guide for resuming work on any computer.
- [**Architecture Plan**](docs/architecture_plan.md) — High-level system design and data flow.
- [**Architecture Review (TS / Rails / AWS Mapping)**](docs/architecture_review.md) — Core concepts mapped to familiar web and cloud patterns.
- [**Phone Design Workshop (Interactive HTML)**](docs/phone_design_workshop.html) — Interactive preview of phone companion UI states.
- [**Wear OS Tile & Screen Workshop (Interactive HTML)**](docs/tile_design_workshop.html) — Interactive preview of watch screens, tiles, and complications.

---

## Current Status: Ready for Physical Device Testing

The codebase is completely implemented, tested, and pushed to `main` (`774b3f6`):

1. **`backend/` (Cloudflare Worker Edge Proxy)**:
   - Built with **Hono**, **`jose`**, and **Zod**.
   - Zero API keys on client devices: `TRANSIT_API_KEY` lives exclusively in Cloudflare Worker secrets.
   - Enforces atomic rate limits (4 req/min, 40 req/day/user, 1,480 req/month) via `QuotaLimiterDO` Durable Object.
   - 24-hour route cache via Cloudflare KV (`ROUTE_KV`).
   - Admin diagnostics endpoint (`GET /v1/admin/quota`) gated by Firebase `{ admin: true }` claim.
   - 7/7 Vitest tests passing; 0 TypeScript errors.

2. **`shared/` (Pure Kotlin Shared Engine)**:
   - DTOs matching official Transit OpenAPI 3.1.0 schema.
   - `TransitV4ResponseMapper`: Parses responses and sorts routes by **`protected_lanes_percentage` descending**, guaranteeing the safest bike path is at index 0 and marked `isSafestDefault = true`.
   - `PolylineDecoder`: Google encoded polyline decoder/encoder and spherical geometry math (Haversine distance, segment projection).
   - `NavigationEngine`: Turn-by-turn state machine emitting **200m (`APPROACH`)**, **50m (`PREPARE`)**, and **15m (`NOW`)** cues, with generous off-route detection (15s off-route $>40$m, 20s cooldown, max 10 reroutes/trip).
   - `UnitFormatter`: Imperial (`ft`/`mi`) and Metric (`m`/`km`) formatting based on `Locale.getDefault()`.
   - `DataLayerContract`: Google Play Services Wearable paths and binary serialization helpers.
   - 6 JUnit test suites covering all models and math.

3. **`mobile/` (Android Phone Companion App)**:
   - Material 3 Expressive UI with **Material You dynamic colors** (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
   - Single-pane `UnifiedMapScreen` with 3-stop `BottomSheetScaffold` (Peek / Half / Full).
   - Clean rider UI free of technical or proxy badges.
   - `NavigationForegroundService`: Ongoing turn notification and location tracking.
   - `PhoneWearSync`: Synchronizes active routes, turn cues, and nav state with the watch.
   - Built-in Atlanta Midtown $\to$ Ponce City Market route fallback for offline/instant testing.
   - `AdminDiagnosticsSheet`: Gated by Firebase `{ admin: true }` claim to monitor live Cloudflare Worker quota counters.

4. **`wear/` (Wear OS 4+ Watch App)**:
   - Wear Compose Material 3 with `dynamicColorScheme` matching the user's active watch-face accent.
   - `RideScreen`: Toggleable **`TurnView`** (large icon, street name, remaining ETA) and **`MapView`** (lightweight 60fps vector canvas route map).
   - `HapticCueEngine`: Distinct wrist vibration patterns for Approach (double tap), Prepare (ramping pulse), and Turn Now (sharp triple pulse).
   - `WatchFallbackGpsManager`: Automatically acquires watch GPS if phone Bluetooth disconnects for $> 6$ seconds.
   - `RideTileService`: ProtoLayout M3 glanceable tile.
   - `EtaComplicationService`: Watch face complication displaying remaining minutes to destination.

---

## Quickstart Guide

### 1. Backend

```bash
cd backend
npm install

# Run unit tests
npm test

# Run local development server
cp .dev.vars.example .dev.vars
# Add TRANSIT_API_KEY to .dev.vars
npm run dev
```

### 2. Android Studio (Phone & Watch)

1. Open Android Studio (Ladybug 2024.2.1+ recommended with JDK 17).
2. Open `/path/to/transit`.
3. Let Gradle sync automatically.
4. **Run on Phone**: Select `mobile` run configuration $\to$ Run (▶).
5. **Run on Watch**: Select `wear` run configuration $\to$ Run (▶).
6. Tap **"Start Ride"** on the phone — the watch will immediately display turn instructions and trigger haptic cues!

---

## Key Files Reference

```
transit/
├── backend/
│   ├── src/index.ts                  # Hono router entrypoint
│   ├── src/controllers/plan.ts       # Bike plan controller & Zod validation
│   ├── src/controllers/admin.ts      # Admin diagnostics controller
│   ├── src/services/transit.ts       # Transit API v4 client
│   ├── src/services/quota.ts         # Quota DO client
│   ├── src/middleware/auth.ts        # Firebase Auth & App Check middleware
│   ├── src/quota-do.ts               # QuotaLimiterDO Durable Object & QuotaEngine
│   └── test/worker.test.ts           # Vitest integration suite
├── shared/
│   ├── src/main/kotlin/.../model/    # Transit DTOs & Domain RouteModel
│   ├── src/main/kotlin/.../nav/      # PolylineDecoder, ResponseMapper, NavigationEngine, UnitFormatter
│   ├── src/main/kotlin/.../datalayer/# DataLayerContract
│   └── src/test/kotlin/...           # 6 JUnit test suites
├── mobile/
│   ├── src/main/AndroidManifest.xml  # Permissions, Foreground Service, Wear Listener
│   ├── src/main/kotlin/.../ui/       # UnifiedMapScreen, MainViewModel, AdminDiagnosticsSheet
│   ├── src/main/kotlin/.../service/  # NavigationForegroundService
│   ├── src/main/kotlin/.../sync/     # PhoneWearSync & PhoneWearListenerService
│   └── src/main/kotlin/.../data/     # WorkerProxyApi, RouteRepository, AuthManager
└── wear/
    ├── src/main/AndroidManifest.xml  # Standalone watch manifest, Tile, Complication
    ├── src/main/kotlin/.../ui/       # RideScreen, WearHomeScreen, WearTheme
    ├── src/main/kotlin/.../nav/      # HapticCueEngine, WatchFallbackGpsManager
    ├── src/main/kotlin/.../tile/     # RideTileService, EtaComplicationService
    └── src/main/kotlin/.../sync/     # WearListenerService
```
