@echo off
echo MODをビルドします...
call gradlew.bat build
if %ERRORLEVEL% equ 0 (
    echo.
    echo 成功！
    echo JARファイル: build\libs\auto-name-tag-1.0.0.jar
    echo .minecraft\mods\ フォルダに入れてください
) else (
    echo ビルドに失敗しました
)
pause
