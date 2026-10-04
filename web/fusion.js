// Faithful JS port of app/fusion (Features.kt, FusionEngine.kt, Model.kt).
// Expert-weighted evidence table: score(cause) = prior + sum of weights over known features, then softmax.

export const LEAF_CLASSES = ['healthy', 'rust', 'leaf_miner', 'phoma', 'cercospora', 'other'];

export const CAUSES = [
  'leaf_rust',
  'cercospora_nutrient_stress',
  'leaf_miner',
  'phoma',
  'berry_disease_suspected',
  'berry_borer_suspected',
  'drought_stress',
  'excess_rain',
  'soil_acidity',
  'nutrient_deficiency',
  'old_unpruned_trees',
];

export const SUSPECTED_CAUSES = new Set(['berry_disease_suspected', 'berry_borer_suspected']);

export const UNKNOWN_ANSWER = 'unknown';

/** Photo classes that have their own seen / not_seen feature. */
export const DISEASE_CLASSES = ['rust', 'leaf_miner', 'phoma', 'cercospora'];

/** Interview questions and their answer IDs. Every question also accepts "unknown". */
export const QUESTIONS = {
  q_tree_age: ['lt5', '5_15', '15_30', 'gt30'],
  q_pruning: ['this_year', 'last_year', 'longer', 'never'],
  q_fertilizer: ['yes', 'little', 'no'],
  q_shade: ['none', 'some', 'heavy'],
  q_berry_spots: ['many', 'few', 'none'],
  q_berry_holes: ['many', 'few', 'none'],
  q_yield_change: ['half_or_less', 'somewhat_less', 'same'],
  q_when_noticed: ['flowering', 'berry_growth', 'harvest'],
};

export const PHOTO = 'photo';
export const INTERVIEW = 'interview';
export const CONTEXT = 'context';

export const FEATURES = [
  ...DISEASE_CLASSES.map((c) => ({ id: `photo.${c}`, source: PHOTO, values: ['seen', 'not_seen'] })),
  { id: 'photo.healthy', source: PHOTO, values: ['mostly', 'not_mostly'] },
  ...Object.entries(QUESTIONS).map(([q, values]) => ({ id: q, source: INTERVIEW, values })),
  { id: 'rain_flowering', source: CONTEXT, values: ['deficit', 'normal', 'excess'] },
  { id: 'rain_berry', source: CONTEXT, values: ['deficit', 'normal', 'excess'] },
  { id: 'soil_ph', source: CONTEXT, values: ['acid', 'ok'] },
  { id: 'soil_n', source: CONTEXT, values: ['low', 'ok'] },
  { id: 'soil_k', source: CONTEXT, values: ['low', 'ok'] },
];

const ALL_KEYS = new Set(FEATURES.flatMap((f) => f.values.map((v) => `${f.id}=${v}`)));

function obs(feature, value, source, params = {}) {
  return { feature, value, source, params, key: `${feature}=${value}`, reason: { id: `reason.${feature}.${value}`, params } };
}

/** Parse and validate fusion_weights.json (mirrors FusionWeights.init). Fills config defaults. */
export function parseWeights(json) {
  const defaults = {
    photo_accept_prob: 0.7, seen_min_count: 2, seen_min_share: 0.3, healthy_mostly_share: 0.7,
    min_accepted_photos: 3, min_known_features: 5, abstain_top_prob: 0.4, conflict_margin: 0.1,
    output_min_prob: 0.25, max_causes: 3, label_likely: 0.6, label_possible: 0.4,
    rain_deficit_pct: 70, rain_excess_pct: 140, soil_acid_ph: 5.0, soil_low_n_g_per_kg: 1.0, soil_low_k_mg_per_kg: 100.0,
  };
  const causes = json.causes || {};
  const keys = Object.keys(causes).sort().join(',');
  if (keys !== [...CAUSES].sort().join(',')) throw new Error(`fusion_weights causes must be exactly ${CAUSES}`);
  for (const [cause, cw] of Object.entries(causes)) {
    const bad = Object.keys(cw.weights || {}).filter((k) => !ALL_KEYS.has(k));
    if (bad.length) throw new Error(`Cause ${cause} uses unknown feature keys: ${bad}`);
  }
  const normCauses = {};
  for (const c of CAUSES) normCauses[c] = { prior: causes[c].prior ?? 0, weights: causes[c].weights || {} };
  return { version: json.version, reviewedBy: json.reviewed_by ?? null, config: { ...defaults, ...(json.config || {}) }, causes: normCauses };
}

/** photos: [{ leafClass, prob }] */
export function summarisePhotos(photos, config) {
  const accepted = photos.filter((p) => p.leafClass !== 'other' && p.prob >= config.photo_accept_prob);
  const counts = {};
  for (const p of accepted) counts[p.leafClass] = (counts[p.leafClass] || 0) + 1;
  const seen = new Set(
    Object.entries(counts)
      .filter(([, n]) => n >= config.seen_min_count && n / accepted.length >= config.seen_min_share)
      .map(([c]) => c),
  );
  return { total: photos.length, accepted: accepted.length, countsByClass: counts, seen };
}

function rainObservation(feature, pct, through, config) {
  if (pct === null || pct === undefined) return null;
  const value = pct < config.rain_deficit_pct ? 'deficit' : pct > config.rain_excess_pct ? 'excess' : 'normal';
  return obs(feature, value, CONTEXT, { pct: String(pct), ...through });
}

const isNum = (v) => v !== null && v !== undefined;

/**
 * input: { photos: [{leafClass, prob}], interview: {q: answer}, context: {
 *   rainFloweringPct, rainBerryPct, soilPh, soilNitrogenGPerKg, soilPotassiumMgPerKg, dataThrough } }
 */
export function extractFeatures(input, config) {
  const summary = summarisePhotos(input.photos, config);
  const photosUsable = summary.accepted >= config.min_accepted_photos;
  const observations = [];
  const unknown = [];
  const params = (count) => ({ count: String(count), accepted: String(summary.accepted) });

  if (photosUsable) {
    for (const cls of DISEASE_CLASSES) {
      const count = summary.countsByClass[cls] || 0;
      observations.push(obs(`photo.${cls}`, summary.seen.has(cls) ? 'seen' : 'not_seen', PHOTO, params(count)));
    }
    const healthy = summary.countsByClass.healthy || 0;
    const mostly = healthy / summary.accepted >= config.healthy_mostly_share;
    observations.push(obs('photo.healthy', mostly ? 'mostly' : 'not_mostly', PHOTO, params(healthy)));
  } else {
    FEATURES.filter((f) => f.source === PHOTO).forEach((f) => unknown.push(f.id));
  }

  for (const [q, answers] of Object.entries(QUESTIONS)) {
    const a = input.interview?.[q];
    if (a === undefined || a === null || a === UNKNOWN_ANSWER) unknown.push(q);
    else if (answers.includes(a)) observations.push(obs(q, a, INTERVIEW));
    else throw new Error(`Unknown answer '${a}' for ${q}`);
  }

  const ctx = input.context || {};
  const through = ctx.dataThrough ? { data_through: ctx.dataThrough } : {};
  for (const [feature, pct] of [['rain_flowering', ctx.rainFloweringPct], ['rain_berry', ctx.rainBerryPct]]) {
    const o = rainObservation(feature, pct, through, config);
    if (o) observations.push(o); else unknown.push(feature);
  }
  if (isNum(ctx.soilPh)) observations.push(obs('soil_ph', ctx.soilPh < config.soil_acid_ph ? 'acid' : 'ok', CONTEXT, { ph: ctx.soilPh.toFixed(1) }));
  else unknown.push('soil_ph');
  if (isNum(ctx.soilNitrogenGPerKg)) observations.push(obs('soil_n', ctx.soilNitrogenGPerKg < config.soil_low_n_g_per_kg ? 'low' : 'ok', CONTEXT, { value: ctx.soilNitrogenGPerKg.toFixed(1) }));
  else unknown.push('soil_n');
  if (isNum(ctx.soilPotassiumMgPerKg)) observations.push(obs('soil_k', ctx.soilPotassiumMgPerKg < config.soil_low_k_mg_per_kg ? 'low' : 'ok', CONTEXT, { value: ctx.soilPotassiumMgPerKg.toFixed(0) }));
  else unknown.push('soil_k');

  return { photoSummary: summary, observations, unknownFeatures: unknown, photosUsable };
}

function softmax(scores) {
  const max = Math.max(...Object.values(scores));
  const exps = {};
  let sum = 0;
  for (const c of CAUSES) { exps[c] = Math.exp(scores[c] - max); sum += exps[c]; }
  const out = {};
  for (const c of CAUSES) out[c] = exps[c] / sum;
  return out;
}

function topBySource(contributions, source) {
  let best = null;
  let bestVal = -Infinity;
  for (const c of CAUSES) {
    const v = contributions[c].filter(([o]) => o.source === source).reduce((s, [, w]) => s + w, 0);
    if (v > 0 && v > bestVal) { best = c; bestVal = v; }
  }
  return best;
}

export function evaluate(weights, input) {
  const config = weights.config;
  const extracted = extractFeatures(input, config);
  const observations = extracted.observations;

  const contributions = {};
  const scores = {};
  for (const c of CAUSES) {
    const cw = weights.causes[c].weights;
    contributions[c] = observations.filter((o) => o.key in cw).map((o) => [o, cw[o.key]]);
    scores[c] = weights.causes[c].prior + contributions[c].reduce((s, [, w]) => s + w, 0);
  }
  const probabilities = softmax(scores);
  // Stable sort, ties keep enum order (as Kotlin sortedByDescending).
  const ranked = [...CAUSES].sort((a, b) => probabilities[b] - probabilities[a]);

  const knownFeatureCount = observations.filter((o) => o.source !== PHOTO).length + (extracted.photosUsable ? 1 : 0);

  const hasConflict = () => {
    const photoTop = topBySource(contributions, PHOTO);
    if (!photoTop) return false;
    const interviewTop = topBySource(contributions, INTERVIEW);
    if (!interviewTop) return false;
    if (photoTop === interviewTop) return false;
    const topTwo = ranked.slice(0, 2);
    if (!topTwo.includes(photoTop) || !topTwo.includes(interviewTop)) return false;
    return Math.abs(probabilities[photoTop] - probabilities[interviewTop]) < config.conflict_margin;
  };

  const abstainReasons = [];
  if (extracted.photoSummary.accepted < config.min_accepted_photos) abstainReasons.push('too_few_photos');
  if (probabilities[ranked[0]] < config.abstain_top_prob) abstainReasons.push('low_top_probability');
  if (knownFeatureCount < config.min_known_features) abstainReasons.push('too_few_known_features');
  if (hasConflict()) abstainReasons.push('photo_interview_conflict');

  const rank = (cause) => {
    const prob = probabilities[cause];
    const label = prob >= config.label_likely ? 'likely' : prob >= config.label_possible ? 'possible' : 'worth_checking';
    const reasons = contributions[cause]
      .filter(([, w]) => w > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 2)
      .map(([o]) => o.reason);
    return { cause, prob, label, reasons, nameId: `cause.${cause}.name` };
  };

  const candidates = ranked.filter((c) => probabilities[c] >= config.output_min_prob).slice(0, config.max_causes).map(rank);
  const status = abstainReasons.length === 0 ? 'ok' : 'needs_human';
  return {
    status,
    causes: status === 'ok' ? candidates : [],
    candidates,
    abstainReasons,
    evidence: observations,
    unknownFeatures: extracted.unknownFeatures,
    knownFeatureCount,
    photoSummary: extracted.photoSummary,
    probabilities,
    scores,
    contributions,
    ranked,
  };
}

/** Fill {param} placeholders in a template. */
export function fill(template, params = {}) {
  return String(template).replace(/\{(\w+)\}/g, (m, k) => (k in params ? params[k] : m));
}
