# Mavuno web demo

A static, browser-only demo of the Mavuno Android app. It runs the app's real leaf model (`leaf.tflite`)
and a line-by-line JavaScript port of the fusion engine (`app/fusion`), with the app's English and
Kiswahili content packs. No build step and no server code, so GitHub Pages can serve this folder as-is.

## Run locally

```sh
python3 -m http.server -d web 8080
# open http://localhost:8080
```

It has to be served over HTTP; opening `index.html` from disk won't work, because it fetches JSON
and the model.

## Test the fusion port

```sh
node web/test_fusion.mjs
```

This runs all scenarios in `content/fixtures/fusion_scenarios.json` (no dependencies). It checks the
same things as `FusionScenarioTest.kt`.

## Files

| File | What it is |
|---|---|
| `index.html`, `styles.css`, `app.js` | The single-page UI |
| `fusion.js` | Port of `Features.kt`, `FusionEngine.kt`, `Model.kt` |
| `classifier.js` | Preprocessing (center crop, halve, resize to 224) and inference |
| `content/` | Copies of the app content: weights, packs, card metadata, model. Re-copy them when they change |
| `content/web_strings.json` | Text used only by the web demo. The Kiswahili here is a draft |
| `content/model/leaf_static.tflite` | `leaf.tflite` with the batch dimension fixed to 1, made by `tools/make_static_model.py`. Logits are identical |
| `samples/` | 6 leaf photos from BRACOL and JMuBEN (CC BY 4.0), taken from the app's parity test set |

## Model runtime

The model runs with [LiteRT.js](https://www.npmjs.com/package/@litertjs/core) (`@litertjs/core@2.5.3`,
loaded from jsDelivr, WASM/XNNPACK on the CPU). `@tensorflow/tfjs-tflite@0.0.1-alpha.10` stays in the
code as a fallback, but it can't load this model: its TFLite build is too old for `FULLY_CONNECTED`
op version 12. LiteRT.js rejects a dynamic batch input (`[-1,224,224,3]`), so the page loads
`leaf_static.tflite`, the same model with batch 1.

If `content/model/leaf.tflite` changes, regenerate the static copy:

```sh
ml/.venv/bin/python web/tools/make_static_model.py content/model/leaf.tflite web/content/model/leaf_static.tflite
```
