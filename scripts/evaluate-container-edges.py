"""Model-guided closed image contours; no fitted ellipses or synthetic plate borders."""
import argparse
import json
import math
import pathlib
import sys

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/deps/plate-eval/python"))
sys.path.insert(0, str(ROOT / "build/deps/vision/python"))
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageOps

parser = argparse.ArgumentParser()
parser.add_argument("--image", required=True)
parser.add_argument("--alpha", required=True)
parser.add_argument("--output", default=str(ROOT / "build/beta8-container-eval"))
args = parser.parse_args()
out = pathlib.Path(args.output)
out.mkdir(parents=True, exist_ok=True)
photo = ImageOps.exif_transpose(Image.open(args.image)).convert("RGB")
rgb = np.asarray(photo.resize((512, 512), Image.Resampling.BILINEAR))
gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
gray = cv2.GaussianBlur(gray, (5, 5), 0)
alpha = np.asarray(Image.open(args.alpha).convert("L").resize((512, 512), Image.Resampling.BILINEAR))
foreground = (alpha >= 128).astype(np.uint8)
foreground_area = int(foreground.sum())
reports = []
selected = None
best_score = -1e9
for low in (12, 20, 32, 48):
    edges = cv2.Canny(gray, low, low * 3)
    supported = cv2.dilate(edges, np.ones((5, 5), np.uint8))
    closed = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(closed, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    for index, contour in enumerate(contours):
        area = cv2.contourArea(contour)
        ratio = area / max(1, foreground_area)
        if area < 512 * 512 * .07 or area > 512 * 512 * .88 or ratio < 1.15 or ratio > 2.35:
            continue
        x, y, w, h = cv2.boundingRect(contour)
        if x < 3 or y < 3 or x + w > 509 or y + h > 509:
            continue
        hull_area = cv2.contourArea(cv2.convexHull(contour))
        solidity = area / max(1, hull_area)
        perimeter = cv2.arcLength(contour, True)
        compactness = 4 * math.pi * area / max(1, perimeter * perimeter)
        if solidity < .85 or compactness < .35:
            continue
        mask = np.zeros_like(gray)
        cv2.drawContours(mask, [contour], -1, 255, cv2.FILLED)
        coverage = float(((mask > 0) & (foreground > 0)).sum()) / max(1, foreground_area)
        if coverage < .96:
            continue
        boundary = np.zeros_like(gray)
        cv2.drawContours(boundary, [contour], -1, 255, 1)
        support = float(((boundary > 0) & (supported > 0)).sum()) / max(1, (boundary > 0).sum())
        if support < .92:
            continue
        # Only rescue a neutral, light container rim: not an arbitrary surrounding table polygon.
        added = (mask > 0) & (alpha < 96)
        added_rgb = rgb[added].astype(np.float32)
        if len(added_rgb) < foreground_area * .12:
            continue
        mean_brightness = float(added_rgb.mean())
        neutral_fraction = float(((added_rgb.max(axis=1) - added_rgb.min(axis=1) < 45) &
                                  (added_rgb.mean(axis=1) > 150)).mean())
        if neutral_fraction < .65 or mean_brightness < 155:
            continue
        score = support * 2 + coverage + solidity - abs(ratio - 1.55) * .3
        report = {"low": low, "contour": index, "area_ratio": ratio, "coverage": coverage,
                  "support": support, "solidity": solidity, "compactness": compactness,
                  "neutral_fraction": neutral_fraction, "mean_added_rgb": mean_brightness, "score": score}
        reports.append(report)
        if score > best_score:
            selected = mask
            best_score = score
            cv2.imwrite(str(out / "selected-edge-support.png"), supported)
            cv2.imwrite(str(out / "selected-edges.png"), edges)
if selected is not None:
    # Keep the food/cutlery predicted outside the container; soften only the measured boundary.
    selected = cv2.GaussianBlur(selected, (3, 3), .6)
    combined = np.maximum(alpha, selected)
else:
    combined = alpha
cv2.imwrite(str(out / "container-alpha.png"), combined)
thumb = photo.copy()
thumb.thumbnail((768, 768))
final_alpha = Image.fromarray(combined).resize(thumb.size, Image.Resampling.BILINEAR)
cutout = thumb.convert("RGBA")
cutout.putalpha(final_alpha)
cutout.save(out / "container-cutout.png")
canvas = Image.new("RGB", (thumb.width * 3, thumb.height + 28), "#eee9e2")
canvas.paste(thumb, (0, 28))
old = thumb.convert("RGBA")
old.putalpha(Image.fromarray(alpha).resize(thumb.size, Image.Resampling.BILINEAR))
canvas.paste(old, (thumb.width, 28), old)
canvas.paste(cutout, (thumb.width * 2, 28), cutout)
draw = ImageDraw.Draw(canvas)
for i, title in enumerate(("Original", "Existing model", "Model + measured closed edges")):
    draw.text((8 + thumb.width * i, 6), title, fill="#302820")
canvas.save(out / "container-comparison.jpg", quality=93)
(out / "container-report.json").write_text(json.dumps({"applied": selected is not None,
    "candidates": sorted(reports, key=lambda value: -value["score"]),
    "scope": "Image-specific local diagnostic, not a human-labelled benchmark; no ellipse fitting."}, indent=2), encoding="utf-8")
print(json.dumps({"applied": selected is not None, "candidates": len(reports), "best": max(reports, key=lambda value: value["score"]) if reports else None}), flush=True)
