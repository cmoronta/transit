import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const backendDir = path.resolve(__dirname, "..");
const repoRoot = path.resolve(backendDir, "..");

function loadApiKey(): string {
  if (process.env.TRANSIT_API_KEY) {
    return process.env.TRANSIT_API_KEY.trim();
  }
  const devVarsPath = path.join(backendDir, ".dev.vars");
  if (fs.existsSync(devVarsPath)) {
    const content = fs.readFileSync(devVarsPath, "utf8");
    const match = content.match(/^TRANSIT_API_KEY\s*=\s*(.+)$/m);
    if (match && match[1] && !match[1].includes("your_transit_api_key_here")) {
      return match[1].trim().replace(/^["']|["']$/g, "");
    }
  }
  throw new Error(
    "Missing TRANSIT_API_KEY. Create backend/.dev.vars with TRANSIT_API_KEY=... or pass TRANSIT_API_KEY=... npm run spike"
  );
}

async function runSpike() {
  const apiKey = loadApiKey();

  // Official Transit API v4 /v4/public/plan parameters from https://api-doc.transitapp.com/v4.html#GET/v4/public/plan
  const params = new URLSearchParams({
    mode: "personal_bike",
    from_lat: "33.7795",
    from_lon: "-84.3834",
    to_lat: "33.7725",
    to_lon: "-84.3656",
    should_include_directions: "true",
    should_include_safety: "true",
    num_result: "3",
  });

  const url = `https://external.transitapp.com/v4/public/plan?${params.toString()}`;
  console.log(`[Phase 0 Spike] Requesting Transit API v4: ${url}`);

  const response = await fetch(url, {
    method: "GET",
    headers: {
      apiKey,
      Accept: "application/json",
      "Accept-Language": "en-US,en;q=0.9",
    },
  });

  console.log(`[Phase 0 Spike] HTTP Status: ${response.status} ${response.statusText}`);
  const bodyText = await response.text();

  if (!response.ok) {
    console.error(`[Phase 0 Spike] Error response body:\n${bodyText}`);
    process.exit(1);
  }

  const json = JSON.parse(bodyText);
  const fixtureDir = path.join(repoRoot, "shared", "src", "test", "resources");
  fs.mkdirSync(fixtureDir, { recursive: true });
  const fixturePath = path.join(fixtureDir, "plan_v4_atlanta_bike.json");
  fs.writeFileSync(fixturePath, JSON.stringify(json, null, 2), "utf8");

  console.log(`[Phase 0 Spike] Saved response fixture to: ${fixturePath}`);
  const results = json.results ?? [];
  console.log(`[Phase 0 Spike] Number of results returned: ${results.length}`);

  if (results.length > 0) {
    const leg = results[0].legs?.[0];
    console.log(`[Phase 0 Spike] First leg safety_analysis:`, JSON.stringify(leg?.safety_analysis, null, 2));
    console.log(`[Phase 0 Spike] First leg directions[0]:`, JSON.stringify(leg?.directions?.[0], null, 2));
  }
}

runSpike().catch((err) => {
  console.error("[Phase 0 Spike] Failed:", err.message);
  process.exit(1);
});
