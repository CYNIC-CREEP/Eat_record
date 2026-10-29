"""Deterministic MNN conversion, bypassing the optional converter telemetry wrapper."""
import argparse
import hashlib
import json
import pathlib
import sys
import urllib.request

sys.dont_write_bytecode = True

parser = argparse.ArgumentParser()
parser.add_argument("--root", required=True)
args = parser.parse_args()
root = pathlib.Path(args.root)
cache = root / "build/deps/vision"
cache.mkdir(parents=True, exist_ok=True)
sys.path.insert(0, str(cache / "python"))
import _tools

manifest = json.loads((root / "assets/models/model_manifest.json").read_text(encoding="utf-8"))
def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()

for kind in ("full", "small"):
    model = manifest[kind]
    output = cache / model["file"]
    if output.exists() and digest(output) == model["sha256"]:
        continue
    source = cache / pathlib.PurePosixPath(model["source_url"]).name
    if not source.exists() or digest(source) != model["source_sha256"]:
        temporary = source.with_suffix(".download")
        urllib.request.urlretrieve(model["source_url"], temporary)
        if digest(temporary) != model["source_sha256"]:
            raise RuntimeError("Model source SHA-256 mismatch")
        temporary.replace(source)
    converted_source = source
    if model.get("source_transform") == "dis-fixed-512-primary-v1":
        from food_model_graph import mobile_isnet
        converted_source = cache / "isnet-general-use-512-primary.onnx"
        mobile_isnet(source, converted_source)
        if digest(converted_source) != model["transformed_sha256"]:
            raise RuntimeError("Adapted graph hash differs; verify graph tooling before packaging")
    _tools.mnnconvert(["MNNConvert", "-f", "ONNX", "--modelFile", str(converted_source),
                       "--MNNModel", str(output), "--fp16", "--bizCode", "EatRecord"])
    if digest(output) != model["sha256"]:
        raise RuntimeError("Converted model hash differs; verify converter version before packaging")
print("Food models verified (full + small, FP16)")
