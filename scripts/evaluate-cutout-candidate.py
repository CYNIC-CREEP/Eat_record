"""Local candidate A/B only. Does not upload images or alter packaged models."""
import argparse
import hashlib
import json
import pathlib
import sys
import time

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/deps/vision/python"))
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageOps

parser = argparse.ArgumentParser()
parser.add_argument("--model", required=True)
parser.add_argument("--image", action="append", required=True)
parser.add_argument("--output", default=str(ROOT / "build/beta8-model-eval"))
args = parser.parse_args()
output = pathlib.Path(args.output)
output.mkdir(parents=True, exist_ok=True)
model = pathlib.Path(args.model)
with model.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
options = ort.SessionOptions()
options.intra_op_num_threads = 2
options.inter_op_num_threads = 1
options.enable_cpu_mem_arena = False
session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
input_info = session.get_inputs()[0]
shape = input_info.shape
side = int(shape[-1]) if isinstance(shape[-1], int) else 1024
reports = []
print(json.dumps({"sha256": digest, "inputs": [(v.name, v.shape) for v in session.get_inputs()],
                  "outputs": [(v.name, v.shape) for v in session.get_outputs()]}), flush=True)
for index, source in enumerate(args.image):
    photo = ImageOps.exif_transpose(Image.open(source)).convert("RGB")
    scaled = np.asarray(photo.resize((side, side), Image.Resampling.BILINEAR), dtype=np.float32) / 255.0
    data = ((scaled - np.asarray([.485, .456, .406], dtype=np.float32)) /
            np.asarray([.229, .224, .225], dtype=np.float32))
    data = np.ascontiguousarray(data.transpose(2, 0, 1)[None])
    start = time.perf_counter()
    results = session.run(None, {input_info.name: data})
    elapsed = time.perf_counter() - start
    # The exported graph may expose auxiliary heads. Compare the highest-resolution single mask.
    candidates = [(i, v) for i, v in enumerate(results) if len(v.shape) == 4 and v.shape[1] == 1]
    if not candidates:
        raise RuntimeError("Candidate graph does not expose an NCHW single-channel mask")
    chosen, logits = max(candidates, key=lambda entry: entry[1].shape[-1] * entry[1].shape[-2])
    raw = np.asarray(logits[0, 0], dtype=np.float32)
    if not np.isfinite(raw).all():
        raise RuntimeError("Non-finite candidate output")
    low, high = float(raw.min()), float(raw.max())
    uses_sigmoid = low < 0 or high > 1
    alpha = 1.0 / (1.0 + np.exp(-np.clip(raw, -80, 80))) if uses_sigmoid else raw
    stem = f"candidate-{index}"
    Image.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8)).save(output / f"{stem}-alpha.png")
    np.save(output / f"{stem}-alpha.npy", alpha)
    thumb = photo.copy()
    thumb.thumbnail((768, 768))
    resized_alpha = Image.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8)).resize(thumb.size, Image.Resampling.BILINEAR)
    cutout = thumb.convert("RGBA")
    cutout.putalpha(resized_alpha)
    cutout.save(output / f"{stem}-cutout.png")
    canvas = Image.new("RGB", (thumb.width * 2, thumb.height + 28), "#eee9e2")
    canvas.paste(thumb, (0, 28))
    canvas.paste(cutout, (thumb.width, 28), cutout)
    draw = ImageDraw.Draw(canvas)
    draw.text((8, 6), "Original", fill="#302820")
    draw.text((thumb.width + 8, 6), "Candidate (local inference)", fill="#302820")
    canvas.save(output / f"{stem}-comparison.jpg", quality=92)
    # These point samples are diagnostics, not a human ground-truth accuracy score.
    samples = {}
    for name, x, y in [("top", .5, .19), ("left", .13, .49), ("bottom", .5, .83), ("right", .92, .47), ("center", .5, .5)]:
        px, py = int(x * alpha.shape[1]), int(y * alpha.shape[0])
        samples[name] = float(alpha[max(0, py - 2):py + 3, max(0, px - 2):px + 3].mean())
    report = {"index": index, "source_name": pathlib.Path(source).name, "input_side": side,
              "desktop_seconds": round(elapsed, 3), "output_name": session.get_outputs()[chosen].name,
              "raw_range": [low, high], "sigmoid_applied": uses_sigmoid, "point_samples_not_ground_truth": samples,
              "model_sha256": digest}
    reports.append(report)
    print(json.dumps(report), flush=True)
(output / "candidate-report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
