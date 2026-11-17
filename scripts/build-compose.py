"""Pinned Compose/Kotlin support for the existing javac/aapt2/d8 build.

No Gradle project is generated. AARs supply dependency classes and Android resources;
the Kotlin compiler and Compose plugin are build-only dependencies.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from collections import deque

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / "build/deps/compose"
LOCK = ROOT / "scripts/compose-dependencies.lock.json"
KOTLIN = "2.0.21"
COMPOSE = "1.7.8"
LIFECYCLE = "2.8.7"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
REPOSITORIES = {
    "google": "https://dl.google.com/dl/android/maven2/",
    "central": "https://repo.maven.apache.org/maven2/",
}
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))
ANDROID = "{http://schemas.android.com/apk/res/android}"


def digest(path: pathlib.Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def save_json(path: pathlib.Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def fetch(relative: str, repository: str, expected: str | None = None) -> pathlib.Path:
    target = CACHE / "maven" / repository / relative
    if target.exists() and (expected is None or digest(target) == expected):
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".download")
    for attempt in range(3):
        try:
            request = urllib.request.Request(REPOSITORIES[repository] + relative)
            with OPENER.open(request, timeout=120) as response, temporary.open("wb") as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
            if expected is not None and digest(temporary) != expected:
                raise RuntimeError("Dependency SHA256 mismatch: " + relative)
            os.replace(temporary, target)
            return target
        except urllib.error.HTTPError as error:
            if error.code == 404:
                raise
            if attempt == 2:
                raise
        except Exception:
            if attempt == 2:
                raise
        time.sleep(attempt + 1)
    raise RuntimeError("Dependency download failed: " + relative)


def version_key(version: str) -> tuple:
    return tuple(int(part) if part.isdigit() else part for part in re.split(r"[.\-]", version))


def canonical(group: str, artifact: str, version: str, runtime: bool) -> tuple[str, str, str]:
    version = version.strip("[]")
    if "," in version or not version:
        raise RuntimeError("Unpinned Maven version: " + group + ":" + artifact + ":" + version)
    if runtime:
        if group.startswith("androidx.compose."):
            version = COMPOSE
        elif group == "androidx.lifecycle":
            version = LIFECYCLE
        elif group == "org.jetbrains.kotlin" and artifact.startswith("kotlin-stdlib"):
            version = KOTLIN
        elif group == "org.jetbrains.kotlinx" and artifact.startswith("kotlinx-coroutines"):
            version = "1.8.1"
        elif group == "androidx.collection":
            version = "1.4.5"
        elif group == "androidx.annotation" and artifact in ("annotation", "annotation-jvm"):
            version = "1.8.1"
    return group, artifact, version


def pom(coordinate: tuple[str, str, str]) -> tuple[ET.Element, str]:
    group, artifact, version = coordinate
    path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    repository = "google" if group.startswith("androidx.") else "central"
    return ET.parse(fetch(path, repository)).getroot(), repository


def resolve(roots: list[tuple[str, str, str]], runtime: bool) -> list[dict]:
    selected: dict[tuple[str, str], tuple[str, str, str]] = {}
    pending = deque(roots)
    models: dict[tuple[str, str, str], tuple[ET.Element, str]] = {}
    while pending:
        coordinate = canonical(*pending.popleft(), runtime)
        group, artifact, version = coordinate
        key = group, artifact
        existing = selected.get(key)
        if existing is not None and version_key(existing[2]) >= version_key(version):
            continue
        selected[key] = coordinate
        model, repository = models.setdefault(coordinate, pom(coordinate))
        for dependency in model.findall("m:dependencies/m:dependency", NS):
            scope = dependency.findtext("m:scope", "compile", NS)
            if scope not in ("compile", "runtime") or dependency.findtext("m:optional", "false", NS) == "true":
                continue
            dg = dependency.findtext("m:groupId", "", NS)
            da = dependency.findtext("m:artifactId", "", NS)
            dv = dependency.findtext("m:version", "", NS)
            if "${" in dv:
                properties = model.find("m:properties", NS)
                property_name = dv.removeprefix("${").removesuffix("}")
                dv = "" if properties is None else properties.findtext("m:" + property_name, "", NS)
            if not dv:
                raise RuntimeError("Dependency version missing in " + ":".join(coordinate) + ": " + da)
            pending.append((dg, da, dv))
    result = []
    for coordinate in sorted(selected.values()):
        model, repository = models[coordinate]
        group, artifact, version = coordinate
        packaging = model.findtext("m:packaging", "jar", NS)
        if packaging == "pom":
            continue
        extension = "aar" if packaging == "aar" else "jar"
        relative = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.{extension}"
        try:
            binary = fetch(relative, repository)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            # Multiplatform metadata delegators sometimes publish a .jar despite an aar POM.
            if extension == "aar":
                extension = "jar"
                relative = relative[:-3] + "jar"
                binary = fetch(relative, repository)
            else:
                raise
        result.append({"coordinate": ":".join(coordinate), "repository": repository,
                       "path": relative, "sha256": digest(binary),
                       "licenses": [{"name": item.findtext("m:name", "", NS),
                                     "url": item.findtext("m:url", "", NS)}
                                    for item in model.findall("m:licenses/m:license", NS)]})
    return result


def create_lock() -> None:
    runtime = resolve([
        ("androidx.compose.ui", "ui-android", COMPOSE),
        ("androidx.compose.foundation", "foundation-android", COMPOSE),
        ("androidx.compose.animation", "animation-core-android", COMPOSE),
        ("androidx.lifecycle", "lifecycle-runtime-android", LIFECYCLE),
        ("androidx.lifecycle", "lifecycle-viewmodel-android", LIFECYCLE),
        ("androidx.savedstate", "savedstate", "1.2.1"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", KOTLIN),
    ], True)
    compiler = resolve([
        ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", KOTLIN),
        ("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", KOTLIN),
    ], False)
    save_json(LOCK, {"kotlin": KOTLIN, "compose": COMPOSE, "runtime": runtime, "compiler": compiler})
    print(f"Locked {len(runtime)} runtime and {len(compiler)} compiler dependencies", flush=True)


def extract_dependency(item: dict) -> pathlib.Path:
    archive = fetch(item["path"], item["repository"], item["sha256"])
    if archive.suffix == ".jar":
        return archive
    destination = CACHE / "aar" / item["coordinate"].replace(":", "_")
    marker = destination / ".sha256"
    if not marker.exists() or marker.read_text() != item["sha256"]:
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as source:
            for member in source.infolist():
                path = pathlib.PurePosixPath(member.filename)
                if ".." in path.parts or path.is_absolute():
                    raise RuntimeError("Invalid AAR path")
            source.extractall(destination)
        marker.write_text(item["sha256"])
    return destination


def prepare(args: argparse.Namespace) -> None:
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    work = pathlib.Path(args.build_dir).resolve()
    work.mkdir(parents=True, exist_ok=True)
    resources = []
    packages = []
    jars = []
    payload: dict[str, bytes] = {}
    services: dict[str, set[str]] = {}
    notices = ["Kotlin and AndroidX Compose runtime dependencies", "",
               "AndroidX is developed by the Android Open Source Project contributors.",
               "Kotlin is developed by JetBrains and contributors; kotlinx libraries retain their respective contributors.",
               "Full Apache License 2.0 text: Kotlin-Compose-Apache-2.0.txt in this directory.",
               "All versions and binary SHA256 values are locked in scripts/compose-dependencies.lock.json.", ""]
    for item in lock["runtime"]:
        path = extract_dependency(item)
        notices += [item["coordinate"]] + [license["name"] + " " + license["url"] for license in item["licenses"]] + [""]
        if path.is_file():
            library_jars = [path]
        else:
            manifest = ET.parse(path / "AndroidManifest.xml").getroot()
            package = manifest.get("package")
            if package:
                packages.append(package)
            source_resources = path / "res"
            if source_resources.is_dir():
                resource_archive = path / "compiled-res.zip"
                if not resource_archive.exists():
                    subprocess.run([args.aapt2, "compile", "--dir", str(source_resources), "-o", str(resource_archive)], check=True)
                resources.append(str(resource_archive))
            library_jars = ([path / "classes.jar"] if (path / "classes.jar").is_file() else [])
            library_jars += sorted((path / "libs").glob("*.jar"))
            for asset in (path / "assets").rglob("*") if (path / "assets").is_dir() else []:
                if asset.is_file():
                    payload["assets/" + asset.relative_to(path / "assets").as_posix()] = asset.read_bytes()
            for abi in ("arm64-v8a", "armeabi-v7a"):
                for library in (path / "jni" / abi).glob("*.so"):
                    payload["lib/" + abi + "/" + library.name] = library.read_bytes()
        jars.extend(str(path) for path in library_jars)
        for library in library_jars:
            with zipfile.ZipFile(library) as source:
                for name in source.namelist():
                    if name.startswith("META-INF/services/") and not name.endswith("/"):
                        services.setdefault(name, set()).update(source.read(name).decode("utf-8").splitlines())
                    basename = pathlib.PurePosixPath(name).name
                    if (name.startswith("META-INF/") and not name.endswith("/")
                            and re.match(r"^(LICENSE|NOTICE|COPYRIGHT)(\.|$)", basename, re.I)):
                        license_path = "assets/licenses/compose/" + item["coordinate"].replace(":", "-")
                        payload[license_path + "/" + library.stem + "/" + basename] = source.read(name)
    for name, values in services.items():
        payload[name] = ("\n".join(sorted(value for value in values if value.strip())) + "\n").encode()
    payload["assets/licenses/Compose-AndroidX-NOTICE.txt"] = "\n".join(notices).encode()
    extras = work / "compose-payload.zip"
    with zipfile.ZipFile(extras, "w", zipfile.ZIP_DEFLATED) as output:
        for name, data in sorted(payload.items()):
            output.writestr(name, data)
    compiler = [str(fetch(item["path"], item["repository"], item["sha256"])) for item in lock["compiler"]]
    plugin = next(path for path in compiler if "kotlin-compose-compiler-plugin-embeddable" in path)
    save_json(work / "compose-build.json", {"jars": jars, "compiler": compiler, "plugin": plugin,
              "resources": resources, "packages": sorted(set(packages)), "payload": str(extras)})
    print(f"Compose dependencies ready: {len(jars)} jars, {len(resources)} resource archives", flush=True)


def compile_kotlin(args: argparse.Namespace) -> None:
    work = pathlib.Path(args.build_dir).resolve()
    config = json.loads((work / "compose-build.json").read_text(encoding="utf-8"))
    sources = sorted((ROOT / "src").rglob("*.kt"))
    if not sources:
        return
    save_json(work / "compose-source.json", {str(source.relative_to(ROOT)): digest(source) for source in sources})
    classes = work / "classes"
    classpath = os.pathsep.join([args.android_jar, str(classes)] + config["jars"])
    command = [args.java, "-cp", os.pathsep.join(config["compiler"]),
               "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-no-jdk",
               "-jvm-target", "1.8", "-Xlambdas=class", "-Xjava-source-roots=" + str(ROOT / "src"),
               "-Xplugin=" + config["plugin"], "-classpath", classpath, "-d", str(classes)]
    command.extend(str(source) for source in sources)
    subprocess.run(command, check=True)
    print("Kotlin + Compose compilation passed", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("lock", "prepare", "compile"))
    parser.add_argument("--build-dir")
    parser.add_argument("--aapt2")
    parser.add_argument("--android-jar")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    try:
        if args.phase == "lock":
            create_lock()
        elif args.phase == "prepare":
            prepare(args)
        else:
            compile_kotlin(args)
    except subprocess.CalledProcessError as error:
        # The compiler has already printed actionable source diagnostics.
        raise SystemExit(error.returncode) from None
