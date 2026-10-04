// Leaf classifier in the browser: the app's own leaf.tflite, run unchanged in WebAssembly.
// Primary runtime: LiteRT.js (@litertjs/core, Google's current web runtime for .tflite files).
// tfjs-tflite (older TFLite build) is kept as a fallback, but it cannot load this model: the model uses
// FULLY_CONNECTED op version 12, newer than tfjs-tflite 0.0.1-alpha.10 supports.
// Preprocessing mirrors app/mobile/.../ml/LeafClassifier.kt: center square crop, halve until within 2x of
// the target, one final resize to 224x224, RGB 0-255 float32, then softmax(logits / temperature).

const TFJS_VERSION = '4.9.0';
const TFLITE_VERSION = '0.0.1-alpha.10';
const CDN = 'https://cdn.jsdelivr.net/npm/@tensorflow';
const LITERT_VERSION = '2.5.3';
const LITERT = `https://cdn.jsdelivr.net/npm/@litertjs/core@${LITERT_VERSION}`;
const SCRIPTS = [
  `${CDN}/tfjs-core@${TFJS_VERSION}/dist/tf-core.min.js`,
  `${CDN}/tfjs-backend-cpu@${TFJS_VERSION}/dist/tf-backend-cpu.min.js`,
  `${CDN}/tfjs-tflite@${TFLITE_VERSION}/dist/tf-tflite.min.js`,
];

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = src;
    s.crossOrigin = 'anonymous';
    s.onload = resolve;
    s.onerror = () => reject(new Error(`Could not load ${src}`));
    document.head.appendChild(s);
  });
}

let liteRtReady = null; // loadLiteRt may only be called once per page

export class LeafClassifier {
  constructor(meta, model) {
    this.meta = meta;
    this.model = model;
    this.version = meta.version;
  }

  static async load(base = 'content/model/') {
    const meta = await (await fetch(`${base}leaf_model.json`)).json();
    const url = new URL(`${base}leaf.tflite`, location.href).href;
    try {
      const litert = await import(`${LITERT}/+esm`);
      liteRtReady ??= litert.loadLiteRt(`${LITERT}/wasm/`);
      await liteRtReady;
      // Same model with the batch dimension fixed to 1 (see tools/make_static_model.py; identical logits).
      const staticUrl = new URL(`${base}leaf_static.tflite`, location.href).href;
      const model = await litert.loadAndCompile(staticUrl, { accelerator: 'wasm' });
      const c = new LeafClassifier(meta, null);
      c.runtime = 'LiteRT.js';
      c.run = async (input, size) => {
        const x = new litert.Tensor(input, [1, size, size, 3]);
        try {
          const outs = await model.run(x);
          const list = Array.isArray(outs) ? outs : Object.values(outs);
          const data = Array.from(list[0].toTypedArray());
          list.forEach((t) => t.delete());
          return data;
        } finally {
          x.delete();
        }
      };
      return c;
    } catch (e) {
      console.warn('LiteRT.js failed, trying tfjs-tflite', e);
    }
    for (const src of SCRIPTS) await loadScript(src);
    const tflite = window.tflite;
    if (!tflite) throw new Error('tfjs-tflite did not initialise');
    tflite.setWasmPath(`${CDN}/tfjs-tflite@${TFLITE_VERSION}/wasm/`);
    const model = await tflite.loadTFLiteModel(url, { numThreads: 1 });
    const c = new LeafClassifier(meta, model);
    c.runtime = 'tfjs-tflite';
    c.run = async (input, size) => {
      const tf = window.tf;
      const x = tf.tensor(input, [1, size, size, 3], 'float32');
      let out;
      try {
        out = model.predict(x);
        if (!(out instanceof tf.Tensor)) out = Array.isArray(out) ? out[0] : Object.values(out)[0];
        return Array.from(await out.data());
      } finally {
        x.dispose();
        if (out && out.dispose) out.dispose();
      }
    };
    return c;
  }

  /** Returns { leafClass, prob, probs } for an <img> / ImageBitmap / canvas. */
  async classify(source) {
    const size = this.meta.input_size || 224;
    const canvas = downscale(centerSquare(source), size);
    const { data } = canvas.getContext('2d', { willReadFrequently: true }).getImageData(0, 0, size, size);
    const [lo, hi] = this.meta.input_range || [0, 255];
    const input = new Float32Array(size * size * 3);
    for (let i = 0, j = 0; i < data.length; i += 4) {
      input[j++] = lo + (data[i] / 255) * (hi - lo);
      input[j++] = lo + (data[i + 1] / 255) * (hi - lo);
      input[j++] = lo + (data[i + 2] / 255) * (hi - lo);
    }
    const raw = await this.run(input, size);
    const logits = this.meta.output === 'probs' ? raw.map((p) => Math.log(Math.max(p, 1e-6))) : raw;
    const probs = softmax(logits, this.meta.temperature || 1);
    let best = 0;
    for (let i = 1; i < probs.length; i++) if (probs[i] > probs[best]) best = i;
    return { leafClass: this.meta.labels[best], prob: probs[best], probs };
  }
}

function softmax(logits, t) {
  const scaled = logits.map((l) => l / t);
  const max = Math.max(...scaled);
  const exps = scaled.map((v) => Math.exp(v - max));
  const sum = exps.reduce((a, b) => a + b, 0);
  return exps.map((e) => e / sum);
}

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

function sizeOf(src) {
  return { w: src.naturalWidth || src.videoWidth || src.width, h: src.naturalHeight || src.videoHeight || src.height };
}

function centerSquare(src) {
  const { w, h } = sizeOf(src);
  const side = Math.min(w, h);
  const c = makeCanvas(side, side);
  c.getContext('2d').drawImage(src, Math.floor((w - side) / 2), Math.floor((h - side) / 2), side, side, 0, 0, side, side);
  return c;
}

function draw(src, w, h) {
  const c = makeCanvas(w, h);
  const ctx = c.getContext('2d');
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'medium';
  ctx.drawImage(src, 0, 0, w, h);
  return c;
}

function downscale(square, target) {
  let b = square;
  while (b.width >= target * 2) b = draw(b, Math.floor(b.width / 2), Math.floor(b.height / 2));
  return b.width === target ? b : draw(b, target, target);
}
