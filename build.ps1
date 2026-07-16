param(
    [switch]$NoUpload
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Version-FromName($Name) {
    $Text = $Name -replace '^[^\d]*', ''
    try {
        return [version]$Text
    } catch {
        return [version]"0.0.0"
    }
}

$LocalJdkBase = Join-Path $env:LOCALAPPDATA "Programs\EclipseAdoptium"
if (-not (Get-Command javac -ErrorAction SilentlyContinue) -and (Test-Path $LocalJdkBase)) {
    $LocalJdkRoot = Get-ChildItem $LocalJdkBase -Directory -Filter "jdk-*" |
        Sort-Object @{ Expression = { Version-FromName $_.Name }; Descending = $true } |
        Select-Object -First 1
    if ($LocalJdkRoot -and (Test-Path (Join-Path $LocalJdkRoot.FullName "bin\javac.exe"))) {
        $env:JAVA_HOME = $LocalJdkRoot.FullName
        $env:Path = (Join-Path $LocalJdkRoot.FullName "bin") + ";" + $env:Path
    }
}

$SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$BuildToolsRoot = Join-Path $SdkRoot "build-tools"
$BuildTools = Get-ChildItem $BuildToolsRoot -Directory |
    Sort-Object @{ Expression = { Version-FromName $_.Name }; Descending = $true } |
    Select-Object -First 1
if (-not $BuildTools) { throw "Android build-tools not found under $BuildToolsRoot" }

$Aapt2 = Join-Path $BuildTools.FullName "aapt2.exe"
$D8 = Join-Path $BuildTools.FullName "d8.bat"
$ZipAlign = Join-Path $BuildTools.FullName "zipalign.exe"
$ApkSigner = Join-Path $BuildTools.FullName "apksigner.bat"
$PlatformRoot = Join-Path $SdkRoot "platforms"
$Platform = Get-ChildItem $PlatformRoot -Directory -Filter "android-*" |
    Sort-Object @{ Expression = { Version-FromName $_.Name }; Descending = $true } |
    Select-Object -First 1
if (-not $Platform) { throw "Android platforms not found under $PlatformRoot" }
$TargetSdkVersion = [int]($Platform.Name -replace '^android-', '')
$AndroidJar = Join-Path $Platform.FullName "android.jar"
$CMakeRoot = Join-Path $SdkRoot "cmake"
$CMakeDir = Get-ChildItem $CMakeRoot -Directory -ErrorAction SilentlyContinue |
    Sort-Object @{ Expression = { Version-FromName $_.Name }; Descending = $true } |
    Select-Object -First 1
if (-not $CMakeDir) { throw "Android CMake not found under $CMakeRoot" }
$CMakeExe = Join-Path $CMakeDir.FullName "bin\cmake.exe"
$NinjaExe = Join-Path $CMakeDir.FullName "bin\ninja.exe"
$NdkRoot = (Get-ChildItem (Join-Path $SdkRoot "ndk") -Directory |
    Sort-Object @{ Expression = { Version-FromName $_.Name }; Descending = $true } |
    Select-Object -First 1).FullName
$BuildRoot = Join-Path $ProjectRoot "build"
$BuildDir = Join-Path $BuildRoot ("run-" + [guid]::NewGuid().ToString("N"))
$CompiledDir = Join-Path $BuildDir "compiled"
$GenDir = Join-Path $BuildDir "gen"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
$OutDir = Join-Path $ProjectRoot "dist"
$ManifestPath = Join-Path $ProjectRoot "AndroidManifest.xml"
$ManifestText = Get-Content $ManifestPath -Raw
if ($ManifestText -notmatch 'android:versionName="([^"]+)"') {
    throw "android:versionName not found in $ManifestPath"
}
$VersionName = $Matches[1]

function Assert-Ok($StepName) {
    if ($LASTEXITCODE -ne 0) {
        throw "$StepName failed with exit code $LASTEXITCODE"
    }
}

function Ensure-File($Path, $Url, $Label) {
    if (Test-Path $Path) {
        return
    }
    $Parent = Split-Path -Parent $Path
    New-Item $Parent -ItemType Directory -Force | Out-Null
    Write-Host "Downloading $Label..."
    Invoke-WebRequest -Uri $Url -OutFile $Path -TimeoutSec 900
}

function Ensure-MnnAndroid {
    $DepsDir = Join-Path $BuildRoot "deps\mnn"
    $Root = Join-Path $DepsDir "mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan"
    if (Test-Path $Root) {
        return $Root
    }
    $ZipPath = Join-Path $DepsDir "mnn_3.6.0_android.zip"
    Ensure-File $ZipPath `
        "https://github.com/alibaba/MNN/releases/download/3.6.0/mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip" `
        "MNN Android runtime"
    Expand-Archive -Path $ZipPath -DestinationPath $DepsDir -Force
    if (-not (Test-Path $Root)) {
        throw "MNN Android runtime extraction failed: $Root"
    }
    return $Root
}

function Ensure-SamModels {
    $ModelDir = Join-Path $BuildRoot "deps\sam-models"
    New-Item $ModelDir -ItemType Directory -Force | Out-Null
    $Embed = Join-Path $ModelDir "embed_vitb_int4.mnn"
    $Segment = Join-Path $ModelDir "segment_vitb_fp32.mnn"
    Ensure-File $Embed `
        "https://github.com/wangzhaode/mnn-segment-anything/releases/download/vit_b_mnn/embed_vitb_int4.mnn" `
        "SAM vit_b int4 embed model"
    Ensure-File $Segment `
        "https://github.com/wangzhaode/mnn-segment-anything/releases/download/vit_b_mnn/segment_vitb_fp32.mnn" `
        "SAM vit_b segment model"
    return @(
        [pscustomobject]@{ Path = $Embed; Entry = "assets/sam/embed_vitb_int4.mnn" },
        [pscustomobject]@{ Path = $Segment; Entry = "assets/sam/segment_vitb_fp32.mnn" }
    )
}

function Ensure-MnnHeaders {
    $HeadersRoot = Join-Path $BuildRoot "research\MNN"
    if (-not (Test-Path $HeadersRoot)) {
        New-Item (Split-Path -Parent $HeadersRoot) -ItemType Directory -Force | Out-Null
        git clone --depth 1 --filter=blob:none --sparse https://github.com/alibaba/MNN.git $HeadersRoot
        Assert-Ok "git clone MNN headers"
        git -C $HeadersRoot sparse-checkout set include tools/cv/include
        Assert-Ok "git sparse-checkout MNN headers"
    }
    if (-not (Test-Path (Join-Path $HeadersRoot "include\MNN\expr\Module.hpp"))) {
        throw "MNN headers missing: $HeadersRoot"
    }
    return $HeadersRoot
}

function Build-NativeSam {
    if (-not (Test-Path $CMakeExe)) { throw "cmake.exe not found under $CMakeRoot" }
    if (-not (Test-Path $NinjaExe)) { throw "ninja.exe not found under $CMakeRoot" }
    if (-not (Test-Path $NdkRoot)) { throw "Android NDK not found under $(Join-Path $SdkRoot "ndk")" }
    $MnnRoot = Ensure-MnnAndroid
    $HeadersRoot = Ensure-MnnHeaders
    $Outputs = @()
    foreach ($Abi in @("arm64-v8a", "armeabi-v7a")) {
        $NativeBuildDir = Join-Path $BuildDir ("native-" + $Abi)
        $MnnLibDir = Join-Path $MnnRoot $Abi
        $ConfigureOutput = & $CMakeExe `
            -S (Join-Path $ProjectRoot "native") `
            -B $NativeBuildDir `
            -G Ninja `
            "-DCMAKE_MAKE_PROGRAM=$NinjaExe" `
            "-DCMAKE_TOOLCHAIN_FILE=$(Join-Path $NdkRoot "build\cmake\android.toolchain.cmake")" `
            "-DANDROID_ABI=$Abi" `
            "-DANDROID_PLATFORM=android-26" `
            "-DANDROID_STL=c++_shared" `
            "-DMNN_INCLUDE_DIR=$(Join-Path $HeadersRoot "include")" `
            "-DMNN_CV_INCLUDE_DIR=$(Join-Path $HeadersRoot "tools\cv\include")" `
            "-DMNN_LIB_DIR=$MnnLibDir" `
            "-DCMAKE_BUILD_TYPE=Release" 2>&1
        if ($ConfigureOutput) { $ConfigureOutput | ForEach-Object { Write-Host $_ } }
        Assert-Ok "cmake configure $Abi"
        $BuildOutput = & $CMakeExe --build $NativeBuildDir --config Release 2>&1
        if ($BuildOutput) { $BuildOutput | ForEach-Object { Write-Host $_ } }
        Assert-Ok "cmake build $Abi"
        $So = Join-Path $NativeBuildDir "libeatsam.so"
        if (-not (Test-Path $So)) {
            throw "Native library missing: $So"
        }
        $Outputs += [pscustomobject]@{
            Abi = $Abi
            Sam = $So
            LibDir = $MnnLibDir
            Deps = @("libc++_shared.so", "libMNN.so", "libMNN_Express.so", "libMNNOpenCV.so")
        }
    }
    return $Outputs
}

function Add-ZipFile($Zip, $Path, $EntryName, $Compression) {
    $Existing = $Zip.GetEntry($EntryName)
    if ($Existing) { $Existing.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $Zip,
        $Path,
        $EntryName,
        $Compression
    ) | Out-Null
}

New-Item $BuildRoot -ItemType Directory -Force | Out-Null
New-Item $CompiledDir, $GenDir, $ClassesDir, $DexDir, $OutDir -ItemType Directory -Force | Out-Null

& $Aapt2 compile --dir (Join-Path $ProjectRoot "res") -o (Join-Path $CompiledDir "res.zip")
Assert-Ok "aapt2 compile"
& $Aapt2 link `
    -I $AndroidJar `
    --manifest (Join-Path $ProjectRoot "AndroidManifest.xml") `
    --java $GenDir `
    --min-sdk-version 26 `
    --target-sdk-version $TargetSdkVersion `
    -o (Join-Path $BuildDir "EatRecord.unsigned.apk") `
    (Join-Path $CompiledDir "res.zip")
Assert-Ok "aapt2 link"

$JavaFiles = @((Join-Path $GenDir "com\eatrecord\app\R.java"))
$JavaFiles += Get-ChildItem (Join-Path $ProjectRoot "src\com\eatrecord\app") -Filter *.java |
    Sort-Object Name |
    Select-Object -ExpandProperty FullName

javac -encoding UTF-8 --release 8 -cp $AndroidJar -d $ClassesDir $JavaFiles
Assert-Ok "javac"

$ClassesJar = Join-Path $BuildDir "classes.jar"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ClassZip = [System.IO.Compression.ZipFile]::Open($ClassesJar, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem $ClassesDir -Recurse -Filter *.class | ForEach-Object {
        $EntryName = $_.FullName.Substring($ClassesDir.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $ClassZip,
            $_.FullName,
            $EntryName,
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $ClassZip.Dispose()
}

& $D8 --min-api 26 --lib $AndroidJar --output $DexDir $ClassesJar
Assert-Ok "d8"

$NativeOutputs = @()
$SamAssets = @()
$AssetRoot = Join-Path $ProjectRoot "assets"
$ProjectAssets = @()
if (Test-Path $AssetRoot) {
    $ProjectAssets = Get-ChildItem $AssetRoot -Recurse -File | ForEach-Object {
        [pscustomobject]@{
            Path = $_.FullName
            Entry = "assets/" + $_.FullName.Substring($AssetRoot.Length + 1).Replace("\", "/")
        }
    }
}

$UnsignedApk = Join-Path $BuildDir "EatRecord.unsigned.apk"
$Zip = [System.IO.Compression.ZipFile]::Open($UnsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    Add-ZipFile $Zip (Join-Path $DexDir "classes.dex") "classes.dex" ([System.IO.Compression.CompressionLevel]::Optimal)
    foreach ($NativeOutput in $NativeOutputs) {
        Add-ZipFile $Zip $NativeOutput.Sam "lib/$($NativeOutput.Abi)/libeatsam.so" ([System.IO.Compression.CompressionLevel]::Optimal)
        foreach ($Dep in $NativeOutput.Deps) {
            Add-ZipFile $Zip (Join-Path $NativeOutput.LibDir $Dep) "lib/$($NativeOutput.Abi)/$Dep" ([System.IO.Compression.CompressionLevel]::Optimal)
        }
    }
    foreach ($Asset in $SamAssets) {
        Add-ZipFile $Zip $Asset.Path $Asset.Entry ([System.IO.Compression.CompressionLevel]::Optimal)
    }
    foreach ($Asset in $ProjectAssets) {
        Add-ZipFile $Zip $Asset.Path $Asset.Entry ([System.IO.Compression.CompressionLevel]::Optimal)
    }
}
finally {
    $Zip.Dispose()
}

$Keystore = Join-Path $ProjectRoot "debug.keystore"
if (-not (Test-Path $Keystore)) {
    keytool -genkeypair -v `
        -keystore $Keystore `
        -storepass android `
        -alias androiddebugkey `
        -keypass android `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=EatRecord, OU=Local, O=Codex, L=Local, S=Local, C=CN" | Out-Null
}

$AlignedApk = Join-Path $BuildDir "EatRecord.aligned.apk"
$FinalApk = Join-Path $OutDir "EatRecord-$VersionName-debug.apk"
$SignedApk = Join-Path $BuildDir "EatRecord-debug.apk"
& $ZipAlign -f 4 $UnsignedApk $AlignedApk
Assert-Ok "zipalign"
& $ApkSigner sign `
    --ks $Keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --v4-signing-enabled false `
    --out $SignedApk `
    $AlignedApk
Assert-Ok "apksigner"
Copy-Item $SignedApk $FinalApk -Force

$Size = [math]::Round((Get-Item $FinalApk).Length / 1MB, 3)
Write-Host "Built $FinalApk ($Size MB)"

if (-not $NoUpload) {
    $UploadScript = Join-Path $ProjectRoot "scripts\upload-lanzou.ps1"
    if (-not (Test-Path $UploadScript)) {
        throw "Upload script not found: $UploadScript"
    }

    & $UploadScript -Apk $FinalApk -Version $VersionName
    Assert-Ok "lanzou upload"
}
