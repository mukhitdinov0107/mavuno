import { parseWeights, evaluate, fill, QUESTIONS, SUSPECTED_CAUSES } from './fusion.js';
import { LeafClassifier } from './classifier.js';

const REPO = 'https://github.com/mukhitdinov0107/mavuno';
const APK = `${REPO}/releases/latest`;
const SAMPLES = [
  ['00_bracol_rust.jpg', 1], ['11_jmuben_rust.jpg', 1],
  ['01_bracol_rust.jpg', 2], ['02_bracol_phoma.jpg', 2],
  ['05_bracol_leaf_miner.jpg', 3], ['06_jmuben_leaf_miner.jpg', 3],
];
const RAIN_OPTIONS = [['', 'web.rain_unknown'], ['60', 'web.rain_below'], ['100', 'web.rain_normal'], ['160', 'web.rain_above']];
const PH_OPTIONS = [['', 'web.rain_unknown'], ['4.6', 'web.ph_acid'], ['5.6', 'web.ph_ok']];

const state = {
  lang: 'en',
  packs: {},
  weights: null,
  cardsMeta: {},
  web: {},
  model: { status: 'loading', error: null, classifier: null },
  photos: [],
  answers: {},
  ctx: { rainFlowering: '', rainBerry: '', soilPh: '' },
  showResult: false,
  howOpen: false,
};
let nextId = 1;

const app = document.getElementById('app');

// ---------- text ----------
function t(key, params) {
  const p = state.packs[state.lang];
  const s = p?.strings[key] ?? state.web[state.lang]?.[key] ?? state.packs.en?.strings[key] ?? state.web.en?.[key] ?? key;
  return params ? fill(s, params) : s;
}
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pct = (p) => `${Math.round(p * 100)}%`;
const reasonText = (r) => t(r.id, r.params);

// ---------- loading ----------
async function getJson(path) {
  const r = await fetch(path);
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
  return r.json();
}

async function boot() {
  try {
    const [weights, cardsMeta, web, enS, enC, enM, swS, swC, swM] = await Promise.all([
      getJson('content/fusion_weights.json'), getJson('content/cards_meta.json'), getJson('content/web_strings.json'),
      getJson('content/packs/en/strings.json'), getJson('content/packs/en/cards.json'), getJson('content/packs/en/manifest.json'),
      getJson('content/packs/sw/strings.json'), getJson('content/packs/sw/cards.json'), getJson('content/packs/sw/manifest.json'),
    ]);
    state.weights = parseWeights(weights);
    state.cardsMeta = cardsMeta.cards || {};
    state.web = web;
    state.packs = { en: { strings: enS, cards: enC, manifest: enM }, sw: { strings: swS, cards: swC, manifest: swM } };
  } catch (e) {
    app.innerHTML = `<p class="fatal">Could not load the demo content (${esc(e.message)}). Serve this folder over HTTP, e.g. <code>python3 -m http.server -d web 8080</code>.</p>`;
    return;
  }
  try {
    const saved = localStorage.getItem('mavuno.lang');
    if (saved && state.packs[saved]) state.lang = saved;
  } catch { /* storage unavailable */ }
  render();
  loadModel();
}

async function loadModel() {
  try {
    state.model.classifier = await LeafClassifier.load('content/model/');
    state.model.status = 'ready';
  } catch (e) {
    console.error('Leaf model failed to load', e);
    state.model.status = 'error';
    state.model.error = e?.message || String(e);
  }
  render();
  for (const p of state.photos) if (p.status === 'waiting') classifyPhoto(p);
}

// ---------- photos ----------
function loadImage(url) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Could not read image'));
    img.src = url;
  });
}

function addPhoto(url, tree, name) {
  const p = { id: nextId++, url, tree, name, status: 'waiting', leafClass: null, prob: null };
  state.photos.push(p);
  if (state.model.status === 'ready') classifyPhoto(p);
  return p;
}

async function classifyPhoto(p) {
  p.status = 'working';
  render();
  try {
    const img = await loadImage(p.url);
    const r = await state.model.classifier.classify(img);
    Object.assign(p, { status: 'done', leafClass: r.leafClass, prob: r.prob });
  } catch (e) {
    console.error('Classification failed', e);
    p.status = 'error';
  }
  render();
}

function nextTree() {
  const counts = [1, 2, 3].map((n) => state.photos.filter((p) => p.tree === n).length);
  return counts.indexOf(Math.min(...counts)) + 1;
}

function addFiles(files) {
  for (const f of files) {
    if (!f.type.startsWith('image/')) continue;
    addPhoto(URL.createObjectURL(f), nextTree(), f.name);
  }
  render();
}

function isAccepted(p) {
  return p.status === 'done' && p.leafClass !== 'other' && p.prob >= state.weights.config.photo_accept_prob;
}

// ---------- fusion ----------
function runFusion() {
  const num = (v) => (v === '' ? null : Number(v));
  return evaluate(state.weights, {
    photos: state.photos.filter((p) => p.status === 'done').map((p) => ({ leafClass: p.leafClass, prob: p.prob })),
    interview: state.answers,
    context: {
      rainFloweringPct: num(state.ctx.rainFlowering),
      rainBerryPct: num(state.ctx.rainBerry),
      soilPh: num(state.ctx.soilPh),
    },
  });
}

// ---------- rendering ----------
const leafSvg = `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 27C5 13 14 5 27 4c0 14-8 23-22 23z" fill="currentColor"/><path d="M7 25C12 19 17 13 24 8" stroke="#fff" stroke-width="1.6" fill="none" stroke-linecap="round"/><circle cx="23" cy="24" r="5" fill="#D7263D"/></svg>`;

function header() {
  const langs = Object.entries(state.packs).map(([code, p]) =>
    `<button class="lang ${code === state.lang ? 'on' : ''}" data-lang="${code}" aria-pressed="${code === state.lang}">${esc(p.manifest.language_name)}</button>`).join('');
  return `
  <header class="top">
    <div class="brand">
      <span class="logo">${leafSvg}</span>
      <h1>${esc(t('app.name'))}</h1>
    </div>
    <div class="langs" role="group" aria-label="${esc(t('lang.choose'))}">${langs}</div>
  </header>
  <p class="tagline">${esc(t('home.tagline'))}</p>
  <section class="note">
    <p>${esc(t('web.demo_note'))}</p>
    <a class="btn btn-sun" href="${APK}" target="_blank" rel="noopener">${esc(t('web.download'))}</a>
  </section>`;
}

function stepHead(n, title, extra = '') {
  return `<div class="step-head"><span class="num">${n}</span><h2>${esc(title)}</h2>${extra}</div>`;
}

function modelStatus() {
  const m = state.model;
  if (m.status === 'loading') return `<p class="status loading"><span class="spinner"></span>${esc(t('web.model_loading'))}</p>`;
  if (m.status === 'error') return `<div class="status error"><strong>${esc(t('photos.model_missing'))}</strong><br>${esc(t('web.model_error'))}<br><small>${esc(m.error || '')}</small></div>`;
  return `<p class="status ready">${esc(t('web.model_ready', { version: m.classifier.version }))}</p>`;
}

function photoCard(p) {
  const trees = [1, 2, 3].map((n) =>
    `<button class="tree ${p.tree === n ? 'on' : ''}" data-tree="${n}" data-photo="${p.id}" aria-pressed="${p.tree === n}" title="${esc(t('photos.tree', { n }))}">${n}</button>`).join('');
  let body;
  if (p.status === 'done') {
    const ok = isAccepted(p);
    body = `<div class="label">${esc(t(`web.label.${p.leafClass}`))} <span class="conf">${pct(p.prob)}</span></div>
      <div class="bar"><i style="width:${(p.prob * 100).toFixed(1)}%" class="${ok ? '' : 'muted'}"></i></div>
      <div class="badge ${ok ? 'ok' : 'no'}">${ok ? '✓ ' + esc(t('photos.ok')) : esc(t('web.not_counted'))}</div>
      ${ok ? '' : `<div class="hint">${esc(t('web.not_counted_hint', { pct: Math.round(state.weights.config.photo_accept_prob * 100) }))}</div>`}`;
  } else if (p.status === 'error') {
    body = `<div class="badge no">${esc(t('web.not_counted'))}</div>`;
  } else if (state.model.status === 'error') {
    body = `<div class="badge no">${esc(t('web.not_counted'))}</div>`;
  } else {
    body = `<div class="label muted"><span class="spinner"></span>${esc(t('web.classifying'))}</div>`;
  }
  return `<li class="photo" data-id="${p.id}">
    <div class="thumb"><img src="${esc(p.url)}" alt="${esc(p.name || '')}" loading="lazy">
      <button class="remove" data-remove="${p.id}" aria-label="${esc(t('web.remove'))}" title="${esc(t('web.remove'))}">×</button></div>
    <div class="meta">${body}
      <div class="trees"><span>${esc(t('photos.tree', { n: '' }).trim())}</span>${trees}</div>
    </div></li>`;
}

function photosSection() {
  const accepted = state.photos.filter(isAccepted).length;
  const treesUsed = new Set(state.photos.map((p) => p.tree)).size;
  const counter = state.photos.length
    ? `<div class="counters"><span class="pill ${accepted >= 5 ? 'good' : ''}">${esc(t('photos.leaves_count', { n: accepted, total: 5 }))}</span><span class="pill ${treesUsed >= 3 ? 'good' : ''}">${esc(t('web.trees_count', { n: treesUsed }))}</span></div>`
    : '';
  return `<section class="card step" id="photos">
    ${stepHead(1, t('web.step_photos'))}
    <p class="lead">${esc(t('photos.instruction'))}</p>
    ${modelStatus()}
    <div class="drop" id="drop">
      <label class="btn btn-green">
        <input type="file" id="file" accept="image/*" multiple hidden>
        ${esc(t('web.add_photos'))}
      </label>
      <span class="drop-hint">${esc(t('web.drop_hint'))}</span>
      <button class="btn btn-outline" id="samples">${esc(t('web.try_samples'))}</button>
    </div>
    ${counter}
    <ul class="photos">${state.photos.map(photoCard).join('')}</ul>
    ${state.photos.length ? `<button class="link" id="clear-photos">${esc(t('web.clear_photos'))}</button>` : ''}
  </section>`;
}

function interviewSection() {
  const qs = Object.entries(QUESTIONS);
  const items = qs.map(([q, answers], i) => {
    const chosen = state.answers[q];
    const btn = (a, label) =>
      `<button class="ans ${chosen === a ? 'on' : ''} ${a === 'unknown' ? 'unk' : ''}" data-q="${q}" data-a="${a}" aria-pressed="${chosen === a}">${esc(label)}</button>`;
    return `<li class="q ${chosen ? 'done' : ''}">
      <div class="q-meta">${esc(t('interview.progress', { n: i + 1, total: qs.length }))}</div>
      <h3>${esc(t(`question.${q}`))}</h3>
      <div class="answers">${answers.map((a) => btn(a, t(`answer.${q}.${a}`))).join('')}${btn('unknown', t('answer.unknown'))}</div>
    </li>`;
  }).join('');
  return `<section class="card step" id="interview">${stepHead(2, t('web.step_interview'))}<ol class="questions">${items}</ol></section>`;
}

function select(id, label, options, value) {
  return `<label class="field"><span>${esc(label)}</span>
    <select id="${id}">${options.map(([v, k]) => `<option value="${v}" ${v === value ? 'selected' : ''}>${esc(t(k))}</option>`).join('')}</select></label>`;
}

function plotSection() {
  return `<section class="card step" id="plot">
    ${stepHead(3, t('web.step_plot'))}
    <p class="lead small">${esc(t('web.plot_note'))}</p>
    <div class="fields">
      ${select('ctx-rf', t('feature.rain_flowering'), RAIN_OPTIONS, state.ctx.rainFlowering)}
      ${select('ctx-rb', t('feature.rain_berry'), RAIN_OPTIONS, state.ctx.rainBerry)}
      ${select('ctx-ph', t('feature.soil_ph'), PH_OPTIONS, state.ctx.soilPh)}
    </div>
    <button class="btn btn-green btn-big" id="see-result">${esc(t('web.see_result'))}</button>
  </section>`;
}

function featureLabel(id) {
  if (id.startsWith('photo.')) return t('feature.photos');
  if (id in QUESTIONS) return t(`question.${id}`);
  return t(`feature.${id}`);
}

function adviceCard(cause) {
  const card = state.packs[state.lang].cards[cause] || state.packs.en.cards[cause];
  if (!card) return '';
  const reviewed = state.cardsMeta[cause]?.reviewed_by;
  const list = (items) => `<ul>${items.map((s) => `<li>${esc(s)}</li>`).join('')}</ul>`;
  return `<div class="advice">
    <div class="advice-head"><h3>${esc(t('card.title'))}</h3>${reviewed ? '' : `<span class="badge review">${esc(t('card.awaiting_review'))}</span>`}</div>
    <p>${esc(card.what)}</p>
    ${SUSPECTED_CAUSES.has(cause) ? `<p class="suspected">${esc(t('card.suspected_note'))}</p>` : ''}
    <h4>${esc(t('card.check_title'))}</h4>${list(card.check)}
    <h4>${esc(t('card.steps_title'))}</h4>${list(card.first_steps)}
    <h4>${esc(t('card.officer_title'))}</h4><p class="officer">${esc(card.contact_officer)}</p>
  </div>`;
}

function howDecided(r) {
  const rows = r.ranked.map((c) => {
    const contribs = r.contributions[c]
      .slice().sort((a, b) => b[1] - a[1])
      .map(([o, w]) => `<li class="${w > 0 ? 'pos' : 'neg'}"><b>${w > 0 ? '+' : '−'}${Math.abs(w).toFixed(1)}</b> ${esc(reasonText(o.reason))}</li>`).join('');
    const prior = state.weights.causes[c].prior;
    return `<li class="how-row">
      <div class="how-top"><span class="how-name">${esc(t(`cause.${c}.name`))}</span>
        <span class="how-num">${esc(t('web.score'))} ${r.scores[c] >= 0 ? '+' : '−'}${Math.abs(r.scores[c]).toFixed(1)} · <b>${pct(r.probabilities[c])}</b></span></div>
      <div class="bar"><i style="width:${(r.probabilities[c] * 100).toFixed(1)}%"></i></div>
      ${contribs || prior ? `<ul class="contribs">${prior ? `<li>${esc(t('web.prior'))} ${prior}</li>` : ''}${contribs}</ul>` : ''}
    </li>`;
  }).join('');
  return `<details class="how" id="how" ${state.howOpen ? 'open' : ''}>
    <summary>${esc(t('web.how_decided'))}</summary>
    <p class="small">${esc(t('web.how_decided_note'))}</p>
    <p class="small">${esc(t('web.known_features', { n: r.knownFeatureCount, min: state.weights.config.min_known_features }))} · ${esc(t('photos.leaves_count', { n: r.photoSummary.accepted, total: r.photoSummary.total }))}</p>
    <ol class="how-list">${rows}</ol>
  </details>`;
}

function resultSection() {
  if (!state.showResult) return '';
  const r = runFusion();
  let body;
  if (r.status === 'ok') {
    const [top, ...others] = r.causes;
    body = `<div class="top-cause">
        <div class="conf-label ${top.label}">${esc(t(`confidence.${top.label}`))} · ${pct(top.prob)}</div>
        <h3 class="cause-name">${esc(t(top.nameId))}</h3>
        <h4>${esc(t('result.why'))}</h4>
        <ul class="reasons">${top.reasons.map((x) => `<li>${esc(reasonText(x))}</li>`).join('')}</ul>
      </div>
      ${others.length ? `<div class="others"><h4>${esc(t('result.also_check'))}</h4><ul>${others.map((o) =>
        `<li><div><b>${esc(t(o.nameId))}</b> <span class="conf-label sm ${o.label}">${esc(t(`confidence.${o.label}`))} · ${pct(o.prob)}</span></div>
         <ul class="reasons sm">${o.reasons.map((x) => `<li>${esc(reasonText(x))}</li>`).join('')}</ul></li>`).join('')}</ul></div>` : ''}
      ${adviceCard(top.cause)}`;
  } else {
    const seen = r.evidence.map((o) => `<li>${esc(reasonText(o.reason))}</li>`).join('');
    const unknownIds = [...new Set(r.unknownFeatures.map((f) => (f.startsWith('photo.') ? 'photo' : f)))];
    const notSeen = unknownIds.map((f) => `<li>${esc(featureLabel(f === 'photo' ? 'photo.x' : f))}</li>`).join('');
    body = `<div class="abstain">
        <h3>${esc(t('abstain.title'))}</h3>
        <p class="abstain-msg">${esc(t('abstain.message'))}</p>
        <ul class="abstain-reasons">${r.abstainReasons.map((a) => `<li>${esc(t(`abstain.${a}`))}</li>`).join('')}</ul>
      </div>
      <div class="cols">
        <div><h4>${esc(t('result.could_see'))}</h4><ul class="checks yes">${seen || '<li>—</li>'}</ul></div>
        <div><h4>${esc(t('result.could_not_see'))}</h4><ul class="checks no">${notSeen || '<li>—</li>'}</ul></div>
      </div>
      ${r.candidates.length ? `<div class="others"><h4>${esc(t('web.best_guesses'))}</h4><ul>${r.candidates.map((o) =>
        `<li><b>${esc(t(o.nameId))}</b> <span class="conf-label sm ${o.label}">${pct(o.prob)}</span></li>`).join('')}</ul></div>` : ''}`;
  }
  return `<section class="card step result ${r.status}" id="result">
    ${stepHead(4, t('result.title'))}
    ${body}
    ${howDecided(r)}
    <button class="btn btn-outline" id="start-over">${esc(t('web.start_over'))}</button>
  </section>`;
}

function footer() {
  return `<footer class="foot">
    <p><strong>${esc(t('web.disclaimer'))}</strong></p>
    <p>${esc(t('web.credits'))}</p>
    <p><a href="${REPO}" target="_blank" rel="noopener">${esc(t('web.source'))}</a> · <a href="${APK}" target="_blank" rel="noopener">${esc(t('web.download'))}</a></p>
  </footer>`;
}

function render() {
  document.documentElement.lang = state.lang;
  app.innerHTML = header() + photosSection() + interviewSection() + plotSection() + resultSection() + footer();
}

// ---------- events ----------
app.addEventListener('click', (e) => {
  const el = e.target.closest('button');
  if (!el) return;
  if (el.dataset.lang) {
    state.lang = el.dataset.lang;
    try { localStorage.setItem('mavuno.lang', state.lang); } catch { /* ignore */ }
    render();
  } else if (el.dataset.q) {
    state.answers[el.dataset.q] = el.dataset.a;
    render();
  } else if (el.dataset.tree) {
    const p = state.photos.find((x) => x.id === Number(el.dataset.photo));
    if (p) { p.tree = Number(el.dataset.tree); render(); }
  } else if (el.dataset.remove) {
    state.photos = state.photos.filter((x) => x.id !== Number(el.dataset.remove));
    render();
  } else if (el.id === 'samples') {
    state.photos = state.photos.filter((p) => !p.sample);
    for (const [file, tree] of SAMPLES) addPhoto(`samples/${file}`, tree, file).sample = true;
    render();
  } else if (el.id === 'clear-photos') {
    state.photos = [];
    render();
  } else if (el.id === 'see-result') {
    state.showResult = true;
    render();
    document.getElementById('result')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } else if (el.id === 'start-over') {
    Object.assign(state, { photos: [], answers: {}, ctx: { rainFlowering: '', rainBerry: '', soilPh: '' }, showResult: false, howOpen: false });
    render();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
});

app.addEventListener('change', (e) => {
  const id = e.target.id;
  if (id === 'file') addFiles(e.target.files);
  else if (id === 'ctx-rf') { state.ctx.rainFlowering = e.target.value; render(); }
  else if (id === 'ctx-rb') { state.ctx.rainBerry = e.target.value; render(); }
  else if (id === 'ctx-ph') { state.ctx.soilPh = e.target.value; render(); }
});

app.addEventListener('toggle', (e) => {
  if (e.target.id === 'how') state.howOpen = e.target.open;
}, true);

for (const type of ['dragenter', 'dragover']) {
  app.addEventListener(type, (e) => {
    const drop = e.target.closest?.('#drop');
    if (drop) { e.preventDefault(); drop.classList.add('over'); }
  });
}
app.addEventListener('dragleave', (e) => e.target.closest?.('#drop')?.classList.remove('over'));
app.addEventListener('drop', (e) => {
  if (!e.target.closest?.('#drop')) return;
  e.preventDefault();
  addFiles(e.dataTransfer.files);
});

boot();
