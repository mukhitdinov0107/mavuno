// Runs every scenario in content/fixtures/fusion_scenarios.json through the JS fusion port.
// Usage: node web/test_fusion.mjs   (no dependencies)
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseWeights, evaluate } from './fusion.js';

const here = dirname(fileURLToPath(import.meta.url));
const load = (p) => JSON.parse(readFileSync(join(here, 'content', p), 'utf8'));
const weights = parseWeights(load('fusion_weights.json'));
const { scenarios } = load('fixtures/fusion_scenarios.json');

function toInput(s) {
  const photos = [];
  for (const [leafClass, prob, count] of s.photos) for (let i = 0; i < count; i++) photos.push({ leafClass, prob });
  const c = s.context || {};
  const v = (k) => (c[k] === undefined || c[k] === null ? null : c[k]);
  return {
    photos,
    interview: s.interview || {},
    context: {
      rainFloweringPct: v('rain_flowering_pct'),
      rainBerryPct: v('rain_berry_pct'),
      soilPh: v('soil_ph'),
      soilNitrogenGPerKg: v('soil_n'),
      soilPotassiumMgPerKg: v('soil_k'),
      dataThrough: v('data_through'),
    },
  };
}

let failed = 0;
if (scenarios.length < 20) { console.log(`FAIL: expected >= 20 scenarios, found ${scenarios.length}`); failed++; }

for (const s of scenarios) {
  const r = evaluate(weights, toInput(s));
  const shown = r.causes.map((c) => c.cause);
  const e = s.expect;
  const problems = [];
  if (r.status !== e.status) problems.push(`status=${r.status}, expected ${e.status} (abstain=${r.abstainReasons})`);
  if (e.top && shown[0] !== e.top) problems.push(`top=${shown[0]}, expected ${e.top}`);
  for (const c of e.includes || []) if (!shown.includes(c)) problems.push(`missing ${c}`);
  for (const c of e.excludes || []) if (shown.includes(c)) problems.push(`should not show ${c}`);
  for (const a of e.abstain_reasons || []) if (!r.abstainReasons.includes(a)) problems.push(`missing abstain reason ${a} (got ${r.abstainReasons})`);
  // Every shown cause has two reasons, each from a feature that raised its score.
  for (const rc of r.causes) {
    if (rc.reasons.length !== 2) problems.push(`${rc.cause} has ${rc.reasons.length} reasons`);
    for (const reason of rc.reasons) {
      const o = r.evidence.find((x) => x.reason.id === reason.id);
      if (!o || !((weights.causes[rc.cause].weights[o.key] ?? 0) > 0)) problems.push(`${rc.cause}: reason ${reason.id} did not contribute`);
    }
  }
  const top = r.ranked.slice(0, 3).map((c) => `${c}=${r.probabilities[c].toFixed(2)}`).join(', ');
  if (problems.length) { failed++; console.log(`FAIL ${s.id}: ${problems.join('; ')}\n     ${top}`); }
  else console.log(`ok   ${s.id}  [${r.status}${r.abstainReasons.length ? ': ' + r.abstainReasons.join(',') : ''}] ${top}`);
}

console.log(`\n${scenarios.length - failed}/${scenarios.length} scenarios passed`);
process.exit(failed ? 1 : 0);
