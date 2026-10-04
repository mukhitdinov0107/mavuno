"""Shared constants and image loading for the leaf classifier pipeline."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
RAW = DATA / "raw"
IMAGES = DATA / "images"
MANIFEST = DATA / "manifest.csv"
RUNS = ROOT / "runs"
CONTENT_MODEL = ROOT.parent / "content" / "model"

# Order is the model's output order and must match leaf_model.json "labels".
CLASSES = ["healthy", "rust", "leaf_miner", "phoma", "cercospora", "other"]
SIZE = 224
SEED = 1234


def center_square(img):
    """Same crop the app applies before classifying (TfliteLeafClassifier.centerSquare)."""
    w, h = img.size
    s = min(w, h)
    left, top = (w - s) // 2, (h - s) // 2
    return img.crop((left, top, left + s, top + s))


def load_for_eval(path):
    """PIL path -> uint8 array (224, 224, 3), matching the app: center square, bilinear resize."""
    import numpy as np
    from PIL import Image

    with Image.open(path) as im:
        im = center_square(im.convert("RGB")).resize((SIZE, SIZE), Image.BILINEAR)
        return np.asarray(im, dtype=np.uint8)
