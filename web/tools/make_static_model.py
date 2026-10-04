# Copies leaf.tflite with the batch dimension fixed to 1 (LiteRT.js cannot feed a [-1,224,224,3] input),
# then checks both files give identical logits. Usage (from repo root, needs TensorFlow):
#   ml/.venv/bin/python web/tools/make_static_model.py content/model/leaf.tflite web/content/model/leaf_static.tflite

import sys, numpy as np
from tensorflow.lite.tools import flatbuffer_utils as fu
import tensorflow as tf
src, dst = sys.argv[1], sys.argv[2]
m = fu.read_model(src)
n = 0
for sg in m.subgraphs:
    for t in sg.tensors:
        if t.shapeSignature is not None and len(t.shapeSignature) and t.shapeSignature[0] == -1:
            t.shapeSignature[0] = 1; n += 1
fu.write_model(m, dst)
print("patched tensors:", n)
x = np.full((1,224,224,3), 100, np.float32)
outs = []
for p in (src, dst):
    it = tf.lite.Interpreter(model_path=p); it.allocate_tensors()
    i = it.get_input_details()[0]; o = it.get_output_details()[0]
    it.set_tensor(i['index'], x); it.invoke(); outs.append(it.get_tensor(o['index']))
    print(p, i['shape_signature'], o['shape_signature'])
print("max abs diff:", float(np.abs(outs[0]-outs[1]).max()), outs[0])
