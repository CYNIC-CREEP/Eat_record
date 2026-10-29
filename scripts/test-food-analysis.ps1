$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $PSScriptRoot
$TestDir=Join-Path $Root 'build\food-tests'
New-Item $TestDir -ItemType Directory -Force | Out-Null
$JsonJar=Join-Path $TestDir 'json-20250517.jar'
if (-not (Test-Path $JsonJar)) { Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/json/json/20250517/json-20250517.jar' -OutFile $JsonJar }
$Platform=Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\platforms" -Directory -Filter android-* | Sort-Object Name -Descending | Select-Object -First 1
$AndroidJar=Join-Path $Platform.FullName 'android.jar'
$Cp="$JsonJar;$AndroidJar"
& javac -encoding UTF-8 --release 8 -cp $Cp -d $TestDir `
    (Join-Path $Root 'src\com\eatrecord\app\FoodAnalysisResult.java') `
    (Join-Path $Root 'src\com\eatrecord\app\NutritionStore.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodAnalysisClient.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodRequestCompatibility.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodJobPriority.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodSavedPhotoPolicy.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodRecordText.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodNameHint.java') `
    (Join-Path $Root 'src\com\eatrecord\app\DayFoodEntries.java') `
    (Join-Path $Root 'src\com\eatrecord\app\NotebookMediaEntries.java') `
    (Join-Path $Root 'src\com\eatrecord\app\HomeCalendarDates.java') `
    (Join-Path $Root 'src\com\eatrecord\app\HomeDayMotion.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodRecognitionPolicy.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ApiCredentialPolicy.java') `
    (Join-Path $Root 'tests\ApiCredentialPolicyTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\RetiredRecognitionCache.java') `
    (Join-Path $Root 'tests\RetiredRecognitionCacheTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ReminderTimeInput.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ManualMealText.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ManualMealPrompts.java') `
    (Join-Path $Root 'src\com\eatrecord\app\CutoutViewport.java') `
    (Join-Path $Root 'src\com\eatrecord\app\FoodImageGeometry.java') `
    (Join-Path $Root 'src\com\eatrecord\app\AppearanceMode.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ApiVisionPolicy.java') `
    (Join-Path $Root 'src\com\eatrecord\app\AppDownloadClient.java') `
    (Join-Path $Root 'src\com\eatrecord\app\UiTokens.java') `
    (Join-Path $Root 'tests\ThemeTokensTest.java') `
    (Join-Path $Root 'tests\AppDownloadClientTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ApiModelPresentation.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ReleaseNotes.java') `
    (Join-Path $Root 'tests\ReleaseNotesModelTest.java') `
    (Join-Path $Root 'tests\FoodAnalysisTest.java') `
    (Join-Path $Root 'tests\FoodSchedulingTest.java') `
    (Join-Path $Root 'tests\NotebookMediaTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\NotebookPageMotion.java') `
    (Join-Path $Root 'tests\NotebookPageMotionTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\RootPageMotion.java') `
    (Join-Path $Root 'tests\RootPageMotionTest.java') `
    (Join-Path $Root 'tests\FoodRecordTextTest.java') `
    (Join-Path $Root 'tests\HomeCalendarDatesTest.java') `
    (Join-Path $Root 'tests\HomeDayMotionTest.java') `
    (Join-Path $Root 'tests\FoodRecognitionPolicyTest.java') `
    (Join-Path $Root 'tests\ReminderTimeInputTest.java') `
    (Join-Path $Root 'tests\ManualMealTextTest.java') `
    (Join-Path $Root 'tests\ManualMealPromptsTest.java') `
    (Join-Path $Root 'tests\CutoutViewportTest.java') `
    (Join-Path $Root 'tests\FoodImageGeometryTest.java') `
    (Join-Path $Root 'tests\ApiVisionPolicyTest.java') `
    (Join-Path $Root 'src\com\eatrecord\app\ProfilePrivacy.java') `
    (Join-Path $Root 'src\com\eatrecord\app\PrivateProfileStore.java') `
    (Join-Path $Root 'src\com\eatrecord\app\BackupArchive.java') `
    (Join-Path $Root 'src\com\eatrecord\app\BackupFileTransaction.java') `
    (Join-Path $Root 'tests\ProfilePrivacyTest.java') `
    (Join-Path $Root 'tests\BackupReliabilityTest.java')
if($LASTEXITCODE -ne 0){throw 'Food tests compilation failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.FoodAnalysisTest
if($LASTEXITCODE -ne 0){throw 'Food contract tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.FoodSchedulingTest
if($LASTEXITCODE -ne 0){throw 'Food scheduling policy tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ApiVisionPolicyTest
if($LASTEXITCODE -ne 0){throw 'API vision capability policy tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.AppDownloadClientTest
if($LASTEXITCODE -ne 0){throw 'Distribution authentication tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ThemeTokensTest
if($LASTEXITCODE -ne 0){throw 'Theme palette checks failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ReleaseNotesModelTest (Join-Path $Root 'AndroidManifest.xml')
if($LASTEXITCODE -ne 0){throw 'Release note/model presentation tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.NotebookMediaTest
if($LASTEXITCODE -ne 0){throw 'Notebook media projection tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.FoodRecordTextTest
if($LASTEXITCODE -ne 0){throw 'Notebook and estimate display tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.HomeCalendarDatesTest
if($LASTEXITCODE -ne 0){throw 'Home calendar date tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ReminderTimeInputTest
if($LASTEXITCODE -ne 0){throw 'Reminder time parser tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ManualMealTextTest
if($LASTEXITCODE -ne 0){throw 'Manual meal text tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.ManualMealPromptsTest
if($LASTEXITCODE -ne 0){throw 'Manual meal prompt policy tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.CutoutViewportTest
if($LASTEXITCODE -ne 0){throw 'Cutout viewport geometry tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.FoodImageGeometryTest
if($LASTEXITCODE -ne 0){throw 'Food image hit geometry tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.HomeDayMotionTest
if($LASTEXITCODE -ne 0){throw 'Linked day motion tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.FoodRecognitionPolicyTest
if($LASTEXITCODE -ne 0){throw 'Recognition source policy tests failed'}
& java -cp "$TestDir;$Cp" com.eatrecord.app.NotebookPageMotionTest
if($LASTEXITCODE -ne 0){throw 'Notebook continuous-motion sequence tests failed'}

& java -cp "$TestDir;$Cp" com.eatrecord.app.RootPageMotionTest
if($LASTEXITCODE -ne 0){throw 'Root-page continuous navigation tests failed'}

& java -cp "$TestDir;$Cp" com.eatrecord.app.RetiredRecognitionCacheTest
if($LASTEXITCODE -ne 0){throw 'Retired cache cleanup scope tests failed'}

& java -cp "$TestDir;$Cp" com.eatrecord.app.ApiCredentialPolicyTest
if($LASTEXITCODE -ne 0){throw 'API credential policy tests failed'}

& java -cp "$TestDir;$Cp" com.eatrecord.app.ProfilePrivacyTest
if($LASTEXITCODE -ne 0){throw 'Profile privacy/migration tests failed'}
& java -Xmx64m -cp "$TestDir;$Cp" com.eatrecord.app.BackupReliabilityTest
if($LASTEXITCODE -ne 0){throw 'Backup transaction/streaming tests failed'}
