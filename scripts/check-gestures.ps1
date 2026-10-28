$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { 'D:\Program\Android\Android Studio\jbr\bin' }
$output = Join-Path $projectRoot '.tools\gesture-checks'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\GestureRules.java') (Join-Path $projectRoot 'tests\GestureRulesCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Gesture test compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output GestureRulesCheck
if ($LASTEXITCODE -ne 0) { throw 'Gesture checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\ThemeSchedule.java') (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\FrameStitcher.java') (Join-Path $projectRoot 'tests\NewFeaturesCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'New feature checks compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output NewFeaturesCheck
if ($LASTEXITCODE -ne 0) { throw 'New feature checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\CompositionLayout.java') (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\CropGeometry.java') (Join-Path $projectRoot 'tests\CaptureEditingCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Capture editing compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output CaptureEditingCheck
if ($LASTEXITCODE -ne 0) { throw 'Capture editing checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\QuickOrbitLayout.java') (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\GestureRules.java') (Join-Path $projectRoot 'tests\OrbitInteractionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Orbit compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output OrbitInteractionCheck
if ($LASTEXITCODE -ne 0) { throw 'Orbit checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\TextSelectionRange.java') (Join-Path $projectRoot 'tests\TextSelectionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Text selection compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output TextSelectionCheck
if ($LASTEXITCODE -ne 0) { throw 'Text selection checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\TextSelectionRange.java') (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\MultiTextSelection.java') (Join-Path $projectRoot 'tests\MultiSelectionCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Multi selection compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output MultiSelectionCheck
if ($LASTEXITCODE -ne 0) { throw 'Multi selection checks failed' }
& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\GestureRules.java') (Join-Path $projectRoot 'tests\EdgeGapCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Edge inset compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output EdgeGapCheck
if ($LASTEXITCODE -ne 0) { throw 'Edge inset checks failed' }

& (Join-Path $jdkBin 'javac.exe') -encoding UTF-8 -d $output (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\BrowserSearch.java') (Join-Path $projectRoot 'app\src\main\java\com\kongye\floatball\GestureRules.java') (Join-Path $projectRoot 'tests\V19Check.java')
if ($LASTEXITCODE -ne 0) { throw 'Search/edge compilation failed' }
& (Join-Path $jdkBin 'java.exe') -cp $output com.kongye.floatball.V19Check
if ($LASTEXITCODE -ne 0) { throw 'Search/edge checks failed' }
