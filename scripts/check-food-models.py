"""Conversion parity and image smoke check; not a substitute for Android performance/accuracy QA."""
import argparse
import hashlib
import json
import pathlib
import sys

sys.dont_write_bytecode = True
import time

parser = argparse.ArgumentParser()
parser.add_argument("--image", required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
cache = root / "build/deps/vision"
sys.path.insert(0, str(cache / "python"))
import MNN
import numpy as np
import onnxruntime as ort
import onnx
from PIL import Image, ImageOps

output = root / "build/vision-qa"
output.mkdir(parents=True, exist_ok=True)
image = ImageOps.exif_transpose(Image.open(args.image)).convert("RGB")
reports = []
manifest = json.loads((root / "assets/models/model_manifest.json").read_text(encoding="utf-8"))
for kind in ("small", "full"):
    model = manifest[kind]
    name, side = pathlib.Path(model["file"]).stem, model["input_size"]
    model_file = cache / model["file"]
    with model_file.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == model["sha256"]
    pixels = np.asarray(image.resize((side, side), Image.Resampling.BILINEAR), dtype=np.float32)
    data = (pixels / max(float(pixels.max()), 1) - np.array(model["normalization_mean"])) / np.array(model["normalization_std"])
    data = np.ascontiguousarray(data.transpose(2, 0, 1)[None], dtype=np.float32)
    engine = MNN.Interpreter(str(model_file))
    engine.setSessionMode(1)  # Session_Release, matching the Android runtime.
    engine.setSessionHint(3, 0)  # WINOGRAD_MEMORY_LEVEL=0: bound workspace memory.
    session = engine.createSession({"backend": "CPU", "numThread": 2, "precision": "low"})
    tensor = engine.getSessionInput(session)
    engine.resizeTensor(tensor, (1, 3, side, side)); engine.resizeSession(session)
    tensor.copyFrom(MNN.Tensor(data.shape, MNN.Halide_Type_Float, data, MNN.Tensor_DimensionType_Caffe))
    start = time.perf_counter(); engine.runSession(session); elapsed = time.perf_counter() - start
    result = engine.getSessionOutput(session, model["output_name"])
    host = MNN.Tensor(result.getShape(), MNN.Halide_Type_Float, np.zeros(result.getShape(), dtype=np.float32), MNN.Tensor_DimensionType_Caffe)
    result.copyToHostTensor(host)
    mask = np.array(host.getData(), dtype=np.float32).reshape(result.getShape())[0, 0]
    mask = (mask - mask.min()) / max(float(mask.max() - mask.min()), 1e-6)
    options = ort.SessionOptions(); options.intra_op_num_threads = 2
    source = cache / pathlib.PurePosixPath(model["source_url"]).name
    with source.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == model["source_sha256"]
    if model.get("source_transform") == "dis-fixed-512-primary-v1":
        from food_model_graph import mobile_isnet
        transformed = cache / "isnet-general-use-512-primary.onnx"
        mobile_isnet(source, transformed)
        with transformed.open("rb") as stream:
            assert hashlib.file_digest(stream, "sha256").hexdigest() == model["transformed_sha256"]
        source = transformed
    graph = onnx.load(str(source))
    graph.graph.input[0].type.tensor_type.shape.dim[2].dim_value = side
    graph.graph.input[0].type.tensor_type.shape.dim[3].dim_value = side
    baseline = ort.InferenceSession(graph.SerializeToString(), sess_options=options, providers=["CPUExecutionProvider"])
    reference = baseline.run(None, {baseline.get_inputs()[0].name: data})[0][0, 0]
    reference = (reference - reference.min()) / max(float(reference.max() - reference.min()), 1e-6)
    difference = float(np.mean(np.abs(mask - reference)))
    intersection = np.logical_and(mask > .5, reference > .5).sum()
    union = np.logical_or(mask > .5, reference > .5).sum()
    iou = float(intersection / max(union, 1))
    assert np.isfinite(mask).all() and mask.max() > .9
    assert difference < .035 and iou > .95, (name, difference, iou)
    sticker = image.copy(); sticker.thumbnail((1024, 1024))
    # Match the Java opaque-interior / antialiased-edge remap.
    alpha_mask = np.clip((mask - .05) / .8, 0, 1)
    alpha_mask = alpha_mask * alpha_mask * (3 - 2 * alpha_mask)
    alpha = Image.fromarray((alpha_mask * 255).astype(np.uint8)).resize(sticker.size, Image.Resampling.BILINEAR)
    sticker.putalpha(alpha); sticker.save(output / f"{name}-smoke.png")
    backdrop = Image.new("RGB", sticker.size, "#f1ede7")
    backdrop.paste(sticker, mask=alpha)
    backdrop.save(output / f"{name}-preview.jpg")
    report = {"model": name, "size": side, "desktop_seconds": round(elapsed, 3), "mean_alpha_error": difference, "conversion_mask_iou": iou}
    reports.append(report); print(json.dumps(report), flush=True)
    del baseline, engine, session
(output / "conversion-parity.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
