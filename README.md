# Mavuno

Offline yield-drop triage for smallholder coffee farmers. Entry for the World Bank × Hack-Nation "Small AI for Development" hackathon (Agriculture). The full spec is the PRD.

## Layout

```
app/          Gradle project
  fusion/       pure-Kotlin evidence fusion engine (no Android deps)
  contentpack/  language-pack loader; guarantees every UI string comes from a pack ID
content/      fusion_weights.json, advice cards, en/sw packs, scenario fixtures
ml/           leaf classifier training, calibration, export (not started)
datapack/     CHIRPS / iSDAsoil regional pack builder (not started)
backend/      FastAPI records API and officer page (not started)
docs/         data card, model card, responsible-AI note, demo script (not started)
scripts/      CI helpers (APK size gate)
```

## Run the tests

Needs JDK 17.

```bash
cd app && ./gradlew test
```

## Status

- Done: fusion engine, all 20 PRD scenario fixtures passing, en/sw string and card packs, the "no free text" render test, CI with the 40 MB size gate.
- Not done: Android app, ML pipeline, data pack, backend, audio.
- Not reviewed: fusion weights and advice cards are placeholders (`reviewed_by: null`). The Swahili text needs a native speaker's review. Card sources still need citations (`content/cards_meta.json`).
