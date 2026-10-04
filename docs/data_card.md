# Data card: Mavuno leaf classifier

## Sources

| Dataset | What | Where from | Licence | Used for |
|---|---|---|---|---|
| [JMuBEN](https://data.mendeley.com/datasets/t2r6rszp5c/1) | Arabica leaf crops: Cercospora, rust, Phoma | Mutira plantation, Kirinyaga, Kenya | CC BY 4.0 | train / val / test |
| [JMuBEN2](https://data.mendeley.com/datasets/tgv3zb82nd/1) | Arabica leaf crops: leaf miner, healthy | same plantation | CC BY 4.0 | train / val / test |
| [BRACOL](https://data.mendeley.com/datasets/yy2k5y8mxg/1) | Whole arabica leaves, white background, 2048×1024 | Espírito Santo, Brazil | CC BY 4.0 | 50% train, 10% val, **40% held-out cross-source test** |
| [PlantDoc](https://github.com/pratikkayal/PlantDoc-Dataset) | Leaves of 13 other crops, field photos | web-sourced | CC BY 4.0 | the `other` class (not a coffee leaf) |

Attribution: JMuBEN by Jepkoech, Mugo, Kenduiywo and Too (2021); BRACOL by Krohling, Esgario and Ventura (2019); PlantDoc by Singh et al. (2020).

## What we found in the data

**JMuBEN is far smaller than its file count.** Each leaf crop is stored many times as rotated, flipped or slightly shifted copies. Grouping files with a rotation- and flip-invariant 16×16 difference hash, then checking groups by eye, gives:

| Class | Files | Distinct crops |
|---|---|---|
| Healthy | 18,984 | ~17 |
| Cercospora | 7,681 | ~82 |
| Phoma | 6,571 | ~181 |
| Rust | 8,336 | ~521 |
| Leaf miner | 16,978 | ~546 |

A random file-level split puts copies of the same leaf in both training and test sets and inflates scores. We split by distinct crop, keep at most 4 copies of a crop for training, and count each crop **once** in validation and test.

**The BRACOL zip on Mendeley is truncated.** Standard unzip rejects it; streaming extraction recovers 1,402 of 1,747 images and the label file. Label codes were checked against the per-disease columns: 0 healthy, 1 leaf miner, 2 rust, 3 Phoma (brown leaf spot), 4 Cercospora, 5 mixed (excluded, 62 images).

## What the data does not cover (PRD 9.2)

- **No photos from the demo region's farms as the app would take them.** JMuBEN is from nearby Kirinyaga, but as 128×128 close-up crops; the app photographs whole leaves, upscaled to 224×224.
- **Backgrounds.** JMuBEN crops are all leaf surface; BRACOL leaves sit on plain white. Real photos will have hands, soil, shadows and several leaves.
- **Healthy and Cercospora rely mostly on BRACOL** (Brazilian conditions), because JMuBEN has so few distinct crops of each.
- **No berry images**, so coffee berry disease and berry borer rely on the interview alone.
- **Local varieties** (SL28, SL34, Ruiru 11, Batian) are not labelled in any source.
- **The `other` class** is other crops' leaves only; it has no soil, hands, sky or random objects.
- RoCoLe (robusta, Ecuador) was planned as an extra field test but its files were not available through the public API.

## Splits

Built by `ml/prepare_data.py` into `ml/data/manifest.csv` (not committed; regenerate from the sources above).
