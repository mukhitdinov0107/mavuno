# Model card: leaf-v1

The only learned component in Mavuno. It reads one photo of a coffee leaf and returns one of six classes with a calibrated probability. Everything after that (combining photos with interview, rain and soil) is the expert-weighted fusion table, not this model.

## Summary

| | |
|---|---|
| Task | One leaf photo → `healthy`, `rust`, `leaf_miner`, `phoma`, `cercospora`, `other` |
| Architecture | MobileNetV3-Small (ImageNet weights), new 6-way head, fine-tuned top 60 layers |
| Input | 224×224 RGB, 0–255; the app center-crops a square and downscales by halving |
| Output | Logits; softmax with temperature **T = 1.1** (fitted on validation) |
| File | `content/model/leaf.tflite`, **1.11 MB**, LiteRT, int8 weights / float activations |
| Speed | **~53 ms** per photo (median) on a 2 GB-RAM Android 16 emulator, CPU only |
| Acceptance | A photo counts as evidence when top probability ≥ **0.70** and class ≠ `other` |
| Data | See [data_card.md](data_card.md). JMuBEN, JMuBEN2, BRACOL, PlantDoc, all CC BY 4.0 |
| Code | `ml/prepare_data.py`, `ml/train.py`, `ml/export_eval.py` |

## Results (exported model, held-out test sets)

| Test set | What it is | n | Accuracy | Macro-F1 | ECE |
|---|---|---|---|---|---|
| JMuBEN test | Kenyan leaf crops, same farm as training, one image per distinct crop | 200 | **97.5%** | **0.98** | 0.02 |
| BRACOL test | Whole leaves, Brazil, different camera and background | 535 | **78.3%** | **0.75** | 0.03 |
| PlantDoc test | Other crops' leaves (should be rejected) | 236 | **99.6% rejected** | | |

**At the 0.70 acceptance threshold on BRACOL:** 70.5% of coffee photos are accepted, and **88.1%** of accepted photos are correct.

| Threshold | 0.50 | 0.60 | 0.70 | 0.80 | 0.90 |
|---|---|---|---|---|---|
| Coverage (BRACOL) | 91% | 80% | 71% | 60% | 44% |
| Accuracy on accepted | 82% | 86% | 88% | 91% | 95% |

### Per class on BRACOL (the honest numbers)

| Class | Precision | Recall | n |
|---|---|---|---|
| Healthy | 0.68 | 0.95 | 56 |
| Rust | 0.93 | 0.82 | 186 |
| Leaf miner | 0.96 | **0.51** | 101 |
| Phoma | 0.96 | 0.88 | 138 |
| Cercospora | **0.36** | 0.76 | 54 |

- **Leaf miner is missed half the time** on whole leaves; most misses are called Cercospora or healthy.
- **Cercospora is over-called:** only about a third of Cercospora predictions are right. Fusion needs a class on ≥2 photos and ≥30% of accepted photos before it counts, which damps single false calls, but the Cercospora advice card will appear more often than it should.
- **Rust**, the most important disease for yield, is the strongest class.

## PRD acceptance (section 11)

| Criterion | Result |
|---|---|
| Macro-F1 ≥ 0.85 on the studio-style test set | ✅ 0.98 (JMuBEN) |
| Field results reported honestly, with the gap | ✅ 0.75 macro-F1 on BRACOL, a gap of 0.23 |
| Accuracy on accepted field photos ≥ 0.85, with coverage | ✅ 0.88 at 71% coverage |
| ≥ 90% of non-coffee images rejected | ✅ 99.6%, but only against other crops' leaves |
| Model ≤ 5 MB, int8 | ⚠️ 1.11 MB; **int8 weights only**, see below |
| ≤ 1.5 s per photo on a 2 GB device | ✅ ~53 ms on the emulator; real low-end phones will be slower but far inside budget |

**Why not full int8.** Full-int8 post-training quantization (weights and activations) dropped validation accuracy from 95% to 54%, with 300 or 1,000 calibration images, uint8 or float I/O. MobileNetV3's hard-swish and squeeze-excite activations quantize poorly. Weight-only int8 keeps the file at 1.1 MB and costs about 2 points (95% → 93% on a 300-image validation sample). Options for a full-int8 model: quantization-aware training, or MobileNetV2 / EfficientNet-Lite0, which quantize well; compare on BRACOL before switching.

## App parity

`app/mobile/src/androidTest/.../ClassifierParityTest` runs the shipped model through the app's own preprocessing on 18 held-out photos and checks it agrees with the Python evaluation. It found that a single bilinear resize from large photos aliased and shifted one confidence from 0.69 to 0.42; the app now halves the image step by step before the final resize (worst gap now 0.03). Run it with `./gradlew :mobile:connectedDebugAndroidTest` on a device or emulator; it is not in CI.

## Known limits

- Not tested on photos taken with the app in a real field: hands, soil, shadows and several leaves in frame.
- Not tested on local Kenyan varieties (SL28, SL34, Ruiru 11, Batian).
- `other` was trained on other crops' leaves only; a photo of soil or a hand may not be rejected by the model (the app's quality gate catches some of these).
- JMuBEN's healthy and Cercospora classes have very few distinct crops (~17 and ~82), so those classes lean on Brazilian data.
