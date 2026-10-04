# Mavuno

Offline yield-drop triage for smallholder coffee farmers. Entry for the World Bank × Hack-Nation "Small AI for Development" hackathon (Agriculture). The full spec is the PRD.

## Layout

```
app/          Gradle project
  fusion/       pure-Kotlin evidence fusion engine (no Android deps)
  contentpack/  language-pack loader; guarantees every UI string comes from a pack ID
  mobile/       Android app (Kotlin, Compose, CameraX, LiteRT, Room + SQLCipher, WorkManager)
content/      fusion_weights.json, advice cards, en/sw packs, scenario fixtures
ml/           leaf classifier: data prep, training, int8 export, evaluation
datapack/     CHIRPS / iSDAsoil regional pack builder (not started)
backend/      FastAPI records API and officer page (not started)
docs/         data card, model card (responsible-AI note and demo script still to write)
scripts/      CI helpers (APK size gate)
```

## Build and test

Needs JDK 17 and the Android SDK (platform 37). Set `sdk.dir` in `app/local.properties`.

```bash
cd app && ./gradlew test :mobile:assembleRelease
```

The APK lands in `app/mobile/build/outputs/apk/release/`. Install it on a phone with `adb install -r <apk>`.
The backend URL defaults to `http://10.0.2.2:8000` (the host, from the emulator); override with `-Pmavuno.backendUrl=…`.

## Train the leaf model

```bash
cd ml && python3.11 -m venv .venv && .venv/bin/pip install tensorflow==2.20.* pillow numpy
.venv/bin/python prepare_data.py && .venv/bin/python train.py --run leaf-v1 && .venv/bin/python export_eval.py --run leaf-v1
```

Datasets and licences are in [docs/data_card.md](docs/data_card.md); results in [docs/model_card.md](docs/model_card.md).

## Status

- Done: fusion engine (20/20 scenarios); en/sw packs; Android app with the full offline check flow; leaf model leaf-v1 (1.1 MB, 97.5% on Kenyan test crops, 78% on Brazilian whole leaves, 88% correct on the photos it accepts).
- Not done: regional rain/soil pack (every plot is "outside"), backend and officer page (reports stay queued), audio clips.
- Not reviewed: fusion weights and advice cards are placeholders (`reviewed_by: null`); Swahili text needs a native speaker; card sources need citations.
