# Transit Bike Navigator (Wear OS 4+ & Android Companion)

Turn-by-turn bike navigation for Wear OS 4+ and Android, powered by **Transit API v4 (`/v4/public/plan`)**, **MapLibre + OpenFreeMap (OSM)**, and a **Cloudflare Worker + Durable Object + KV** edge proxy.

## Architecture & Design Docs
- [Architecture Plan](docs/architecture_plan.md)
- [Architecture Review (TS/Rails/AWS Mapping)](docs/architecture_review.md)
- [Phone Companion App Design Workshop (Interactive HTML)](docs/phone_design_workshop.html)
- [Wear OS Tile & Screen Design Workshop (Interactive HTML)](docs/tile_design_workshop.html)

## Repository Structure
- `backend/` — Cloudflare Worker + `QuotaLimiterDO` Durable Object + `ROUTE_KV` 24h edge cache (TypeScript + Wrangler + Vitest). Holds `TRANSIT_API_KEY` securely off-device.
- `shared/` — Pure Kotlin shared library (`RouteModel`, `PolylineDecoder`, `TransitV4ResponseMapper`, `UnitFormatter`, `DataLayerContract`, `NavigationEngine`).
- `mobile/` — Android Phone Companion App (Material 3 Expressive with Material You dynamic colors, single-pane `UnifiedMapScreen`, Google Sign-In, Firebase App Check, and `NavigationForegroundService`).
- `wear/` — Wear OS 4+ Watch App (`WearHomeScreen`, `RideScreen` with Turn View & MapLibre/Canvas Map View, Always-On Display, `OngoingActivity`, `HapticCueEngine`, `WatchFallbackGpsManager`, `RideTileService`, and `EtaComplicationService`).

## Phase 0: Running the Transit API v4 Live Spike
1. Copy `backend/.dev.vars.example` to `backend/.dev.vars` (gitignored) and add your `TRANSIT_API_KEY`:
   ```bash
   cp backend/.dev.vars.example backend/.dev.vars
   ```
2. Run the spike script to query an Atlanta bike route (Midtown → Ponce City Market) and save the response fixture to `shared/src/test/resources/plan_v4_atlanta_bike.json`:
   ```bash
   cd backend
   npm install
   npm run spike
   ```
