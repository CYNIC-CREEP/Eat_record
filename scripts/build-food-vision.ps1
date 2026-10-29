param([string]$ProjectRoot, [string]$BuildDir, [string]$SdkRoot, [string]$NdkRoot, [string]$CMakeExe, [string]$NinjaExe, [switch]$Qa)
$ErrorActionPreference = 'Stop'
$VisionRoot = Join-Path $ProjectRoot 'build\deps\vision'
New-Item $VisionRoot -ItemType Directory -Force | Out-Null
function Verified-Download($Path, $Url, $Hash) {
    if ((Test-Path $Path) -and ((Get-FileHash $Path -Algorithm SHA256).Hash.ToLowerInvariant() -eq $Hash)) { return }
    $Partial = "$Path.download"
    Invoke-WebRequest $Url -OutFile $Partial -TimeoutSec 900
    if ((Get-FileHash $Partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Hash) { throw "SHA-256 mismatch: $Path" }
    Move-Item -LiteralPath $Partial -Destination $Path -Force
}
$RuntimeZip = Join-Path $VisionRoot 'mnn-android-3.6.0.zip'
Verified-Download $RuntimeZip 'https://github.com/alibaba/MNN/releases/download/3.6.0/mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip' '3ff2b92e11531f5a9d820b6bf6a8aede3e124e098a3645d8f6a23dcbc862015f'
$Runtime = Join-Path $VisionRoot 'runtime\mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan'
if (-not (Test-Path "$Runtime\arm64-v8a\libMNN.so")) { Expand-Archive $RuntimeZip (Join-Path $VisionRoot 'runtime') -Force }
$Headers = Join-Path $VisionRoot 'MNN'
if (-not (Test-Path "$Headers\.git")) {
    git clone --depth 1 --branch 3.6.0 --filter=blob:none --sparse https://github.com/alibaba/MNN.git $Headers | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'MNN headers download failed' }
    git -C $Headers sparse-checkout set include | Out-Host
}
$Revision = (git -C $Headers rev-parse HEAD).Trim()
if ($Revision -ne 'cc20f672af9e177e2fa338c332dc097de2fc9264') { throw 'Unexpected MNN headers revision' }
$Python = (Get-Command python -ErrorAction Stop).Source
if (-not (Test-Path "$VisionRoot\python\_tools.cp311-win_amd64.pyd")) {
    & $Python -m pip install --target "$VisionRoot\python" 'MNN==3.6.0' --disable-pip-version-check | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'MNN model converter installation failed (Python 3.11 required)' }
}
if (-not (Test-Path "$VisionRoot\python\onnx\__init__.py")) {
    & $Python -m pip install --target "$VisionRoot\python" 'onnx==1.23.2' --disable-pip-version-check | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'ONNX graph tooling installation failed' }
}
& $Python (Join-Path $ProjectRoot 'scripts\prepare-food-models.py') --root $ProjectRoot | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Model conversion or integrity check failed' }
& $Python (Join-Path $ProjectRoot 'scripts\prepare-edgetam.py') | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'EdgeTAM conversion or integrity check failed' }
$Manifest = Get-Content (Join-Path $ProjectRoot 'assets\models\model_manifest.json') -Raw | ConvertFrom-Json
$Entries = @()
foreach ($Model in @($Manifest.full, $Manifest.small, $Manifest.refiner.encoder, $Manifest.refiner.decoder)) {
    $Entries += [pscustomobject]@{Path=(Join-Path $VisionRoot $Model.file); Entry="assets/models/$($Model.file)"}
}
foreach ($Abi in @('arm64-v8a','armeabi-v7a')) {
    $NativeDir = Join-Path $BuildDir "vision-$Abi"
    $LibraryDir = Join-Path $Runtime $Abi
    & $CMakeExe -S (Join-Path $ProjectRoot 'native') -B $NativeDir -G Ninja "-DCMAKE_MAKE_PROGRAM=$NinjaExe" `
        "-DCMAKE_TOOLCHAIN_FILE=$NdkRoot\build\cmake\android.toolchain.cmake" "-DANDROID_ABI=$Abi" `
        '-DANDROID_PLATFORM=android-26' '-DANDROID_STL=c++_shared' "-DEATVISION_QA=$([int]$Qa.IsPresent)" "-DMNN_INCLUDE_DIR=$Headers\include" `
        "-DMNN_LIB_DIR=$LibraryDir" '-DCMAKE_BUILD_TYPE=Release' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Native configure failed: $Abi" }
    & $CMakeExe --build $NativeDir --config Release | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Native build failed: $Abi" }
    $Entries += [pscustomobject]@{Path=(Join-Path $NativeDir 'libeatvision.so'); Entry="lib/$Abi/libeatvision.so"}
    # Only the pinned cutout runtime libraries are included.
    foreach ($Name in @('libMNN.so','libmnncore.so','libc++_shared.so')) {
        $Entries += [pscustomobject]@{Path=(Join-Path $LibraryDir $Name); Entry="lib/$Abi/$Name"}
    }

}
$Entries
