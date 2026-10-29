param(
    [switch]$NoUpload,
    [switch]$Qa,
    [string]$QaFixture
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
if (Test-Path $LocalJdkBase) {
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

$ReleaseNotesPath = Join-Path $ProjectRoot 'src\com\eatrecord\app\ReleaseNotes.java'
if (-not (Test-Path $ReleaseNotesPath)) { throw 'Local release notes source is required' }
$ReleaseGroups = [regex]::Matches((Get-Content $ReleaseNotesPath -Raw), '(?m)^\s*\{\s*"([^"]+)"')
if ($ReleaseGroups.Count -eq 0 -or $ReleaseGroups[$ReleaseGroups.Count - 1].Groups[1].Value -ne $VersionName) {
    throw "Release notes must contain the current version as their latest entry: $VersionName"
}

$DefaultFontPath = Join-Path $ProjectRoot 'res\font\xiaolai_regular.ttf'
if (-not (Test-Path $DefaultFontPath) -or (Get-FileHash $DefaultFontPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'e2f68daf0e72777a8cf58bc83de1b98634b251e537ddbfca24b0ae50d1802da2') {
    throw 'Bundled Xiaolai v3.126 font is missing or does not match its verified release hash'
}
if (-not (Test-Path (Join-Path $ProjectRoot 'assets\licenses\Xiaolai-OFL-1.1.txt'))) { throw 'Bundled font license is required' }

function Assert-Ok($StepName) {
    if ($LASTEXITCODE -ne 0) {
        throw "$StepName failed with exit code $LASTEXITCODE"
    }
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
$BuildManifest = $ManifestPath
if ($Qa) {
    $BuildManifest = Join-Path $BuildDir 'AndroidManifest.xml'
    [xml]$QaXml = $ManifestText
    $AndroidNs = 'http://schemas.android.com/apk/res/android'
    $QaXml.manifest.application.SetAttribute('debuggable', $AndroidNs, 'true')
    $Instrumentation = $QaXml.CreateElement('instrumentation')
    $Instrumentation.SetAttribute('name', $AndroidNs, 'com.eatrecord.app.UpgradeSmoke')
    $Instrumentation.SetAttribute('targetPackage', $AndroidNs, 'com.eatrecord.app')
    $QaXml.manifest.AppendChild($Instrumentation) | Out-Null
    $QaXml.Save($BuildManifest)
    if (-not $NoUpload) { throw 'QA builds require -NoUpload' }
}

& $Aapt2 compile --dir (Join-Path $ProjectRoot "res") -o (Join-Path $CompiledDir "res.zip")
Assert-Ok "aapt2 compile"
& $Aapt2 link `
    -I $AndroidJar `
    --manifest $BuildManifest `
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
if ($Qa) { $JavaFiles += (Join-Path $ProjectRoot 'tests\android\UpgradeSmoke.java') }

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

$VisionEntries = @(& (Join-Path $ProjectRoot 'scripts\build-food-vision.ps1') `
    -ProjectRoot $ProjectRoot -BuildDir $BuildDir -SdkRoot $SdkRoot -NdkRoot $NdkRoot `
    -CMakeExe $CMakeExe -NinjaExe $NinjaExe -Qa:$Qa)
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
    foreach ($Asset in $ProjectAssets) {
        Add-ZipFile $Zip $Asset.Path $Asset.Entry ([System.IO.Compression.CompressionLevel]::Optimal)
    }
    foreach ($Asset in $VisionEntries) {
        Add-ZipFile $Zip $Asset.Path $Asset.Entry ([System.IO.Compression.CompressionLevel]::Optimal)
    }
    if ($Qa -and $QaFixture) {
        Add-ZipFile $Zip $QaFixture 'assets/qa/food.jpg' ([System.IO.Compression.CompressionLevel]::Optimal)
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
if ($Qa) { $FinalApk = Join-Path $OutDir "EatRecord-$VersionName-qa.apk" }
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
if ($Size -gt 300) { throw "APK exceeds 300 MiB budget: $Size MiB" }
$CheckZip = [System.IO.Compression.ZipFile]::OpenRead($FinalApk)
try {
    foreach ($Entry in $VisionEntries) {
        if (-not $CheckZip.GetEntry($Entry.Entry)) { throw "Missing APK entry: $($Entry.Entry)" }
    }
    $PackedModels = Get-Content (Join-Path $ProjectRoot 'assets\models\model_manifest.json') -Raw | ConvertFrom-Json
    foreach ($Model in @($PackedModels.full, $PackedModels.small, $PackedModels.refiner.encoder, $PackedModels.refiner.decoder)) {
        $Packed = $CheckZip.GetEntry("assets/models/$($Model.file)")
        if (-not $Packed) { throw "Missing packaged model: $($Model.file)" }
        $Stream = $Packed.Open()
        $Sha = [System.Security.Cryptography.SHA256]::Create()
        try { $Hash = [BitConverter]::ToString($Sha.ComputeHash($Stream)).Replace('-','').ToLowerInvariant() }
        finally { $Sha.Dispose(); $Stream.Dispose() }
        if ($Hash -ne $Model.sha256) { throw "Packaged model hash mismatch: $($Model.file)" }
    }
    if (@($CheckZip.Entries | Where-Object { $_.FullName -match 'sam/|libeatsam|\.onnx$' }).Count -gt 0) {
        throw 'Unexpected legacy model or ONNX source in APK'
    }
} finally { $CheckZip.Dispose() }
Write-Host "Built $FinalApk ($Size MB)"

if (-not $NoUpload) {
    $UploadScript = Join-Path $ProjectRoot "scripts\upload-lanzou.ps1"
    if (-not (Test-Path $UploadScript)) {
        throw "Upload script not found: $UploadScript"
    }

    & $UploadScript -Apk $FinalApk -Version $VersionName
    Assert-Ok "lanzou upload"
}
