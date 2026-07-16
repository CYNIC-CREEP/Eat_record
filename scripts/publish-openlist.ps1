param(
    [string]$BaseUrl = "http://47.97.215.111:5244",
    [string]$RemoteRoot = "/lanzou/Myapp/eat record",
    [string]$Username = $env:OPENLIST_USERNAME,
    [string]$Password = $env:OPENLIST_PASSWORD,
    [string]$ArtifactSuffix = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ManifestPath = Join-Path $ProjectRoot "AndroidManifest.xml"
$ManifestText = Get-Content $ManifestPath -Raw
if ($ManifestText -notmatch 'android:versionName="([^"]+)"') {
    throw "android:versionName not found in $ManifestPath"
}
$Version = $Matches[1]
if ($ManifestText -notmatch 'android:versionCode="([^"]+)"') {
    throw "android:versionCode not found in $ManifestPath"
}
$VersionCode = [int]$Matches[1]

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "Please set OPENLIST_USERNAME and OPENLIST_PASSWORD first, or pass -Username and -Password."
}

function Encode-Path([string]$PathValue) {
    $parts = $PathValue.Trim("/") -split "/"
    $encoded = @()
    foreach ($part in $parts) {
        if ($part.Length -gt 0) {
            $encoded += [System.Uri]::EscapeDataString($part)
        }
    }
    return "/" + ($encoded -join "/")
}

function Join-RemotePath([string]$Left, [string]$Right) {
    return $Left.TrimEnd("/") + "/" + $Right.TrimStart("/")
}

function Page-Url([string]$RemotePath) {
    return $BaseUrl.TrimEnd("/") + (Encode-Path $RemotePath)
}

function Api-Url([string]$Path) {
    return $BaseUrl.TrimEnd("/") + $Path
}

function Login-OpenList {
    $body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
    $result = Invoke-RestMethod -Uri (Api-Url "/api/auth/login") -Method Post -Body $body -ContentType "application/json"
    if ($result.code -ne 200 -or [string]::IsNullOrWhiteSpace($result.data.token)) {
        throw "OpenList login failed: $($result.message)"
    }
    return $result.data.token
}

function Api-Headers([string]$Token) {
    return @{ Authorization = $Token }
}

function Ensure-OpenListFolder([string]$Token, [string]$RemotePath) {
    $body = @{ path = $RemotePath } | ConvertTo-Json -Compress
    $result = Invoke-RestMethod -Uri (Api-Url "/api/fs/mkdir") -Method Post -Headers (Api-Headers $Token) -Body $body -ContentType "application/json"
    if ($result.code -ne 200) {
        throw "Create folder failed: $RemotePath - $($result.message)"
    }
}

function Remove-OpenListFileIfPresent([string]$Token, [string]$RemotePath) {
    $separator = $RemotePath.LastIndexOf("/")
    if ($separator -le 0 -or $separator -ge $RemotePath.Length - 1) {
        throw "Invalid remote file path: $RemotePath"
    }
    $folder = $RemotePath.Substring(0, $separator)
    $name = $RemotePath.Substring($separator + 1)
    $listBody = @{ path = $folder } | ConvertTo-Json -Compress
    $list = Invoke-RestMethod -Uri (Api-Url "/api/fs/list") -Method Post `
        -Headers (Api-Headers $Token) -Body $listBody -ContentType "application/json"
    if ($list.code -ne 200) {
        throw "List folder failed before upload: $folder - $($list.message)"
    }
    $exists = @($list.data.content | Where-Object { $_.name -eq $name }).Count -gt 0
    if (-not $exists) {
        return
    }
    $removeBody = @{ dir = $folder; names = @($name) } | ConvertTo-Json -Compress
    $removed = Invoke-RestMethod -Uri (Api-Url "/api/fs/remove") -Method Post `
        -Headers (Api-Headers $Token) -Body $removeBody -ContentType "application/json"
    if ($removed.code -ne 200) {
        throw "Remove old file failed: $RemotePath - $($removed.message)"
    }
}

function Upload-OpenListFile([string]$Token, [string]$LocalPath, [string]$RemotePath) {
    Remove-OpenListFileIfPresent $Token $RemotePath
    $headers = @{
        Authorization = $Token
        "File-Path" = [System.Uri]::EscapeDataString($RemotePath)
        "As-Task" = "false"
    }
    $bytes = [System.IO.File]::ReadAllBytes($LocalPath)
    $result = Invoke-RestMethod -Uri (Api-Url "/api/fs/put") -Method Put -Headers $headers -Body $bytes -ContentType "application/octet-stream"
    if ($result.code -ne 200) {
        throw "Upload failed: $RemotePath - $($result.message)"
    }
    Write-Host "Uploaded $RemotePath"
}

function Assert-RemoteContains([string]$Token, [string]$FolderPath, [string]$FileName) {
    for ($attempt = 1; $attempt -le 8; $attempt++) {
        $body = @{ path = $FolderPath } | ConvertTo-Json -Compress
        $result = Invoke-RestMethod -Uri (Api-Url "/api/fs/list") -Method Post -Headers (Api-Headers $Token) -Body $body -ContentType "application/json"
        if ($result.code -ne 200) {
            throw "List folder failed: $FolderPath - $($result.message)"
        }
        foreach ($item in $result.data.content) {
            if ($item.name -eq $FileName) {
                return
            }
        }
        if ($attempt -lt 8) {
            Start-Sleep -Seconds ([math]::Min(12, 2 + $attempt))
        }
    }
    throw "Uploaded file not visible yet: $FolderPath/$FileName"
}

if (-not $SkipBuild) {
    & (Join-Path $ProjectRoot "build.ps1") -NoUpload
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed with exit code $LASTEXITCODE"
    }
}

$DistDir = Join-Path $ProjectRoot "dist"
$Apk = Join-Path $DistDir ("EatRecord-$Version-debug.apk")
if (-not (Test-Path $Apk)) {
    throw "APK not found: $Apk"
}

Copy-Item -LiteralPath $Apk -Destination (Join-Path $DistDir "EatRecord-debug.apk") -Force
$PublishedApk = $Apk
if (-not [string]::IsNullOrWhiteSpace($ArtifactSuffix)) {
    $SafeSuffix = $ArtifactSuffix -replace '[^A-Za-z0-9._-]', '-'
    $PublishedApk = Join-Path $DistDir ("EatRecord-$Version-$SafeSuffix-debug.apk")
    Copy-Item -LiteralPath $Apk -Destination $PublishedApk -Force
}

$SourceZip = Join-Path $DistDir ("EatRecord-$Version-source.zip")
if (Test-Path $SourceZip) {
    Remove-Item -LiteralPath $SourceZip -Force
}
$SourceItems = Get-ChildItem $ProjectRoot -Force | Where-Object {
    $_.Name -notin @(
        ".git",
        ".agents",
        ".codex",
        ".codex-remote-attachments",
        ".lanzou-browser",
        ".lanzou-uploader",
        ".playwright-cli",
        "build",
        "dist",
        "debug.keystore"
    )
}
Compress-Archive -Path $SourceItems.FullName -DestinationPath $SourceZip -CompressionLevel Optimal -Force

$InstallDir = "apk"
$CodeDir = "source"
$ServerDir = "server"
$TutorialName = (-join ([char[]](0x65B0, 0x624B, 0x6559, 0x7A0B))) + ".md"

$ApkRemote = Join-RemotePath $RemoteRoot "$Version/$InstallDir/$(Split-Path -Leaf $PublishedApk)"
$SourceRemote = Join-RemotePath $RemoteRoot "$Version/$CodeDir/$(Split-Path -Leaf $SourceZip)"
$ServerCloudLocal = Join-Path $ProjectRoot "server\cloud-api-server.py"
$ServerDeployLocal = Join-Path $ProjectRoot "server\deploy-cloud-api.sh"
$ServerCloudRemote = Join-RemotePath $RemoteRoot "$Version/$ServerDir/cloud-api-server.py"
$ServerDeployRemote = Join-RemotePath $RemoteRoot "$Version/$ServerDir/deploy-cloud-api.sh"
$FontLocalDir = Join-Path $ProjectRoot "server\fonts"
$FontRemoteRoot = Join-RemotePath $RemoteRoot "fonts"
$UpdateRemote = Join-RemotePath $RemoteRoot "update.json"
$ApkUrl = Page-Url $ApkRemote

$UpdateLocal = Join-Path $DistDir "update.json"
$ServerUpdatePath = Join-Path $ProjectRoot "server\update.json"
$Notes = @(
    "Publish eat record $Version",
    "Fix meal photo pick animation and improve local record experience."
)
if (Test-Path $ServerUpdatePath) {
    try {
        $ServerUpdate = Get-Content -LiteralPath $ServerUpdatePath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($ServerUpdate.notes) {
            $Notes = @($ServerUpdate.notes)
        }
    } catch {
        Write-Host "Could not read server update notes, using defaults."
    }
}
$Update = [ordered]@{
    versionCode = $VersionCode
    versionName = $Version
    apkUrl = $ApkUrl
    apkPath = $ApkRemote
    notes = $Notes
}
$UpdateJsonText = $Update | ConvertTo-Json -Depth 4
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($UpdateLocal, $UpdateJsonText, $Utf8NoBom)
if (Test-Path (Split-Path -Parent $ServerUpdatePath)) {
    [System.IO.File]::WriteAllText($ServerUpdatePath, $UpdateJsonText, $Utf8NoBom)
}

$Token = Login-OpenList
Ensure-OpenListFolder $Token (Join-RemotePath $RemoteRoot $Version)
Ensure-OpenListFolder $Token (Join-RemotePath $RemoteRoot "$Version/$InstallDir")
Ensure-OpenListFolder $Token (Join-RemotePath $RemoteRoot "$Version/$CodeDir")
Ensure-OpenListFolder $Token (Join-RemotePath $RemoteRoot "$Version/$ServerDir")
if (Test-Path $FontLocalDir) {
    Ensure-OpenListFolder $Token $FontRemoteRoot
    foreach ($RetiredFont in @(
        "MaShanZheng-Regular.ttf",
        "OFL-MaShanZheng.txt",
        "ZCOOLQingKeHuangYou-Regular.ttf",
        "OFL-ZCOOLQingKeHuangYou.txt"
    )) {
        Remove-OpenListFileIfPresent $Token (Join-RemotePath $FontRemoteRoot $RetiredFont)
    }
}
Upload-OpenListFile $Token $PublishedApk $ApkRemote
Upload-OpenListFile $Token $SourceZip $SourceRemote
if (Test-Path $ServerCloudLocal) {
    Upload-OpenListFile $Token $ServerCloudLocal $ServerCloudRemote
}
if (Test-Path $ServerDeployLocal) {
    Upload-OpenListFile $Token $ServerDeployLocal $ServerDeployRemote
}
if (Test-Path $FontLocalDir) {
    Get-ChildItem -LiteralPath $FontLocalDir -File | ForEach-Object {
        Upload-OpenListFile $Token $_.FullName (Join-RemotePath $FontRemoteRoot $_.Name)
    }
}
Upload-OpenListFile $Token $UpdateLocal $UpdateRemote

$TutorialLocal = Join-Path (Join-Path $ProjectRoot "server") $TutorialName
if (Test-Path $TutorialLocal) {
    $TutorialRemote = Join-RemotePath $RemoteRoot $TutorialName
    Upload-OpenListFile $Token $TutorialLocal $TutorialRemote
}

Start-Sleep -Seconds 3
Assert-RemoteContains $Token (Join-RemotePath $RemoteRoot "$Version/$InstallDir") (Split-Path -Leaf $PublishedApk)
Assert-RemoteContains $Token (Join-RemotePath $RemoteRoot "$Version/$CodeDir") (Split-Path -Leaf $SourceZip)
if (Test-Path $ServerCloudLocal) {
    Assert-RemoteContains $Token (Join-RemotePath $RemoteRoot "$Version/$ServerDir") (Split-Path -Leaf $ServerCloudLocal)
}
if (Test-Path $ServerDeployLocal) {
    Assert-RemoteContains $Token (Join-RemotePath $RemoteRoot "$Version/$ServerDir") (Split-Path -Leaf $ServerDeployLocal)
}
if (Test-Path $FontLocalDir) {
    Get-ChildItem -LiteralPath $FontLocalDir -File | ForEach-Object {
        Assert-RemoteContains $Token $FontRemoteRoot $_.Name
    }
}
Assert-RemoteContains $Token $RemoteRoot "update.json"
if (Test-Path $TutorialLocal) {
    Assert-RemoteContains $Token $RemoteRoot $TutorialName
}

Write-Host ""
Write-Host "Published eat record $Version"
Write-Host "APK URL: $ApkUrl"
Write-Host "OpenList update.json copy: $(Page-Url $UpdateRemote)"
if (Test-Path $TutorialLocal) {
    Write-Host "Tutorial: $(Page-Url (Join-RemotePath $RemoteRoot $TutorialName))"
}
Write-Host "App update endpoint should serve dist\update.json from: http://47.97.215.111/eat-record/update.json"
