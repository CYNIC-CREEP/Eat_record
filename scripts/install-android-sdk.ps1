param(
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    [string]$JdkBaseRoot = (Join-Path $env:LOCALAPPDATA "Programs\EclipseAdoptium")
)

$ErrorActionPreference = "Stop"

function Download-File($Url, $OutFile, $Label) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutFile) | Out-Null
    if (Test-Path $OutFile) {
        Remove-Item -LiteralPath $OutFile -Force
    }
    Write-Host "Downloading $Label..."
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        & $curl.Source `
            --fail `
            --location `
            --retry 8 `
            --retry-delay 6 `
            --retry-all-errors `
            --connect-timeout 40 `
            --output $OutFile `
            $Url
        if ($LASTEXITCODE -eq 0 -and (Test-Path $OutFile) -and ((Get-Item $OutFile).Length -gt 0)) {
            return
        }
        Write-Host "curl failed, falling back to Invoke-WebRequest..."
    }
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -TimeoutSec 1800
}

function Remove-DirectoryIfExists($Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
}

function Expand-ZipFile($ZipPath, $DestinationPath, $Label) {
    Remove-DirectoryIfExists $DestinationPath
    New-Item -ItemType Directory -Force -Path $DestinationPath | Out-Null
    Write-Host "Extracting $Label..."
    $tar = Get-Command tar.exe -ErrorAction SilentlyContinue
    if ($tar) {
        & $tar.Source -xf $ZipPath -C $DestinationPath
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Write-Host "tar.exe extraction failed, falling back to Expand-Archive..."
        Remove-DirectoryIfExists $DestinationPath
        New-Item -ItemType Directory -Force -Path $DestinationPath | Out-Null
    }
    Expand-Archive -Path $ZipPath -DestinationPath $DestinationPath -Force
}

function Parse-Version($Text) {
    try {
        $versionText = [string]$Text
        if ($versionText -match '^\d+$') {
            $versionText = "$versionText.0.0"
        }
        return [version]$versionText
    } catch {
        return [version]"0.0.0"
    }
}

function Latest-PackagePath($Repo, $Regex) {
    $nodes = $Repo.SelectNodes("//*[local-name()='remotePackage']")
    $packageMatches = @()
    foreach ($node in $nodes) {
        $path = $node.path
        if ($path -match $Regex) {
            $versionText = $Matches[1]
            $packageMatches += [pscustomobject]@{
                Path = $path
                Version = (Parse-Version $versionText)
            }
        }
    }
    if ($packageMatches.Count -eq 0) {
        throw "No SDK package matched: $Regex"
    }
    return ($packageMatches | Sort-Object Version -Descending | Select-Object -First 1).Path
}

function Latest-CommandLineToolsUrl($Repo) {
    $nodes = $Repo.SelectNodes("//*[local-name()='remotePackage']")
    $candidates = @()
    foreach ($node in $nodes) {
        if ($node.path -ne "cmdline-tools;latest") {
            continue
        }
        foreach ($archive in $node.SelectNodes(".//*[local-name()='archive']")) {
            $hostOs = $archive.SelectSingleNode(".//*[local-name()='host-os']")
            if ($hostOs -and $hostOs.InnerText -ne "windows") {
                continue
            }
            $url = $archive.SelectSingleNode(".//*[local-name()='url']")
            if ($url -and $url.InnerText -match "commandlinetools-win-.*_latest\.zip") {
                return "https://dl.google.com/android/repository/" + $url.InnerText
            }
        }
    }
    throw "Could not find latest Windows command line tools in Android repository metadata."
}

New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $JdkBaseRoot | Out-Null

$TempRoot = Join-Path $env:TEMP ("eat-record-android-sdk-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null

try {
    $AdoptiumInfoPath = Join-Path $TempRoot "adoptium-releases.json"
    Download-File `
        "https://api.adoptium.net/v3/info/available_releases" `
        $AdoptiumInfoPath `
        "Adoptium release metadata"
    $AdoptiumInfo = Get-Content $AdoptiumInfoPath -Raw | ConvertFrom-Json
    $JdkFeature = [int]$AdoptiumInfo.most_recent_feature_release
    if ($JdkFeature -le 0) {
        throw "Could not detect latest JDK feature release."
    }
    $JdkRoot = Join-Path $JdkBaseRoot ("jdk-" + $JdkFeature)

    if (-not (Test-Path (Join-Path $JdkRoot "bin\javac.exe"))) {
        $JdkZip = Join-Path $TempRoot ("jdk-" + $JdkFeature + ".zip")
        Download-File `
            "https://api.adoptium.net/v3/binary/latest/$JdkFeature/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
            $JdkZip `
            "JDK $JdkFeature"
        $JdkExtract = Join-Path $TempRoot "jdk"
        Expand-ZipFile $JdkZip $JdkExtract "JDK $JdkFeature"
        $ExtractedJdk = Get-ChildItem $JdkExtract -Directory | Select-Object -First 1
        if (-not $ExtractedJdk) {
            throw "JDK extraction failed."
        }
        Remove-DirectoryIfExists $JdkRoot
        Move-Item -LiteralPath $ExtractedJdk.FullName -Destination $JdkRoot
    }

    $RepoXmlPath = Join-Path $TempRoot "android-repository.xml"
    Download-File `
        "https://dl.google.com/android/repository/repository2-1.xml" `
        $RepoXmlPath `
        "Android SDK repository metadata"
    [xml]$Repo = Get-Content $RepoXmlPath -Raw

    $CmdlineLatest = Join-Path $SdkRoot "cmdline-tools\latest"
    if (-not (Test-Path (Join-Path $CmdlineLatest "bin\sdkmanager.bat"))) {
        $ToolsZip = Join-Path $TempRoot "android-commandlinetools.zip"
        Download-File `
            (Latest-CommandLineToolsUrl $Repo) `
            $ToolsZip `
            "latest Android command line tools"
        $ToolsExtract = Join-Path $TempRoot "cmdline"
        Expand-ZipFile $ToolsZip $ToolsExtract "Android command line tools"
        Remove-DirectoryIfExists $CmdlineLatest
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $CmdlineLatest) | Out-Null
        Move-Item -LiteralPath (Join-Path $ToolsExtract "cmdline-tools") -Destination $CmdlineLatest
    }

    $LatestPlatform = Latest-PackagePath $Repo '^platforms;android-(\d+)$'
    $LatestBuildTools = Latest-PackagePath $Repo '^build-tools;(.+)$'
    $LatestCmake = Latest-PackagePath $Repo '^cmake;(.+)$'
    $LatestNdk = Latest-PackagePath $Repo '^ndk;(.+)$'

    $env:JAVA_HOME = $JdkRoot
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:Path = (Join-Path $JdkRoot "bin") + ";" +
        (Join-Path $SdkRoot "cmdline-tools\latest\bin") + ";" +
        (Join-Path $SdkRoot "platform-tools") + ";" +
        $env:Path

    [Environment]::SetEnvironmentVariable("JAVA_HOME", $JdkRoot, "User")
    [Environment]::SetEnvironmentVariable("ANDROID_HOME", $SdkRoot, "User")
    [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SdkRoot, "User")

    $SdkManager = Join-Path $CmdlineLatest "bin\sdkmanager.bat"
    if (-not (Test-Path $SdkManager)) {
        throw "sdkmanager not found: $SdkManager"
    }

    Write-Host "Latest packages selected:"
    Write-Host "  JDK: $JdkFeature"
    Write-Host "  $LatestPlatform"
    Write-Host "  $LatestBuildTools"
    Write-Host "  $LatestCmake"
    Write-Host "  $LatestNdk"

    Write-Host "Accepting Android SDK licenses..."
    1..120 | ForEach-Object { "y" } | & $SdkManager --sdk_root=$SdkRoot --licenses

    Write-Host "Installing Android SDK packages..."
    & $SdkManager --sdk_root=$SdkRoot `
        "platform-tools" `
        $LatestPlatform `
        $LatestBuildTools `
        $LatestCmake `
        $LatestNdk

    Write-Host ""
    Write-Host "Installed:"
    & (Join-Path $JdkRoot "bin\javac.exe") -version
    $BuildToolsVersion = ($LatestBuildTools -replace '^build-tools;', '')
    & (Join-Path $SdkRoot ("build-tools\" + $BuildToolsVersion + "\aapt2.exe")) version
    Write-Host "ANDROID_SDK_ROOT=$SdkRoot"
    Write-Host "JAVA_HOME=$JdkRoot"
}
finally {
    Remove-DirectoryIfExists $TempRoot
}



