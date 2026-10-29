"""Pinned public source -> single-image ONNX -> verified MNN; cached outside source assets."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
CACHE=ROOT/"build/deps/edgetam"
VISION=ROOT/"build/deps/vision"
MODEL=json.loads((ROOT/"assets/models/model_manifest.json").read_text(encoding="utf-8"))["refiner"]
def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream,"sha256").hexdigest()
def valid(path, sha):
    return path.is_file() and digest(path)==sha
def main():
    CACHE.mkdir(parents=True,exist_ok=True)
    if all(valid(VISION/MODEL[k]["file"],MODEL[k]["sha256"]) for k in ("encoder","decoder")):
        print("EdgeTAM encoder + decoder SHA-256 verified (cached)")
        return
    exported=CACHE/"export"
    if not all(valid(exported/(k+".onnx"),MODEL[k]["onnx_sha256"]) for k in ("encoder","decoder")):
        source=CACHE/"source"
        if not (source/".git").is_dir():
            subprocess.run(["git","-c","http.version=HTTP/1.1","clone","--depth","1","--filter=blob:none","--sparse",MODEL["source_repository"],str(source)],check=True)
        subprocess.run(["git","-C",str(source),"sparse-checkout","set","sam2"],check=True)
        revision=subprocess.check_output(["git","-C",str(source),"rev-parse","HEAD"],text=True).strip()
        if revision!=MODEL["source_commit"]:
            subprocess.run(["git","-C",str(source),"fetch","--depth","1","origin",MODEL["source_commit"]],check=True)
            subprocess.run(["git","-C",str(source),"checkout","--detach",MODEL["source_commit"]],check=True)
        checkpoint=CACHE/"edgetam.pt"
        if not valid(checkpoint,MODEL["checkpoint_sha256"]):
            partial=checkpoint.with_suffix(".download")
            # Short bounded ranges avoid restarting a large download after a TLS reset.
            with partial.open("wb") as target:
                for start in range(0,MODEL["checkpoint_bytes"],8*1024*1024):
                    end=min(start+8*1024*1024,MODEL["checkpoint_bytes"])-1
                    request=urllib.request.Request(MODEL["checkpoint_url"],headers={"Range":f"bytes={start}-{end}"})
                    with urllib.request.urlopen(request,timeout=120) as response:
                        if response.status!=206:
                            raise RuntimeError("Model host must honor byte ranges")
                        part=response.read(end-start+2)
                    if len(part)!=end-start+1:
                        raise RuntimeError("Incomplete model range")
                    target.write(part)
            if not valid(partial,MODEL["checkpoint_sha256"]):
                raise RuntimeError("EdgeTAM checkpoint hash mismatch")
            partial.replace(checkpoint)
        python=CACHE/"python"
        if not (python/"torch/__init__.py").is_file():
            subprocess.run([sys.executable,"-m","pip","install","--target",str(python),"--index-url","https://download.pytorch.org/whl/cpu","torch==2.5.1+cpu","torchvision==0.20.1+cpu","--disable-pip-version-check"],check=True)
        if not (python/"timm/__init__.py").is_file():
            subprocess.run([sys.executable,"-m","pip","install","--no-deps","--target",str(python),"hydra-core==1.3.2","omegaconf==2.3.0","antlr4-python3-runtime==4.9.3","PyYAML==6.0.3","iopath==0.1.10","portalocker==3.2.0","termcolor==3.1.0","timm==1.0.15","huggingface-hub==0.36.0","safetensors==0.6.2","packaging==25.0","tqdm==4.67.1","--disable-pip-version-check"],check=True)
        subprocess.run([sys.executable,str(ROOT/"scripts/export-edgetam.py")],check=True)
    sys.path.insert(0,str(VISION/"python"))
    import _tools
    for key in ("encoder","decoder"):
        config=MODEL[key]; onnx=exported/(key+".onnx"); output=VISION/config["file"]
        if not valid(onnx,config["onnx_sha256"]):
            raise RuntimeError("Exported EdgeTAM graph hash changed; do not silently package")
        cached=exported/(key+".mnn")
        if valid(cached,config["sha256"]):
            shutil.copyfile(cached,output)
        else:
            _tools.mnnconvert(["MNNConvert","-f","ONNX","--modelFile",str(onnx),"--MNNModel",str(output),"--fp16","--bizCode","EatRecord"])
        if not valid(output,config["sha256"]):
            raise RuntimeError("Converted EdgeTAM model hash changed")
    print("EdgeTAM single-image encoder + decoder SHA-256 verified")
if __name__=="__main__":
    main()
