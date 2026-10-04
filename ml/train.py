"""Train the leaf classifier: MobileNetV3-Small from ImageNet weights, two phases.

Phase 1 trains only the new head on frozen features; phase 2 fine-tunes the top of the backbone.
Outputs logits (no softmax) so temperature scaling can be fitted after int8 export.

    .venv/bin/python train.py --run leaf-v1
"""
import argparse
import csv
import json

import numpy as np
import tensorflow as tf
import keras

from common import CLASSES, MANIFEST, RUNS, SEED, SIZE


def read_manifest():
    with open(MANIFEST) as f:
        return list(csv.DictReader(f))


def decode(path):
    img = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
    img.set_shape([None, None, 3])
    return img


def train_view(path, label):
    """Field-style augmentation: random zoomed crops, any rotation, light, colour and blur changes."""
    img = decode(path)
    shape = tf.shape(img)
    side = tf.cast(tf.cast(tf.minimum(shape[0], shape[1]), tf.float32) * tf.random.uniform([], 0.55, 1.0), tf.int32)
    img = tf.image.random_crop(img, tf.stack([side, side, 3]))
    img = tf.image.resize(img, [SIZE, SIZE])
    img = tf.image.rot90(img, tf.random.uniform([], 0, 4, dtype=tf.int32))
    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_brightness(img, 40.0)
    img = tf.image.random_contrast(img, 0.75, 1.25)
    img = tf.image.random_saturation(img, 0.75, 1.25)
    # Occasional soft blur, like a slightly shaky hand.
    blur = tf.nn.avg_pool2d(img[None], 3, 1, "SAME")[0]
    img = tf.where(tf.random.uniform([]) < 0.2, blur, img)
    return tf.clip_by_value(img, 0.0, 255.0), label


def eval_view(path, label):
    """Center square + resize, exactly what the app does."""
    img = decode(path)
    shape = tf.shape(img)
    side = tf.minimum(shape[0], shape[1])
    img = tf.image.crop_to_bounding_box(img, (shape[0] - side) // 2, (shape[1] - side) // 2, side, side)
    return tf.image.resize(img, [SIZE, SIZE]), label


def dataset(rows, training, batch=64):
    paths = [r["path"] for r in rows]
    labels = [CLASSES.index(r["label"]) for r in rows]
    ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if training:
        ds = ds.shuffle(len(rows), seed=SEED, reshuffle_each_iteration=True)
    ds = ds.map(train_view if training else eval_view, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(batch).prefetch(tf.data.AUTOTUNE)


def build_model():
    base = keras.applications.MobileNetV3Small(
        input_shape=(SIZE, SIZE, 3), include_top=False, weights="imagenet", pooling="avg", include_preprocessing=True,
    )
    inputs = keras.Input((SIZE, SIZE, 3))
    x = base(inputs, training=False)
    x = keras.layers.Dropout(0.25)(x)
    logits = keras.layers.Dense(len(CLASSES), name="logits")(x)
    return keras.Model(inputs, logits), base


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="leaf-v1")
    ap.add_argument("--head-epochs", type=int, default=4)
    ap.add_argument("--tune-epochs", type=int, default=8)
    args = ap.parse_args()

    keras.utils.set_random_seed(SEED)
    rows = read_manifest()
    train = [r for r in rows if r["split"] == "train"]
    val = [r for r in rows if r["split"] == "val"]
    counts = np.bincount([CLASSES.index(r["label"]) for r in train], minlength=len(CLASSES))
    class_weight = {i: float(len(train) / (len(CLASSES) * c)) for i, c in enumerate(counts) if c}
    print("train per class:", dict(zip(CLASSES, counts.tolist())), "val:", len(val))

    model, base = build_model()
    loss = keras.losses.SparseCategoricalCrossentropy(from_logits=True)
    out = RUNS / args.run
    out.mkdir(parents=True, exist_ok=True)
    callbacks = [
        keras.callbacks.EarlyStopping(monitor="val_loss", patience=3, restore_best_weights=True),
        keras.callbacks.CSVLogger(str(out / "history.csv"), append=True),
    ]

    base.trainable = False
    model.compile(keras.optimizers.Adam(1e-3), loss, metrics=["accuracy"])
    model.fit(dataset(train, True), validation_data=dataset(val, False), epochs=args.head_epochs, class_weight=class_weight, callbacks=callbacks)

    # Fine-tune the last blocks; BatchNorm stays frozen so small batches do not wreck its statistics.
    base.trainable = True
    for layer in base.layers[:-60]:
        layer.trainable = False
    for layer in base.layers:
        if isinstance(layer, keras.layers.BatchNormalization):
            layer.trainable = False
    model.compile(keras.optimizers.Adam(1e-4), loss, metrics=["accuracy"])
    model.fit(dataset(train, True), validation_data=dataset(val, False), epochs=args.tune_epochs, class_weight=class_weight, callbacks=callbacks)

    model.save(out / "model.keras")
    json.dump({"classes": CLASSES, "train_counts": counts.tolist()}, open(out / "train_info.json", "w"), indent=2)
    print("saved", out / "model.keras")


if __name__ == "__main__":
    main()
