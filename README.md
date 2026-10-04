# Mavuno: offline yield-drop triage for smallholder coffee farmers

**World Bank × Hack-Nation "Small AI for Development" · Track B: Agriculture**

*Mavuno* is Swahili for "harvest". Noor's coffee yields have slipped and she doesn't know why; the extension officer visits twice a year. Mavuno is an Android app that works with **no internet**. She photographs a few coffee leaves and answers 8 picture questions. The app tells her the likely cause, why it thinks so, and safe first steps. When it isn't sure, it says so and sends her to the extension officer. Each check becomes a report that waits on the phone and goes to her cooperative once there is signal, with her consent.

- **Android app (APK, 13 MB):** [Releases](https://github.com/mukhitdinov0107/mavuno/releases/latest)
- **Web demo, runs the same model and scoring in your browser:** https://mukhitdinov0107.github.io/mavuno/
- **Language:** Kiswahili (default) and English, switchable at any time
- **One-page report:** [docs/Solo Builder_OnePager.pdf](docs/Solo%20Builder_OnePager.pdf) · **Videos:** demo, tech walkthrough and team intro are in the [release](https://github.com/mukhitdinov0107/mavuno/releases/latest)

## Summary

Kenya's coffee yields fell from 973 to 474 kg per hectare between 1963/64 and 2021/22, across more than 800,000 smallholders, while each extension officer serves about 1,380 farmers against FAO's recommended 400 (Kenya Ministry of Agriculture and Livestock Development, *Coffee Development and Marketing Strategy 2024–2029*; *Agriculture Extension Manual*, 2025). A farmer like Noor sees her harvest drop and may wait months for anyone to tell her why.

Mavuno is an Android app that answers that question **offline, in Swahili, on a phone the family already has.** Noor's daughter photographs five coffee leaves from three trees; a 1.1 MB leaf model on the phone reads each one. Noor answers eight questions by tapping pictures (tree age, pruning, fertilizer, shade, berries, harvest), so reading isn't required. A transparent scoring table weighs the leaves, the answers and the plot data, and shows the likely cause, the two clues behind it, and a vetted advice card with safe first steps and when to call the officer. When the evidence is weak it says so: *"Sina uhakika, muulize afisa ugani"* ("I'm not sure, ask the extension officer"). With her consent, each check becomes an encrypted report that waits on the phone and goes to her cooperative when there is signal.

What works today: the complete offline check flow in Swahili and English; a leaf model that is 97.5% accurate on Kenyan leaf crops and 88% on the confident photos of whole leaves from another country; abstain rules covered by tests; consent, encryption and delete-all; and a browser demo running the same model. Everything the app says comes from a fixed, reviewable list, so it can't make anything up.

## How it works

```
leaf photos ──► quality check ──► leaf model (AI, on-device) ──► keep photos it is ≥70% sure of ─┐
8 picture answers ───────────────────────────────────────────────────────────────────────────────┤
rain & soil for the plot (offline data pack; planned) ───────────────────────────────────────────┤
                                                                                                  ▼
                                       turn everything into clues ──► score 11 causes (fixed table)
                                                                                                  ▼
                               sure enough? ── yes ──► likely cause + 2 reasons + vetted advice card
                                            └─ no ───► "Not sure, ask the extension officer"
```

1. **The learned part is image recognition.** A MobileNetV3-Small, fine-tuned on coffee leaves, labels each photo: healthy, rust, leaf miner, phoma, cercospora, or not a coffee leaf. It is 1.1 MB, takes about 50 ms per photo on a low-end phone and runs fully offline.
2. **The advice part is rules, deliberately.** Photo results, answers and plot data become clues. A transparent table (`content/fusion_weights.json`) adds or removes points for each of 11 causes. The top cause picks one **pre-written** advice card. Nothing is generated, so nothing can be made up, and an agronomist can correct the table without retraining.

**Why AI, and not SMS or a spreadsheet:** SMS can't read a leaf. A spreadsheet can't weigh what the leaves show against how the trees were managed and what the rain did. The model does the one thing rules can't, which is see; the rules do the one thing the model shouldn't, which is decide what a farmer is told.

## Results (leaf model, held-out photos)

| Test set | Accuracy | Macro-F1 |
|---|---|---|
| Kenyan leaf close-ups, same farm as training (JMuBEN) | 97.5% | 0.98 |
| **Whole leaves from Brazil, different camera (BRACOL)** | **78.3%** | **0.75** |
| …photos the model is ≥70% sure of | 88.1% (71% of photos) | |
| Non-coffee leaves rejected (PlantDoc) | 99.6% | |

The scoring logic passes 20 hand-written farm scenarios, including every abstain rule. Details, and where the model is weak, are in [docs/model_card.md](docs/model_card.md).

**A data finding worth knowing:** JMuBEN's 18,984 "healthy" images are about 17 distinct leaf crops, each saved many times rotated or flipped. A random split would put copies of one leaf in both training and test sets and score near 100% while proving little. We split by distinct leaf. See [docs/data_card.md](docs/data_card.md).

## Guardrails

- **Fail-safe:** abstains when there are fewer than 3 confident photos, the top cause is below 40%, there are fewer than 5 known clues, or photos and answers conflict.
- **No free text:** every word on screen comes from a reviewed list in both languages, and a test fails the build otherwise.
- **Human in the loop:** cultural practices only, no chemicals or doses; treatment decisions go to the extension officer.
- **Consent and privacy:** nothing is shared without a yes, asked per check, with a separate yes for photos. Data is encrypted on the phone, and "Delete all my data" wipes everything.

More in [docs/responsible_ai.md](docs/responsible_ai.md).

## Repository

```
app/          Gradle project
  fusion/       scoring engine, pure Kotlin, 20 scenario tests
  contentpack/  language packs; guarantees every UI string exists in sw and en
  mobile/       Android app (Compose, CameraX, LiteRT, Room + SQLCipher, WorkManager)
content/      fusion weights, advice cards, Swahili/English packs, leaf model, test scenarios
ml/           data prep (duplicate-aware splits), training, export, evaluation
web/          browser demo running the same model and scoring
docs/         data card, model card, responsible-AI note
```

## Build and run

Needs JDK 17 and the Android SDK (platform 37); set `sdk.dir` in `app/local.properties`.

```bash
cd app && ./gradlew test :mobile:assembleRelease      # tests + APK (≈13 MB, limit 40 MB)
adb install -r mobile/build/outputs/apk/release/mobile-release.apk
```

Retrain the leaf model (datasets and licences in [docs/data_card.md](docs/data_card.md)):

```bash
cd ml && python3.11 -m venv .venv && .venv/bin/pip install tensorflow==2.20.* pillow numpy
.venv/bin/python prepare_data.py && .venv/bin/python train.py --run leaf-v1 && .venv/bin/python export_eval.py --run leaf-v1
```

## Status and limits

- **Done:** offline check flow; leaf model; scoring with abstain; Swahili and English; encrypted storage; consent; send queue; history; delete-all.
- **Not done:** the regional rain/soil pack (CHIRPS, iSDAsoil), so every plot currently shows as "outside the data area"; the cooperative backend and officer page, so reports wait on the phone; recorded audio.
- **Not reviewed:** scoring weights and advice cards are drafts awaiting an agronomist; Swahili awaits a native-speaker review; the model is untested on real Kenyan field photos.

Prototype only; not for real farm decisions.

## Data and licence

Leaf images: [JMuBEN](https://data.mendeley.com/datasets/t2r6rszp5c/1), [JMuBEN2](https://data.mendeley.com/datasets/tgv3zb82nd/1), [BRACOL](https://data.mendeley.com/datasets/yy2k5y8mxg/1), [PlantDoc](https://github.com/pratikkayal/PlantDoc-Dataset), all CC BY 4.0. Typeface: Atkinson Hyperlegible (OFL). Code: MIT, see [LICENSE](LICENSE).
