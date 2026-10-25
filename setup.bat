@echo off
chcp 65001 > nul
setlocal

set "BASE_DIR=%~dp0"
set "WRAPPER_JAR=%BASE_DIR%gradle\wrapper\gradle-wrapper.jar"
set "GRADLE_ZIP=%TEMP%\gradle-8.8-bin.zip"
set "GRADLE_DIR=%TEMP%\gradle-8.8"

echo --- Step 1: Download Gradle 8.8 ---
if exist "%GRADLE_DIR%\bin\gradle.bat" (
    echo Already downloaded.
    goto step2
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.8-bin.zip' -OutFile '%GRADLE_ZIP%'"

if not exist "%GRADLE_ZIP%" (
    echo DOWNLOAD FAILED. Check your internet connection.
    pause
    exit /b 1
)

echo --- Step 2: Extract Gradle ---
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%TEMP%' -Force"

:step2
echo --- Step 3: Generate Gradle Wrapper ---
if exist "%WRAPPER_JAR%" (
    echo Wrapper already exists.
    goto build
)

cd /d "%BASE_DIR%"
"%GRADLE_DIR%\bin\gradle.bat" wrapper --gradle-version 8.8 --distribution-type bin

if not exist "%WRAPPER_JAR%" (
    echo WRAPPER GENERATION FAILED.
    pause
    exit /b 1
)

:build
echo --- Step 4: Build MOD ---
cd /d "%BASE_DIR%"
call gradlew.bat build

if errorlevel 1 (
    echo BUILD FAILED.
    pause
    exit /b 1
)

echo.
echo BUILD SUCCESS!
echo Output: build\libs\animalnamer-1.0.0.jar
echo Copy this jar to your .minecraft\mods\ folder.
pause
