"""Local MNN A/B; executes native/focused_cutout.hpp through a desktop driver.

Build scripts/focused-cutout-policy.cpp with a C++17 compiler, then pass its
executable as --policy-runner. Images stay local; no weights are downloaded.
This is a diagnostic with source-preservation metrics, not segmentation accuracy.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
import time

sys.dont_write_bytecode = True
ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "build/deps/vision/python"), str(ROOT / "build/deps/plate-eval/python")]
import MNN
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageOps


def mnn(path, inputs):
    engine = MNN.Interpreter(str(path))
    engine.setSessionMode(1)
    engine.setSessionHint(3, 0)
    session = engine.createSession({"backend": "CPU", "numThread": 2, "precision": "low"})
    for name, values in inputs.items():
        tensor = engine.getSessionInput(session, name) if name else engine.getSessionInput(session)
        engine.resizeTensor(tensor, values.shape)
    engine.resizeSession(session)
    for name, values in inputs.items():
        tensor = engine.getSessionInput(session, name) if name else engine.getSessionInput(session)
        tensor.copyFrom(MNN.Tensor(values.shape, MNN.Halide_Type_Float,
            np.ascontiguousarray(values, dtype=np.float32), MNN.Tensor_DimensionType_Caffe))
    started = time.perf_counter()
    assert engine.runSession(session) == 0
    result = {}
    for name, tensor in engine.getSessionOutputAll(session).items():
        shape = tensor.getShape()
        host = MNN.Tensor(shape, MNN.Halide_Type_Float, np.zeros(shape, dtype=np.float32), MNN.Tensor_DimensionType_Caffe)
        tensor.copyToHostTensor(host)
        result[name] = np.asarray(host.getData(), dtype=np.float32).reshape(shape)
    return result, time.perf_counter() - started


def resize_float(values, side=512):
    return np.asarray(Image.fromarray(values).resize((side, side), Image.Resampling.BILINEAR))


def native_resize(pixels, width, height, quantize=False):
    """The existing native half-pixel four-tap sampler; no PIL downsample filter."""
    source_height, source_width = pixels.shape[:2]
    xx = np.clip((np.arange(width, dtype=np.float32) + .5) * source_width / width - .5, 0, source_width - 1)
    yy = np.clip((np.arange(height, dtype=np.float32) + .5) * source_height / height - .5, 0, source_height - 1)
    x0, y0 = xx.astype(int), yy.astype(int)
    x1, y1 = np.minimum(x0 + 1, source_width - 1), np.minimum(y0 + 1, source_height - 1)
    dx, dy = (xx - x0)[None, :, None], (yy - y0)[:, None, None]
    top = pixels[y0[:, None], x0[None, :]] * (1 - dx) + pixels[y0[:, None], x1[None, :]] * dx
    bottom = pixels[y1[:, None], x0[None, :]] * (1 - dx) + pixels[y1[:, None], x1[None, :]] * dx
    result = np.asarray(top * (1 - dy) + bottom * dy, np.float32)
    return np.floor(result + .5).astype(np.uint8) if quantize else result


def clean(mask):
    # Same byte remap and four-connected island/hole policy as FoodCutoutEngine.
    mask = np.floor(np.clip(mask, 0, 1) * 255) / 255
    remap = np.clip((mask - .05) / .8, 0, 1)
    remap = np.floor(remap * remap * (3 - 2 * remap) * 255 + .5).astype(np.uint8)
    fg = remap >= 128
    for foreground in (True, False):
        count, labels, stats, _ = cv2.connectedComponentsWithStats((fg == foreground).astype(np.uint8), connectivity=4)
        for index in range(1, count):
            x, y, w, h, area = stats[index]
            edge = x == 0 or y == 0 or x + w == 512 or y + h == 512
            if (foreground and area < 512 * 512 // 2000) or (not foreground and not edge and area < 512 * 512 // 100):
                remap[labels == index] = 0 if foreground else 255
    return remap


def standard_refinement(old, candidates):
    selected, best = None, .7
    count = (old > .5).sum()
    for margin, index, score, alpha in candidates:
        binary = alpha > .5
        coverage = (binary & (old > .5)).sum() / count
        area = binary.sum() / count
        if score >= best and coverage >= .94 and 1.12 <= area <= 2.3 and binary.mean() <= .85:
            selected, best = alpha, score
    if selected is None:
        return old
    solid = cv2.boxFilter((selected > .5).astype(np.float32), -1, (5, 5), normalize=True, borderType=cv2.BORDER_CONSTANT)
    fraction = cv2.boxFilter(np.ones((512, 512), np.float32), -1, (5, 5), normalize=True, borderType=cv2.BORDER_CONSTANT)
    solid = solid * 2 >= fraction
    count, labels, _, _ = cv2.connectedComponentsWithStats(solid.astype(np.uint8), connectivity=4)
    for index in range(1, count):
        if ((labels == index) & (old > .5)).sum() < 16:
            solid[labels == index] = False
    return np.maximum(old, np.where(solid, selected, 0))


def policy(runner, old_path, alpha=None, light_path=None, score=None, result_path=None):
    command = [str(runner), str(old_path)]
    if alpha is not None:
        alpha_path = result_path.with_suffix(".candidate.f32")
        np.asarray(alpha, dtype=np.float32).tofile(alpha_path)
        command.extend([str(alpha_path), str(light_path), str(float(score)), str(result_path)])
    result = subprocess.run(command, capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", action="append", required=True)
    parser.add_argument("--policy-runner", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "build/focused-cutout-eval")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / "assets/models/model_manifest.json").read_text())
    for spec in [manifest["full"], manifest["refiner"]["encoder"], manifest["refiner"]["decoder"]]:
        path = ROOT / "build/deps/vision" / spec["file"]
        with path.open("rb") as stream:
            assert hashlib.file_digest(stream, "sha256").hexdigest() == spec["sha256"]
    reports = []
    for image_index, source in enumerate(args.image):
        out = args.output / str(image_index)
        out.mkdir(exist_ok=True)
        source_photo = ImageOps.exif_transpose(Image.open(source)).convert("RGB")
        ratio = min(1, 1024 / max(source_photo.size))
        width, height = [int(np.floor(value * ratio + .5)) for value in source_photo.size]
        pixels = native_resize(np.asarray(source_photo, np.float32), width, height, quantize=True)
        photo = Image.fromarray(pixels)
        # Match the Java first RGB8 scale, then compare old four-tap native
        # preprocessing with the actual new C++ antialiased sampling function.
        rgba_path, rgb_path = out / "input.rgba", out / "antialiased.rgb"
        rgba_path.write_bytes(photo.convert("RGBA").tobytes())
        subprocess.run([str(args.policy_runner), "--resample", str(rgba_path), str(width), str(height), "512", str(rgb_path)], check=True)
        sampled = np.fromfile(rgb_path, np.uint8).reshape(512, 512, 3).astype(np.float32)
        original_sampled = native_resize(pixels.astype(np.float32), 512, 512)
        if width <= 512 and height <= 512:
            sampled = original_sampled
        primary, baseline_seconds = [], 0
        for model_pixels in [original_sampled, sampled]:
            data = (model_pixels / max(1, model_pixels.max()) - .5).transpose(2, 0, 1)[None]
            values, seconds = mnn(ROOT / "build/deps/vision" / manifest["full"]["file"], {None: data})
            baseline_seconds += seconds
            alpha = values[manifest["full"]["output_name"]][0, 0]
            primary.append(np.floor((alpha - alpha.min()) / (alpha.max() - alpha.min()) * 255) / 255)
        original_old, old = primary
        old_path = out / "baseline.f32"
        old.astype(np.float32).tofile(old_path)
        subject = policy(args.policy_runner, old_path)
        encoder_pixels = native_resize(pixels.astype(np.float32), 1024, 1024)
        detail_pixels = encoder_pixels[::2, ::2]
        light = ((detail_pixels.mean(axis=2) > 170) & (detail_pixels.max(axis=2) - detail_pixels.min(axis=2) < 45)).astype(np.uint8)
        light_path = out / "light.u8"
        light.tofile(light_path)
        data = ((encoder_pixels / 255 - [.485, .456, .406]) / [.229, .224, .225]).transpose(2, 0, 1)[None]
        features, encoder_seconds = mnn(ROOT / "build/deps/vision" / manifest["refiner"]["encoder"]["file"], {"image": data})
        baselines, decoder_seconds = [], 0
        for primary_mask in primary:
            y, x = np.where(primary_mask > .5)
            box = np.asarray([x.min(), y.min(), x.max() + 1, y.max() + 1], np.float32) * 2
            candidates = []
            for margin in [.04, .08]:
                expanded = np.clip(box + [-1024 * margin, -1024 * margin, 1024 * margin, 1024 * margin], 0, 1023).reshape(1, 2, 2)
                result, seconds = mnn(ROOT / "build/deps/vision" / manifest["refiner"]["decoder"]["file"], {**features, "box": expanded})
                decoder_seconds += seconds
                for index, (mask, score) in enumerate(zip(result["masks"][0], result["scores"][0])):
                    alpha = np.clip(resize_float(mask) + .5, 0, 1)
                    candidates.append((margin, index, float(score), alpha))
            baselines.append(standard_refinement(primary_mask, candidates))
        original_baseline, baseline = baselines
        final = baseline
        focused, focused_reports, focus_seconds = False, [], 0
        # Focus is a fallback only. Existing accepted EdgeTAM expansion wins.
        if np.array_equal(baseline, old) and subject["core"]:
            l, t, r, b = subject["bounds"]
            box = np.asarray([l * 2, t * 2, min(1023, (r + 1) * 2), min(1023, (b + 1) * 2)], np.float32).reshape(1, 2, 2)
            result, focus_seconds = mnn(ROOT / "build/deps/vision" / manifest["refiner"]["decoder"]["file"], {**features, "box": box})
            best = .85
            for index, (mask, score) in enumerate(zip(result["masks"][0], result["scores"][0])):
                alpha = np.clip(resize_float(mask) + .5, 0, 1)
                result_path = out / f"focused-{index}.f32"
                report = policy(args.policy_runner, old_path, alpha, light_path, score, result_path)
                report.update(index=index, score=float(score))
                focused_reports.append(report)
                if report["accepted"] and score >= best:
                    final = np.fromfile(result_path, np.float32).reshape(512, 512)
                    best, focused = score, True
        old_alpha, final_alpha = clean(original_baseline), clean(final)
        sheet = Image.new("RGB", (photo.width * 3, photo.height + 35), "#eee9e2")
        sheet.paste(photo, (0, 35))
        for column, (alpha, name) in enumerate([(old_alpha, "Before"), (final_alpha, "After")], 1):
            rgba = photo.convert("RGBA")
            rgba.putalpha(Image.fromarray(alpha).resize(photo.size, Image.Resampling.BILINEAR))
            rgba.save(out / f"{name.lower()}.png")
            sheet.paste(rgba, (photo.width * column, 35), rgba)
        draw = ImageDraw.Draw(sheet)
        for column, label in enumerate(["Original", "Before", "After - focused" if focused else "After - antialiased input"]):
            draw.text((photo.width * column + 10, 8), label, fill="black")
        sheet.save(out / "comparison.jpg", quality=94)
        strong = old > .9
        report = {"source": pathlib.Path(source).name, "focused": focused, "core": subject["core"],
            "before_foreground": int((old_alpha >= 128).sum()), "after_foreground": int((final_alpha >= 128).sum()),
            "new_input_strong_saliency_preserved": float(((final_alpha >= 128) & strong).sum() / max(1, strong.sum())),
            "original_input_strong_saliency_preserved": float(((final_alpha >= 128) & (original_old > .9)).sum() / max(1, (original_old > .9).sum())),
            "changed_pixels": int((old_alpha != final_alpha).sum()), "focus_candidates": focused_reports,
            "desktop_seconds": {"baseline": baseline_seconds, "encoder": encoder_seconds, "normal_decoders": decoder_seconds, "extra_focus_decoder": focus_seconds},
            "scope": "Local FP16 MNN; native half-pixel initial/old input, exact new C++ antialias and focus policy. Not human-labelled accuracy or device timing."}
        reports.append(report)
        print(json.dumps(report), flush=True)
    (args.output / "report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
