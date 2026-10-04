"""Export the trained model to LiteRT, calibrate it, and evaluate the exported file (PRD 7.1, 9.3).

Quantization: int8 weights with float activations ("dynamic range") by default. Full-int8 activation
quantization of this MobileNetV3-Small dropped validation accuracy from 95% to 54% (hard-swish and
squeeze-excite layers quantize poorly), even with 1,000 calibration images. Weight-only int8 keeps the
file at ~1.2 MB and loses ~2 points. Pass --quant full to reproduce the full-int8 result.

All reported numbers come from the exported file, run the way the app runs it.

    .venv/bin/python export_eval.py --run leaf-v1
"""
import argparse
import csv
import json
import random
import shutil
from datetime import date

import numpy as np
import tensorflow as tf
import keras

from common import CLASSES, CONTENT_MODEL, MANIFEST, RUNS, SEED, load_for_eval

OTHER = CLASSES.index("other")


def rows_where(rows, **kw):
    return [r for r in rows if all(r[k] == v for k, v in kw.items())]


def export(model, rep_rows, path, quant):
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    if quant == "full":
        def representative():
            for r in rep_rows:
                yield [load_for_eval(r["path"])[None].astype(np.float32)]

        conv.representative_dataset = representative
        conv.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        conv.inference_input_type = tf.uint8
        conv.inference_output_type = tf.int8
    path.write_bytes(conv.convert())


class LiteModel:
    """Runs the .tflite exactly as the app does: 0-255 pixels in (quantized if the input is int), logits out."""

    def __init__(self, path):
        self.it = tf.lite.Interpreter(model_path=str(path), num_threads=4)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]
        self.scale, self.zero = self.out["quantization"] if self.out["dtype"] != np.float32 else (1.0, 0)

    def logits(self, rows):
        res = []
        for r in rows:
            pixels = load_for_eval(r["path"])[None]
            if self.inp["dtype"] == np.float32:
                pixels = pixels.astype(np.float32)
            else:
                s, z = self.inp["quantization"]
                pixels = np.clip(np.round(pixels / s + z), 0, 255).astype(self.inp["dtype"])
            self.it.set_tensor(self.inp["index"], pixels)
            self.it.invoke()
            q = self.it.get_tensor(self.out["index"])[0].astype(np.float32)
            res.append((q - self.zero) * self.scale)
        return np.array(res)


def softmax(z, t=1.0):
    z = z / t
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


def fit_temperature(logits, y):
    """Temperature scaling: the T that minimises validation negative log-likelihood."""
    best = (1.0, 1e9)
    for t in np.arange(0.5, 5.01, 0.05):
        p = softmax(logits, t)
        nll = -np.mean(np.log(p[np.arange(len(y)), y] + 1e-12))
        if nll < best[1]:
            best = (float(round(t, 2)), nll)
    return best[0]


def ece(probs, y, bins=10):
    conf = probs.max(1)
    pred = probs.argmax(1)
    total = 0.0
    for lo in np.linspace(0, 1, bins, endpoint=False):
        m = (conf > lo) & (conf <= lo + 1 / bins)
        if m.any():
            total += m.mean() * abs((pred[m] == y[m]).mean() - conf[m].mean())
    return float(total)


def per_class(pred, y):
    out = {}
    f1s = []
    for i, c in enumerate(CLASSES):
        tp = int(((pred == i) & (y == i)).sum())
        fp = int(((pred == i) & (y != i)).sum())
        fn = int(((pred != i) & (y == i)).sum())
        if tp + fn == 0:
            continue
        p = tp / (tp + fp) if tp + fp else 0.0
        r = tp / (tp + fn)
        f1 = 2 * p * r / (p + r) if p + r else 0.0
        f1s.append(f1)
        out[c] = {"precision": round(p, 3), "recall": round(r, 3), "f1": round(f1, 3), "n": tp + fn}
    return out, float(np.mean(f1s)) if f1s else 0.0


def coverage_curve(probs, y):
    """For each threshold: share of coffee photos accepted as evidence, and accuracy on those."""
    pred = probs.argmax(1)
    conf = probs.max(1)
    curve = []
    for t in np.arange(0.40, 0.96, 0.05):
        accepted = (conf >= t) & (pred != OTHER)
        cov = float(accepted.mean())
        acc = float((pred[accepted] == y[accepted]).mean()) if accepted.any() else float("nan")
        curve.append({"threshold": round(float(t), 2), "coverage": round(cov, 3), "accuracy": round(acc, 3)})
    return curve


def evaluate(model, rows, t, threshold):
    if not rows:
        return None
    y = np.array([CLASSES.index(r["label"]) for r in rows])
    probs = softmax(model.logits(rows), t)
    pred = probs.argmax(1)
    classes, macro_f1 = per_class(pred, y)
    coffee = y != OTHER
    out = {
        "n": len(rows),
        "accuracy": round(float((pred == y).mean()), 3),
        "macro_f1": round(macro_f1, 3),
        "ece": round(ece(probs, y), 3),
        "per_class": classes,
        "confusion": np.bincount(y * len(CLASSES) + pred, minlength=len(CLASSES) ** 2).reshape(len(CLASSES), -1).tolist(),
    }
    if coffee.any():
        pc, yc = probs[coffee], y[coffee]
        accepted = (pc.max(1) >= threshold) & (pc.argmax(1) != OTHER)
        out["at_threshold"] = {
            "threshold": threshold,
            "coverage": round(float(accepted.mean()), 3),
            "accuracy_on_accepted": round(float((pc.argmax(1)[accepted] == yc[accepted]).mean()), 3) if accepted.any() else None,
        }
        out["coverage_curve"] = coverage_curve(pc, yc)
    if (~coffee).any():
        po = probs[~coffee]
        # A non-coffee photo is rejected if it is called "other" or falls below the acceptance threshold.
        rejected = (po.argmax(1) == OTHER) | (po.max(1) < threshold)
        out["non_coffee_rejected"] = round(float(rejected.mean()), 3)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="leaf-v1")
    ap.add_argument("--threshold", type=float, default=0.70)
    ap.add_argument("--quant", choices=["dynamic", "full"], default="dynamic")
    args = ap.parse_args()

    out = RUNS / args.run
    rows = list(csv.DictReader(open(MANIFEST)))
    random.Random(SEED).shuffle(rows)
    model = keras.models.load_model(out / "model.keras")

    tflite = out / "leaf.tflite"
    export(model, rows_where(rows, split="train")[:300], tflite, args.quant)
    lite = LiteModel(tflite)
    print(f"{args.quant} model:", round(tflite.stat().st_size / 1e6, 2), "MB")

    val = rows_where(rows, split="val")
    t = fit_temperature(lite.logits(val), np.array([CLASSES.index(r["label"]) for r in val]))
    print("temperature:", t)

    report = {
        "run": args.run,
        "date": date.today().isoformat(),
        "model_bytes": tflite.stat().st_size,
        "quantization": args.quant,
        "temperature": t,
        "sets": {},
    }
    for name, sel in {
        "val": dict(split="val"),
        "test_jmuben": dict(split="test", source="jmuben"),
        "test_bracol": dict(split="test", source="bracol"),
        "test_plantdoc_other": dict(split="test", source="plantdoc"),
    }.items():
        res = evaluate(lite, rows_where(rows, **sel), t, args.threshold)
        if res:
            report["sets"][name] = res
            print(name, {k: res[k] for k in ("n", "accuracy", "macro_f1", "ece")}, res.get("at_threshold", ""), res.get("non_coffee_rejected", ""))
    json.dump(report, open(out / "eval.json", "w"), indent=2)

    CONTENT_MODEL.mkdir(parents=True, exist_ok=True)
    shutil.copy(tflite, CONTENT_MODEL / "leaf.tflite")
    json.dump(
        {
            "version": args.run,
            "labels": CLASSES,
            "input_size": 224,
            "input_range": [0, 255],
            "output": "logits",
            "temperature": t,
            "accept_threshold": args.threshold,
            "trained_on": ["JMuBEN (CC BY 4.0)", "JMuBEN2 (CC BY 4.0)", "BRACOL (CC BY 4.0)", "PlantDoc (CC BY 4.0)"],
        },
        open(CONTENT_MODEL / "leaf_model.json", "w"),
        indent=2,
    )
    print("copied to", CONTENT_MODEL)


if __name__ == "__main__":
    main()
