param(
    [Parameter(Mandatory = $true)]
    [string]$Apk,

    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Url = "https://www.ilanzou.com/console/files/286902984?title=eat%20record&status=1"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Uploader = Join-Path $ProjectRoot "scripts\upload-lanzou.mjs"
$UploaderDeps = Join-Path $ProjectRoot ".lanzou-uploader"
$ResolvedApk = Resolve-Path -LiteralPath $Apk
$DistDir = Join-Path $ProjectRoot "dist"
$CodeZip = Join-Path $DistDir ("EatRecord-$Version-code.zip")

New-Item $DistDir -ItemType Directory -Force | Out-Null
if (Test-Path $CodeZip) {
    Remove-Item $CodeZip -Force
}

$CodeFiles = Get-ChildItem $ProjectRoot -Force | Where-Object {
    $_.Name -notin @(".git", ".agents", ".codex", ".lanzou-browser", ".lanzou-uploader", "build", "dist", "debug.keystore")
}
Compress-Archive -Path $CodeFiles.FullName -DestinationPath $CodeZip -CompressionLevel Optimal -Force

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is required for Lanzou upload automation."
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm is required for Lanzou upload automation."
}

if (-not (Test-Path (Join-Path $UploaderDeps "node_modules\playwright"))) {
    New-Item $UploaderDeps -ItemType Directory -Force | Out-Null
    npm install --prefix $UploaderDeps --no-save playwright
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install Playwright for Lanzou upload automation."
    }
}

node $Uploader --apk $ResolvedApk --code $CodeZip --version $Version --url $Url
if ($LASTEXITCODE -ne 0) {
    throw "Lanzou upload failed with exit code $LASTEXITCODE"
}
