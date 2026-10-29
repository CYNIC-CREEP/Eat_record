"""Offline EdgeTAM evaluation. Prompts come from the existing alpha, not plate geometry."""
import argparse
import json
import pathlib
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path[:0] = [str(ROOT / "build/deps/edgetam/python"), str(ROOT / "build/deps/edgetam/source")]
import numpy as np
import torch
import timm.models
from PIL import Image, ImageDraw, ImageOps

# The complete checkpoint includes the backbone. Never fetch a second pretrained model.
original_create = timm.models.create_model
def offline_create(*args, **kwargs):
    kwargs["pretrained"] = False
    return original_create(*args, **kwargs)
timm.models.create_model = offline_create
from sam2.build_sam import build_sam2
from sam2.sam2_image_predictor import SAM2ImagePredictor

parser = argparse.ArgumentParser()
parser.add_argument("--image", action="append", required=True)
parser.add_argument("--alpha", action="append", required=True)
parser.add_argument("--output", default=str(ROOT / "build/beta8-edgetam-eval"))
args = parser.parse_args()
assert len(args.image) == len(args.alpha)
output = pathlib.Path(args.output)
output.mkdir(parents=True, exist_ok=True)
torch.set_num_threads(2)
model = build_sam2("configs/edgetam.yaml", str(ROOT / "build/deps/edgetam/edgetam.pt"),
                   device="cpu", apply_postprocessing=False)
predictor = SAM2ImagePredictor(model)
reports = []
for image_index, (source, alpha_path) in enumerate(zip(args.image, args.alpha)):
    photo = ImageOps.exif_transpose(Image.open(source)).convert("RGB")
    photo.thumbnail((1024, 1024))
    w, h = photo.size
    alpha_image = Image.open(alpha_path)
    if alpha_image.mode == "RGBA":
        alpha_image = alpha_image.getchannel("A")
    alpha = np.asarray(alpha_image.convert("L").resize((w, h), Image.Resampling.BILINEAR)) / 255.0
    foreground = alpha > .5
    yy, xx = np.where(foreground)
    bounds = np.array([xx.min(), yy.min(), xx.max(), yy.max()], dtype=np.float32)
    center = np.array([xx.mean(), yy.mean()], dtype=np.float32)
    started = time.perf_counter()
    predictor.set_image(np.asarray(photo))
    encode_seconds = time.perf_counter() - started
    print(json.dumps({"image": image_index, "encoder_seconds": encode_seconds}), flush=True)
    results = []
    tiles = []
    for margin in [0, .08, .16, .24]:
        box = bounds + np.array([-w, -h, w, h], dtype=np.float32) * margin
        box[[0, 2]] = np.clip(box[[0, 2]], 0, w - 1)
        box[[1, 3]] = np.clip(box[[1, 3]], 0, h - 1)
        for use_points in [False, True]:
            points = np.asarray([center, [2, 2], [w-3, 2], [2, h-3], [w-3, h-3]], dtype=np.float32)
            started = time.perf_counter()
            masks, scores, _ = predictor.predict(
                box=box, point_coords=points if use_points else None,
                point_labels=np.array([1, 0, 0, 0, 0], dtype=np.int32) if use_points else None,
                multimask_output=True, return_logits=True)
            elapsed = time.perf_counter() - started
            for candidate_index, (mask, score) in enumerate(zip(masks, scores)):
                binary = mask > 0
                coverage = float((binary & foreground).sum() / foreground.sum())
                ratio = float(binary.sum() / foreground.sum())
                name = f"{image_index}-m{margin}-p{int(use_points)}-c{candidate_index}"
                Image.fromarray(binary.astype(np.uint8)*255).save(output / f"{name}-alpha.png")
                rgba = photo.convert("RGBA")
                rgba.putalpha(Image.fromarray(binary.astype(np.uint8)*255))
                rgba.save(output / f"{name}.png")
                tile = Image.new("RGB", (300, 430), "#eee9e2")
                rgba.thumbnail((300, 390))
                tile.paste(rgba, ((300-rgba.width)//2, 40), rgba)
                ImageDraw.Draw(tile).text((4, 4), f"{name}\nscore {score:.3f} coverage {coverage:.3f} area {ratio:.2f}", fill="black")
                tiles.append(tile)
                results.append({"name": name, "score": float(score), "coverage": coverage,
                                "area_ratio": ratio, "decoder_seconds": elapsed,
                                "image_fraction": float(binary.mean())})
    sheet = Image.new("RGB", (300*6, 430*4), "white")
    for i, tile in enumerate(tiles):
        sheet.paste(tile, ((i%6)*300, (i//6)*430))
    sheet.save(output / f"image-{image_index}-candidates.jpg", quality=90)
    reports.append({"image": image_index, "encoder_seconds": encode_seconds, "results": results})
    (output / "report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
    print(json.dumps(reports[-1]), flush=True)
