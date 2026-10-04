"""Build data/manifest.csv (path, label, source, split, group) from the extracted datasets.

- Maps each dataset's folder names onto our six classes.
- Groups near-duplicates with a rotation- and flip-invariant hash, so a rotated copy of a test image
  can never sit in the training set (PRD 9.3: avoid leakage).
- JMuBEN is split 70/15/15 by distinct leaf; each leaf counts once in val and test.
- BRACOL (whole leaves, Brazil) is split 50/10/40; its test part is our cross-source ("different farm") test.
- PlantDoc (non-coffee plants) is the "other" class.

    .venv/bin/python prepare_data.py
"""
import csv
import random
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image

from common import IMAGES, MANIFEST, SEED

JMUBEN = {"Cerscospora": "cercospora", "Leaf rust": "rust", "Phoma": "phoma", "Healthy": "healthy", "Miner": "leaf_miner"}
EXT = {".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG"}
# JMuBEN saves each leaf crop 16 times (rotations/flips), so sampling works in distinct leaves.
GROUPS_PER_CLASS = 600   # distinct leaves per class across all splits
TRAIN_COPIES = 4         # augmented copies of one leaf kept for training
# 16x16 dHash: at 9x9, plain green healthy crops collided and 18,984 images merged into 13 "leaves".
HASH_SIZE = 16


def dhash(arr):
    diff = arr[:, 1:] > arr[:, :-1]
    return int("".join("1" if b else "0" for b in diff.flatten()), 2)


def invariant_hash(path):
    """Smallest dHash over the 8 rotations/flips: rotated or mirrored copies hash the same."""
    try:
        with Image.open(path) as im:
            g = np.asarray(im.convert("L").resize((HASH_SIZE + 1, HASH_SIZE + 1), Image.BILINEAR), dtype=np.int16)
    except Exception:
        return path, None
    variants = []
    for k in range(4):
        r = np.rot90(g, k)
        variants += [r, r[:, ::-1]]
    return path, min(dhash(v) for v in variants)


def images_under(folder):
    return [p for p in Path(folder).rglob("*") if p.suffix in EXT]


def hash_all(paths):
    with ProcessPoolExecutor() as pool:
        return dict(pool.map(invariant_hash, paths, chunksize=256))


def jmuben_rows(rng):
    rows = []
    for folder, label in JMUBEN.items():
        paths = images_under(IMAGES / "jmuben" / folder)
        hashes = hash_all(paths)
        groups = defaultdict(list)
        for p, h in hashes.items():
            if h is not None:
                groups[h].append(p)
        keys = sorted(groups)
        rng.shuffle(keys)
        dup = sum(len(v) for v in groups.values() if len(v) > 1)
        sizes = Counter(len(v) for v in groups.values())
        print(f"jmuben/{label}: {len(paths)} images, {len(groups)} distinct, {dup} in duplicate groups, group sizes {sorted(sizes.items())[:5]}")
        chosen = keys[:GROUPS_PER_CLASS]
        n = len(chosen)
        for i, k in enumerate(chosen):
            split = "train" if i < 0.70 * n else "val" if i < 0.85 * n else "test"
            # Evaluation counts each distinct leaf once; training keeps a few augmented copies.
            members = sorted(groups[k])
            rng.shuffle(members)
            for p in members[: TRAIN_COPIES if split == "train" else 1]:
                rows.append({"path": str(p), "label": label, "source": "jmuben", "split": split, "group": f"j{k:x}"})
    return rows


def bracol_rows(rng):
    """BRACOL whole-leaf photos (Brazil). The published zip is truncated; we use the photos that extract.

    predominant_stress codes, checked against the per-disease columns: 0 healthy, 1 miner, 2 rust,
    3 phoma (brown leaf spot), 4 cercospora, 5 mixed (excluded). Split 50/10/40 per class; the 40%
    is the cross-source test set.
    """
    leaf = IMAGES / "bracol" / "coffee-datasets" / "coffee-datasets" / "leaf"
    codes = {"0": "healthy", "1": "leaf_miner", "2": "rust", "3": "phoma", "4": "cercospora"}
    by_label = defaultdict(list)
    with open(leaf / "dataset.csv") as f:
        for r in csv.DictReader(f):
            p = leaf / "images" / f"{r['id']}.jpg"
            if r["predominant_stress"] in codes and p.exists():
                by_label[codes[r["predominant_stress"]]].append(p)
    rows = []
    for label, paths in sorted(by_label.items()):
        paths.sort()
        rng.shuffle(paths)
        n = len(paths)
        for i, p in enumerate(paths):
            split = "train" if i < 0.5 * n else "val" if i < 0.6 * n else "test"
            rows.append({"path": str(p), "label": label, "source": "bracol", "split": split, "group": f"b{p.stem}"})
    print("bracol:", dict(Counter((r["split"], r["label"]) for r in rows)))
    return rows


def plantdoc_rows(rng):
    rows = []
    root = IMAGES.parent / "raw" / "plantdoc"
    for split_dir, target in (("train", None), ("test", "test")):
        for p in images_under(root / split_dir):
            split = target or ("val" if rng.random() < 0.15 else "train")
            rows.append({"path": str(p), "label": "other", "source": "plantdoc", "split": split, "group": f"p{p.stem}"})
    print("plantdoc:", Counter(r["split"] for r in rows))
    return rows


def readable(path):
    """Fully decodes the image; truncated or mislabelled files (e.g. the cut-off end of BRACOL) fail here."""
    try:
        with Image.open(path) as im:
            if im.format not in ("JPEG", "PNG"):
                return path, False
            im.load()
        return path, True
    except Exception:
        return path, False


def main():
    rng = random.Random(SEED)
    rows = jmuben_rows(rng) + bracol_rows(rng) + plantdoc_rows(rng)
    with ProcessPoolExecutor() as pool:
        ok = dict(pool.map(readable, [r["path"] for r in rows], chunksize=128))
    bad = [r["path"] for r in rows if not ok[r["path"]]]
    print(f"dropped {len(bad)} unreadable images:", bad[:5])
    rows = [r for r in rows if ok[r["path"]]]
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    with open(MANIFEST, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["path", "label", "source", "split", "group"])
        w.writeheader()
        w.writerows(rows)
    print("manifest:", len(rows), "rows")
    for split in ("train", "val", "test"):
        print(f"  {split}:", dict(Counter(r["label"] for r in rows if r["split"] == split)))


if __name__ == "__main__":
    main()
