# Responsible AI note

The challenge's pass/fail criterion: are the limits respected, and is the account of privacy, consent, bias and human oversight credible? This is what Mavuno does today, and where it falls short.

## Human in the loop

- The app **informs, it never acts.** It does not order inputs, sell, spray or message anyone on the farmer's behalf.
- **Treatment decisions go to a person.** Advice cards contain only cultural practices (pruning, sanitation, shade, mulching, soil testing). There are no product names or doses, and every card ends with when to contact the extension officer.
- **Berry diseases are always "suspected"**, because no image model backs them; the card asks the farmer to show a sample to the officer.

## Fail-safe: "not sure, ask a person"

The app abstains instead of guessing when any of these hold (thresholds in `content/fusion_weights.json`):

| Rule | Why |
|---|---|
| Fewer than 3 photos the leaf model is ≥70% sure of | Not enough visual evidence |
| Top cause below 40% | Nothing stands out |
| Fewer than 5 known pieces of evidence | Too many "I don't know" answers or no data for the plot |
| Photos and interview point to different causes, nearly tied | The evidence conflicts |

The abstain screen plays *"Sina uhakika. Tafadhali muulize afisa ugani"* ("I am not sure, please ask the extension officer"), shows what the app could and could not check, and offers to send the report flagged `needs_human`. Each rule has a unit test (`app/fusion`, scenarios S13–S18).

## No hallucinations

- No generative model anywhere in the advice path. Causes, reasons and advice come from a fixed list in the content packs.
- `ContentPackTest` renders every fusion output for every test scenario in English and Swahili and fails on any missing string; `UiStringIdsTest` fails if a screen asks for text that is not in both packs.

## Consent and data

| Question | Answer |
|---|---|
| What is stored? | Name, phone number, cooperative, plot GPS, each check's photos, answers and result. |
| Where? | On the phone, in app-private storage. Records are in a SQLCipher-encrypted database; the key is random and wrapped by the Android Keystore. Excluded from cloud backup and device transfer. |
| What leaves the phone? | Nothing, unless the farmer says **yes** on the consent screen, asked **per check**. Photos are a **separate** yes. Default is no. |
| Who can read shared reports? | The farmer's own cooperative (planned backend, token per cooperative). |
| Lost or shared phone? | Set-up says plainly that the phone may be shared. "Delete all my data" in Settings wipes records, photos, the send queue, profile and the database key. |

## Bias and limits (see the data and model cards)

- The leaf model was trained on Kenyan leaf **close-ups** and Brazilian whole leaves on **white backgrounds**. It has not been tested on photos taken in a Kenyan field with hands, soil or shadows, or on local varieties (SL28, SL34, Ruiru 11, Batian).
- On whole leaves from a different country it is 78% accurate overall, and 88% on the photos it accepts. It misses leaf miner half the time and over-calls Cercospora.
- The scoring weights and advice cards are **drafts, not reviewed by an agronomist**. The app shows "Awaiting agronomist review" on every card until a named reviewer signs off.
- Swahili text needs review by a native speaker; no audio is recorded yet.

**Mavuno is a prototype. It must not be used for real farm decisions until the advice and weights are reviewed and the model is tested on field photos.**
