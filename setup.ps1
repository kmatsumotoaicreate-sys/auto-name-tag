# Animal Namer MOD - Setup Script
# PowerShellから実行: Right-click -> "PowerShellで実行"

$ErrorActionPreference = "Stop"
$BaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = "8.8"
$GradleZip = "$env:TEMP\gradle-$GradleVersion-bin.zip"
$GradleDir = "$env:TEMP\gradle-$GradleVersion"
$WrapperJar = "$BaseDir\gradle\wrapper\gradle-wrapper.jar"

Write-Host "=== Animal Namer MOD Setup ===" -ForegroundColor Cyan

# Step 1: Download Gradle
if (-not (Test-Path "$GradleDir\bin\gradle.bat")) {
    Write-Host "[1/3] Gradle $GradleVersion をダウンロード中..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $GradleZip
    Write-Host "[2/3] 展開中..." -ForegroundColor Yellow
    Expand-Archive -Path $GradleZip -DestinationPath $env:TEMP -Force
} else {
    Write-Host "[1/3] Gradle は取得済みです" -ForegroundColor Green
    Write-Host "[2/3] スキップ" -ForegroundColor Green
}

# Step 2: Generate Wrapper
if (-not (Test-Path $WrapperJar)) {
    Write-Host "[3/3] Gradle Wrapper を生成中..." -ForegroundColor Yellow
    Set-Location $BaseDir
    & "$GradleDir\bin\gradle.bat" wrapper --gradle-version $GradleVersion --distribution-type bin
    if (-not (Test-Path $WrapperJar)) {
        Write-Host "エラー: Wrapper の生成に失敗しました" -ForegroundColor Red
        Read-Host "Enterで終了"
        exit 1
    }
} else {
    Write-Host "[3/3] Wrapper は既に存在します" -ForegroundColor Green
}

Write-Host ""
Write-Host "セットアップ完了！MODをビルドします..." -ForegroundColor Cyan

# Step 3: Build
Set-Location $BaseDir
& "$BaseDir\gradlew.bat" build

if ($LASTEXITCODE -ne 0) {
    Write-Host "ビルド失敗。上のエラーを確認してください。" -ForegroundColor Red
    Read-Host "Enterで終了"
    exit 1
}

Write-Host ""
Write-Host "=== ビルド成功！ ===" -ForegroundColor Green
Write-Host "生成ファイル: build\libs\animalnamer-1.0.0.jar" -ForegroundColor Green
Write-Host "このJARを .minecraft\mods\ フォルダに入れてください" -ForegroundColor Green
Write-Host ""
Read-Host "Enterで終了"
