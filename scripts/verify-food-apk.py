"""Verify distributable assets, pinned ARM runtimes, and QA isolation (no network)."""
import argparse
import hashlib
import json
import pathlib
import struct
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ABIS = ("arm64-v8a", "armeabi-v7a")
COMMON_LIBS = {"libeatvision.so", "libMNN.so", "libmnncore.so", "libc++_shared.so"}
RETIRED_LIBS = {"libeatoffline.so", "libllm.so", "libMNN_Express.so", "libMNNOpenCV.so", "libMNNAudio.so", "libMNN_CL.so", "libMNN_Vulkan.so"}
# Extracted from the same official MNN 3.6.0 runtime zip pinned by the build script
# (zip SHA-256 3ff2b92e11531f5a9d820b6bf6a8aede3e124e098a3645d8f6a23dcbc862015f).
RUNTIME_HASHES = {
    "arm64-v8a": {
        "libMNN.so": "37320e103908ec9148997528124ad9f917da27cac1746f6d0061038cb7e41f76",
        "libmnncore.so": "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
        "libc++_shared.so": "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970",
    },
    "armeabi-v7a": {
        "libMNN.so": "ac13f602896f5c75f72632361d9f986f053ed58bf97d8c594236ad65193fcc9c",
        "libmnncore.so": "966aa04af136c8c99fe84309bc845e16478cee215a2b717b829635bfc20b1f9c",
        "libc++_shared.so": "4df8b2e81690dd1393a94f65c18b41e5b91494b10b21415823a94ab6bb81018a",
    },
}
SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libjnigraphics.so",
               "libz.so", "libOpenSLES.so", "libEGL.so", "libGLESv2.so", "libGLESv3.so", "libvulkan.so",
               "libmediandk.so", "libaaudio.so"}
RETIRED_FILES = {"llm_config.json", "tokenizer.txt", "llm.mnn", "llm.mnn.weight", "visual.mnn", "visual.mnn.weight"}
RETIRED_ASSETS = {"assets/models/offline_food_manifest.json", "assets/data/offline_nutrition.json",
                  "assets/licenses/Offline-MNN-NOTICE.txt", "assets/licenses/Qwen3.5-Apache-2.0.txt",
                  "assets/licenses/Qwen3.5-NOTICE.txt", "assets/licenses/USDA-FoodData-Central-NOTICE.txt"}
RETIRED_CLASSES = (b"OfflineFoodModel", b"OfflineFoodAnalyzer", b"OfflineFoodResult", b"OfflineNutritionCatalog")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_local_asset(archive, name):
    data = archive.read(name)
    require(data == (ROOT / name).read_bytes(), "Packaged asset differs from source: " + name)
    return data


def elf_needed(data, abi):
    """Read DT_NEEDED without external tooling, also rejecting wrong-ABI binaries."""
    require(data[:4] == b"\x7fELF" and data[5] == 1, "Expected little-endian Android ELF: " + abi)
    is64 = abi == "arm64-v8a"
    require(data[4] == (2 if is64 else 1), "ELF word size differs from ABI: " + abi)
    machine = struct.unpack_from("<H", data, 18)[0]
    require(machine == (183 if is64 else 40), "ELF machine differs from ABI: " + abi)
    phoff = struct.unpack_from("<Q" if is64 else "<I", data, 32 if is64 else 28)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54 if is64 else 42)
    loads, dynamic = [], None
    for index in range(phnum):
        fields = struct.unpack_from("<IIQQQQQQ" if is64 else "<IIIIIIII", data, phoff + index * phentsize)
        kind = fields[0]
        offset, address, length = (fields[2], fields[3], fields[5]) if is64 else (fields[1], fields[2], fields[4])
        if kind == 1:
            loads.append((address, offset, length))
        elif kind == 2:
            dynamic = (offset, length)
    require(dynamic is not None, "Missing ELF dynamic table: " + abi)
    needed, strings, string_size = [], None, None
    width = 16 if is64 else 8
    for offset in range(dynamic[0], dynamic[0] + dynamic[1], width):
        tag, value = struct.unpack_from("<qQ" if is64 else "<iI", data, offset)
        if tag == 0:
            break
        if tag == 1:
            needed.append(value)
        elif tag == 5:
            strings = value
        elif tag == 10:
            string_size = value
    require(strings is not None and string_size is not None, "Missing ELF string table: " + abi)
    string_offset = next((offset + strings - address for address, offset, length in loads
                          if address <= strings and strings + string_size <= address + length), None)
    require(string_offset is not None, "ELF string table outside file-backed segment: " + abi)
    result = set()
    for offset in needed:
        require(offset < string_size, "ELF dependency outside string table: " + abi)
        end = data.find(b"\0", string_offset + offset, string_offset + string_size)
        require(end >= 0, "Unterminated ELF dependency: " + abi)
        result.add(data[string_offset + offset:end].decode("ascii"))
    return result


def verify(apk):
    manifest = json.loads((ROOT / "assets/models/model_manifest.json").read_text(encoding="utf-8"))
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entries are not distributable")
        for name in names:
            path = pathlib.PurePosixPath(name)
            require(not name.startswith(("assets/qa/", "assets/sam/", "assets/offline_food/"))
                    and not name.lower().endswith((".onnx", ".pt", ".pth", ".py", ".java", ".class",
                                                  ".safetensors", ".gguf", ".mnn.weight"))
                    and path.name not in RETIRED_FILES and name not in RETIRED_ASSETS,
                    "QA/source/download-only model artifact in APK: " + name)
        dex_names = [name for name in names if name.startswith("classes") and name.endswith(".dex")]
        require("classes.dex" in dex_names, "Missing classes.dex")
        for name in dex_names:
            data = archive.read(name)
            require(b"UpgradeSmoke" not in data, "QA instrumentation runner in " + name)
            require(not any(retired in data for retired in RETIRED_CLASSES), "Retired recognition class in " + name)
        actual_libs = {name for name in names if name.startswith("lib/") and not name.endswith("/")}
        require(not ({pathlib.PurePosixPath(name).name for name in actual_libs} & RETIRED_LIBS),
                "Retired recognition runtime in APK")
        expected_libs = {"lib/" + abi + "/" + lib for abi in ABIS
                         for lib in COMMON_LIBS}
        require(actual_libs == expected_libs,
                "Unexpected ABI/library set: missing=" + str(sorted(expected_libs - actual_libs))
                + ", extra=" + str(sorted(actual_libs - expected_libs)))
        for abi in ABIS:
            packaged = {pathlib.PurePosixPath(name).name for name in actual_libs if name.startswith("lib/" + abi + "/")}
            for lib in sorted(packaged):
                data = archive.read("lib/" + abi + "/" + lib)
                needed = elf_needed(data, abi)
                require(not (needed - packaged - SYSTEM_LIBS), "Unpackaged native dependencies: " + abi + "/" + lib
                        + " -> " + str(sorted(needed - packaged - SYSTEM_LIBS)))
                if lib in RUNTIME_HASHES[abi]:
                    require(sha256(data) == RUNTIME_HASHES[abi][lib], "Pinned runtime differs: " + abi + "/" + lib)
                if lib == "libeatvision.so":
                    require(b"EatVisionQA" not in data, "QA native entry point in " + abi)
        configs = [manifest["full"], manifest["small"], manifest["refiner"]["encoder"], manifest["refiner"]["decoder"]]
        expected_models = {"assets/models/" + config["file"] for config in configs}
        require({name for name in names if name.endswith(".mnn")} == expected_models, "Expected exactly four bundled cutout models")
        for config in configs:
            require(sha256(archive.read("assets/models/" + config["file"])) == config["sha256"], "Bundled model hash differs: " + config["file"])
        read_local_asset(archive, "assets/models/model_manifest.json")
        guide = read_local_asset(archive, "assets/help/getting-started.md")
        require(guide == (ROOT / "server/新手教程.md").read_bytes(), "Bundled guide differs from current guide")
        require(not any(word in guide for word in ("离线基础模型".encode(), "1.29GiB".encode(), b"Qwen3.5-2B")),
                "Retired recognition recommendation in bundled guide")
        font = archive.read("res/font/xiaolai_regular.ttf")
        require(sha256(font) == "e2f68daf0e72777a8cf58bc83de1b98634b251e537ddbfca24b0ae50d1802da2"
                and font == (ROOT / "res/font/xiaolai_regular.ttf").read_bytes(), "Bundled Xiaolai font differs")
        for name in ("EdgeTAM-NOTICE.txt", "MNN-Apache-2.0.txt", "ISNet-Apache-2.0.txt", "ISNet-NOTICE.txt",
                     "U2Net-Apache-2.0.txt", "rembg-MIT.txt", "Xiaolai-OFL-1.1.txt", "Xiaolai-NOTICE.txt"):
            require(read_local_asset(archive, "assets/licenses/" + name), "Empty required license: " + name)
    size = apk.stat().st_size
    require(size <= 300 * 1024 * 1024, "APK exceeds 300 MiB budget")
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    return {"bytes": size, "MiB": size / (1024 * 1024), "sha256": digest, "models": 4,
            "abis": list(ABIS), "native_libraries": len(expected_libs), "native_dependency_closure": True,
            "pinned_runtime_hashes": sum(map(len, RUNTIME_HASHES.values())),
            "retired_recognition_components_absent": True,
            "QA_in_distributable": False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    args = parser.parse_args()
    print(json.dumps(verify(pathlib.Path(args.apk)), indent=2))


if __name__ == "__main__":
    main()
