#!/usr/bin/env python3
"""Converts the GhostFaceNets Keras weights into the TFLite model bundled in core/data (Fast & Rich round, Phase 2).

Source: https://github.com/HamadYA/GhostFaceNets (MIT), release v1.2, GhostFaceNet_W1.3_S1_ArcFace.h5
        (GhostFaceNetV1, width 1.3, stride 1, ArcFace loss, trained on MS1MV3; 112×112 RGB → 512-d embedding).
Output: ghostfacenet_v1_w1.3_s1_int8.tflite (int8 dynamic-range quantisation, float32 in/out, ~4.5 MB).

    python3 -m venv venv && venv/bin/pip install "tensorflow-cpu==2.21.*" "tf_keras==2.21.*" h5py numpy
    venv/bin/python scripts/faces/convert_ghostfacenet.py GhostFaceNet_W1.3_S1_ArcFace.h5 core/data/src/main/assets/faces/ghostfacenet_v1_w1.3_s1_int8.tflite

Why the detours: the weights were saved by Keras 2.4 with mixed_float16 layer policies, which Keras 3 cannot load and
TFLite's builtin Conv2D cannot run; the architecture (standard layers only) is rebuilt with float32 policies from the
saved config, the weights are loaded into it, variables are frozen into constants at batch size 1, and the frozen graph
is converted. The app feeds (pixel - 127.5) / 128 as NHWC float32 and L2-normalises the 512 output values.
"""
import os, sys, json
os.environ["TF_USE_LEGACY_KERAS"] = "1"
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"
import h5py
import numpy as np
import tensorflow as tf
import tf_keras
from tensorflow.python.framework.convert_to_constants import convert_variables_to_constants_v2

src, dst = sys.argv[1], sys.argv[2]
with h5py.File(src, "r") as f:
    cfg = f.attrs["model_config"]
    cfg = cfg if isinstance(cfg, str) else cfg.decode()
cfg32 = cfg.replace('{"class_name": "Policy", "config": {"name": "mixed_float16"}}', '"float32"').replace("mixed_float16", "float32")
model = tf_keras.models.model_from_json(cfg32)
model.load_weights(src)
assert tuple(model.input_shape[1:]) == (112, 112, 3) and model.output_shape[-1] == 512, (model.input_shape, model.output_shape)

run = tf.function(lambda x: model(x, training=False))
concrete = run.get_concrete_function(tf.TensorSpec([1, 112, 112, 3], tf.float32, name="input"))
frozen = convert_variables_to_constants_v2(concrete)
converter = tf.lite.TFLiteConverter.from_concrete_functions([frozen], model)
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite = converter.convert()
os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
open(dst, "wb").write(tflite)
print("wrote", dst, len(tflite), "bytes")

# Agreement check against the float Keras model on random inputs in the app's input range.
rng = np.random.default_rng(1)
x = rng.uniform(-1, 1, size=(16, 112, 112, 3)).astype(np.float32)
ref = model.predict(x, verbose=0); ref /= np.linalg.norm(ref, axis=1, keepdims=True)
it = tf.lite.Interpreter(model_path=dst); it.allocate_tensors()
i, o = it.get_input_details()[0], it.get_output_details()[0]
outs = []
for k in range(len(x)):
    it.set_tensor(i["index"], x[k:k + 1]); it.invoke(); outs.append(it.get_tensor(o["index"])[0])
outs = np.array(outs); outs /= np.linalg.norm(outs, axis=1, keepdims=True)
cos = (outs * ref).sum(1)
print(f"cosine vs keras: min {cos.min():.4f} mean {cos.mean():.4f}")
assert cos.min() > 0.95, "quantised model drifted too far from the float model"
