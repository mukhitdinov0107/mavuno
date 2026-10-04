# Mavuno

Offline yield-drop triage for smallholder coffee farmers. Entry for the World Bank × Hack-Nation "Small AI for Development" hackathon (Agriculture). The full spec is the PRD.

## Layout

```
app/          Gradle project
  fusion/       pure-Kotlin evidence fusion engine (no Android deps)
  contentpack/  language-pack loader; guarantees every UI string comes from a pack ID
  mobile/       Android app (Kotlin, Compose, CameraX, LiteRT, Room + SQLCipher, WorkManager)
content/      fusion_weights.json, advice cards, en/sw packs, scenario fixtures
ml/           leaf classifier training, calibration, export (not started)
datapack/     CHIRPS / iSDAsoil regional pack builder (not started)
backend/      FastAPI records API and officer page (not started)
docs/         data card, model card, responsible-AI note, demo script (not started)
scripts/      CI helpers (APK size gate)
```

## Build and test

Needs JDK 17 and the Android SDK (platform 37). Set `sdk.dir` in `app/local.properties`.

```bash
cd app && ./gradlew test :mobile:assembleRelease
```

The APK lands in `app/mobile/build/outputs/apk/release/`. Install it on a phone with `adb install -r <apk>`.
The backend URL defaults to `http://10.0.2.2:8000` (the host, from the emulator); override with `-Pmavuno.backendUrl=…`.

## Status

- Done: fusion engine (20/20 scenarios), en/sw packs, the Android app's full check flow (set-up with GPS, photos with quality gate, 8-question interview, result or abstain screen with reasons and advice cards, consent, encrypted local store, sync queue, history, delete all data). Release APK is 12 MB.
- Placeholders in the app: no leaf model yet (photos are kept but not read, so every check abstains; debug builds let you label photos by hand to exercise the flow), no regional data pack yet (every plot is "outside"), no audio clips yet (replay buttons are disabled), no backend yet (reports stay queued).
- Not reviewed: fusion weights and advice cards are placeholders (`reviewed_by: null`). The Swahili text needs a native speaker's review. Card sources still need citations (`content/cards_meta.json`).
