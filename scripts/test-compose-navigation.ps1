param(
    [string]$Serial,
    [string]$OutputDir,
    [switch]$PlanOnly
)

$ErrorActionPreference = 'Stop'
$TaskRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputDir) { $OutputDir = Join-Path $TaskRoot 'build/v063b3/device' }
$TaskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$TaskPackage = 'com.eatrecord.app'

if ($PlanOnly) {
    @(
        'Resolve tab hit areas from Today and Me accessibility trees.'
        'Use their intersections for rapid Today/Footprints/Me reversal taps.'
        'Toggle the meal menu repeatedly, then leave Today during expansion.'
        'Check hidden action semantics after leaving and returning to Today.'
        'Capture screenshots and one bounded screen recording.'
    ) | Write-Output
    return
}

if (-not $Serial -or $Serial -notmatch '^[A-Za-z0-9._:-]+$') {
    throw 'Specify the serial of the intended ordinary-package test device.'
}
if (-not (Test-Path -LiteralPath $TaskAdb)) { throw 'Android platform-tools adb is missing.' }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$TaskDeviceList = & $TaskAdb devices -l
if (-not ($TaskDeviceList -match ('^' + [regex]::Escape($Serial) + '\s+device\b'))) {
    throw 'The selected device is not connected and authorized.'
}

function Invoke-TaskAdb([string[]]$Arguments) {
    $TaskPreviousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $TaskOutput = & $TaskAdb -s $Serial @Arguments 2>&1
        $TaskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $TaskPreviousErrorAction }
    if ($TaskExit -ne 0) { throw "adb failed for $($Arguments[0])" }
    return ($TaskOutput -join "`n")
}

function Assert-TaskForeground {
    $TaskActivities = Invoke-TaskAdb -Arguments @('shell', 'dumpsys', 'activity', 'activities')
    if ($TaskActivities -notmatch '(mResumedActivity|topResumedActivity).*com\.eatrecord\.app/') {
        throw 'Eat Record must be the foreground Activity for UI input.'
    }
}

$TaskToken = [guid]::NewGuid().ToString('N')
$TaskRemoteXml = "/sdcard/eatrecord-compose-$TaskToken.xml"
$TaskRemoteShot = "/sdcard/eatrecord-compose-$TaskToken.png"
$TaskRemoteMovie = "/sdcard/eatrecord-compose-$TaskToken.mp4"

function Read-TaskUi {
    for ($TaskAttempt = 0; $TaskAttempt -lt 4; $TaskAttempt++) {
        try {
            Assert-TaskForeground
            $TaskDump = Invoke-TaskAdb -Arguments @('shell', 'uiautomator', 'dump', $TaskRemoteXml)
            if ($TaskDump -notmatch 'dumped to:') { throw 'Accessibility tree is not idle yet.' }
            $TaskLocalXml = Join-Path $OutputDir 'window.xml'
            Invoke-TaskAdb -Arguments @('pull', $TaskRemoteXml, $TaskLocalXml) | Out-Null
            return [xml](Get-Content -LiteralPath $TaskLocalXml -Raw -Encoding utf8)
        } catch {
            if ($TaskAttempt -eq 3) { throw }
            Start-Sleep -Milliseconds 300
        }
    }
}

function Find-TaskButton($TaskXml, [string]$Label) {
    $TaskNodes = @($TaskXml.SelectNodes('//node') | Where-Object {
        $_.package -eq $TaskPackage -and $_.enabled -eq 'true' -and ($_.clickable -eq 'true' -or $_.selected -eq 'true') -and
        ($_.'content-desc' -eq $Label -or $_.text -eq $Label)
    })
    if ($TaskNodes.Count -ne 1) { throw "Expected one enabled button labelled $Label." }
    $TaskBounds = [regex]::Match($TaskNodes[0].bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $TaskBounds.Success) { throw 'Invalid accessibility bounds.' }
    return @([int]$TaskBounds.Groups[1].Value, [int]$TaskBounds.Groups[2].Value,
        [int]$TaskBounds.Groups[3].Value, [int]$TaskBounds.Groups[4].Value)
}

function Tap-TaskBounds([int[]]$Bounds) {
    Assert-TaskForeground
    $TaskX = [int](($Bounds[0] + $Bounds[2]) / 2)
    $TaskY = [int](($Bounds[1] + $Bounds[3]) / 2)
    Invoke-TaskAdb -Arguments @('shell', 'input', 'tap', "$TaskX", "$TaskY") | Out-Null
}

function Save-TaskScreenshot([string]$Name) {
    Assert-TaskForeground
    Invoke-TaskAdb -Arguments @('shell', 'screencap', '-p', $TaskRemoteShot) | Out-Null
    Invoke-TaskAdb -Arguments @('pull', $TaskRemoteShot, (Join-Path $OutputDir "$Name.png")) | Out-Null
}

function Assert-TaskActionsHidden($TaskXml) {
    $TaskActionLabels = @('添加餐食', '拍照或相册添加食物', '手写记录餐食')
    $TaskVisible = @($TaskXml.SelectNodes('//node') | Where-Object {
        $_.package -eq $TaskPackage -and $_.enabled -eq 'true' -and
        $_.'content-desc' -in $TaskActionLabels
    })
    if ($TaskVisible.Count) { throw 'A hidden meal action remains enabled outside Today.' }
}

function Assert-TaskSubmenuClosed($TaskXml) {
    $TaskVisible = @($TaskXml.SelectNodes('//node') | Where-Object {
        $_.package -eq $TaskPackage -and $_.enabled -eq 'true' -and
        $_.'content-desc' -in @('拍照或相册添加食物', '手写记录餐食')
    })
    if ($TaskVisible.Count) { throw 'A collapsed submenu action remains enabled.' }
}

$TaskUi = Read-TaskUi
$TaskEdits = @($TaskUi.SelectNodes('//node') | Where-Object {
    $_.package -eq $TaskPackage -and $_.class -match 'EditText' -and $_.text
})
if ($TaskEdits.Count) { throw 'Finish the current text edit before running the UI smoke.' }
Tap-TaskBounds (Find-TaskButton $TaskUi '今日')
Start-Sleep -Milliseconds 1100
$TaskTodayUi = Read-TaskUi
$TaskToday = @{}
foreach ($TaskLabel in @('今日', '足迹', '我')) { $TaskToday[$TaskLabel] = Find-TaskButton $TaskTodayUi $TaskLabel }
$TaskMain = Find-TaskButton $TaskTodayUi '添加餐食'
Save-TaskScreenshot 'today-collapsed'

Tap-TaskBounds $TaskToday['我']
Start-Sleep -Milliseconds 1100
$TaskOtherUi = Read-TaskUi
Assert-TaskActionsHidden $TaskOtherUi
Save-TaskScreenshot 'me-actions-hidden'
$TaskStable = @{}
foreach ($TaskLabel in @('今日', '足迹', '我')) {
    $TaskOther = Find-TaskButton $TaskOtherUi $TaskLabel
    $TaskFirst = $TaskToday[$TaskLabel]
    $TaskOverlap = @([math]::Max($TaskFirst[0], $TaskOther[0]), [math]::Max($TaskFirst[1], $TaskOther[1]),
        [math]::Min($TaskFirst[2], $TaskOther[2]), [math]::Min($TaskFirst[3], $TaskOther[3]))
    if ($TaskOverlap[2] - $TaskOverlap[0] -lt 8 -or $TaskOverlap[3] - $TaskOverlap[1] -lt 8) {
        throw 'Tab layouts have no safe common rapid-tap area.'
    }
    $TaskStable[$TaskLabel] = $TaskOverlap
}

$TaskRecorder = Start-Process -FilePath $TaskAdb -ArgumentList @('-s', $Serial, 'shell', 'screenrecord',
    '--time-limit', '30', $TaskRemoteMovie) -WindowStyle Hidden -PassThru
try {
    $TaskCommands = @()
    foreach ($TaskLabel in @('今日', '足迹', '我', '今日', '我', '足迹', '今日', '足迹', '今日')) {
        $TaskBounds = $TaskStable[$TaskLabel]
        $TaskCommands += "input tap $([int](($TaskBounds[0]+$TaskBounds[2])/2)) $([int](($TaskBounds[1]+$TaskBounds[3])/2)); sleep 0.055"
    }
    Assert-TaskForeground
    Invoke-TaskAdb -Arguments @('shell', ($TaskCommands -join '; ')) | Out-Null
    Start-Sleep -Milliseconds 900
    Save-TaskScreenshot 'rapid-reversal-today'
    Tap-TaskBounds $TaskMain
    Start-Sleep -Milliseconds 80
    Tap-TaskBounds $TaskStable['足迹']
    Start-Sleep -Milliseconds 900
    Assert-TaskActionsHidden (Read-TaskUi)
    Save-TaskScreenshot 'leave-during-menu'
    Tap-TaskBounds $TaskStable['今日']
    Start-Sleep -Milliseconds 900
    Assert-TaskSubmenuClosed (Read-TaskUi)
    $TaskX = [int](($TaskMain[0] + $TaskMain[2]) / 2)
    $TaskY = [int](($TaskMain[1] + $TaskMain[3]) / 2)
    Assert-TaskForeground
    Invoke-TaskAdb -Arguments @('shell', "input tap $TaskX $TaskY; sleep 0.06; input tap $TaskX $TaskY; sleep 0.06; input tap $TaskX $TaskY; sleep 0.06; input tap $TaskX $TaskY") | Out-Null
    Start-Sleep -Milliseconds 900
    Assert-TaskSubmenuClosed (Read-TaskUi)
    Save-TaskScreenshot 'menu-reversal-collapsed'
    Tap-TaskBounds $TaskMain
    Start-Sleep -Milliseconds 700
    $TaskExpanded = Read-TaskUi
    $TaskCapture = Find-TaskButton $TaskExpanded '拍照或相册添加食物'
    $TaskWrite = Find-TaskButton $TaskExpanded '手写记录餐食'
    if ($TaskCapture[3] -ge $TaskMain[1] -or $TaskWrite[3] -ge $TaskCapture[1]) {
        throw 'Expanded submenu circles have overlapping vertical hit areas.'
    }
    Save-TaskScreenshot 'today-expanded'
    Invoke-TaskAdb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK') | Out-Null
    Start-Sleep -Milliseconds 700
    Assert-TaskSubmenuClosed (Read-TaskUi)
}
finally {
    $TaskRecorder.WaitForExit(35000) | Out-Null
    if (-not $TaskRecorder.HasExited) { throw 'Recording is still active. Preserve the bounded remote recording for inspection.' }
    Invoke-TaskAdb -Arguments @('pull', $TaskRemoteMovie, (Join-Path $OutputDir 'interaction.mp4')) | Out-Null
    Invoke-TaskAdb -Arguments @('shell', 'rm', '-f', $TaskRemoteXml, $TaskRemoteShot, $TaskRemoteMovie) | Out-Null
}
Write-Output 'PASS ordinary UI tab/menu smoke. Inspect interaction.mp4 for visible continuity.'
