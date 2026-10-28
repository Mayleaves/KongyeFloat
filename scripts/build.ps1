param([switch]$DebugBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $bundledJdk = 'D:\Program\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $bundledJdk) { $env:JAVA_HOME = $bundledJdk }
    else { throw 'Please set JAVA_HOME to JDK 17 or newer.' }
}
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android' }
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
if (-not $DebugBuild -and -not (Test-Path -LiteralPath 'signing.properties')) {
    if (Test-Path -LiteralPath 'signing\kongye-release.jks') { throw 'Existing signing key found. Restore signing.properties rather than replacing the key.' }
    New-Item -ItemType Directory -Force -Path 'signing' | Out-Null
    $localKeyPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:KONGYE_LOCAL_KEY_PASSWORD = $localKeyPassword
    try {
        & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore 'signing\kongye-release.jks' -storetype JKS -alias kongye -keyalg RSA -keysize 3072 -validity 36500 -storepass:env KONGYE_LOCAL_KEY_PASSWORD -keypass:env KONGYE_LOCAL_KEY_PASSWORD -dname 'CN=Kongye Float, OU=Android, O=Kongye, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot generate local signing key.' }
        @("storeFile=signing/kongye-release.jks", "storePassword=$localKeyPassword", 'keyAlias=kongye', "keyPassword=$localKeyPassword") | Set-Content -LiteralPath 'signing.properties' -Encoding ascii
    } finally { Remove-Item Env:KONGYE_LOCAL_KEY_PASSWORD -ErrorAction SilentlyContinue }
}
$variant = if ($DebugBuild) { 'Debug' } else { 'Release' }
& '.\gradlew.bat' ":app:assemble$variant" ":app:lint$variant" --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build or Lint failed.' }
New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
$versionMatch = [regex]::Match((Get-Content -LiteralPath 'app\build.gradle.kts' -Raw), 'versionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) { throw 'Cannot read application version.' }
$appVersion = $versionMatch.Groups[1].Value
$name = if ($DebugBuild) { "空叶悬浮球-$appVersion-debug.apk" } else { "空叶悬浮球-$appVersion.apk" }
$apkSource = "app\build\outputs\apk\$($variant.ToLowerInvariant())\app-$($variant.ToLowerInvariant()).apk"
Copy-Item -LiteralPath $apkSource -Destination (Join-Path 'dist' $name) -Force
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar 'D:\Program\Android\Sdk\build-tools\36.0.0\lib\apksigner.jar' verify --verbose (Join-Path 'dist' $name)
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Get-FileHash -LiteralPath (Join-Path 'dist' $name) -Algorithm SHA256 | Format-List
