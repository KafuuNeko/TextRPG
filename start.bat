@echo off
chcp 65001 >nul 2>&1
title TextRPG
echo.
echo   TextRPG Starting...
echo.
gradlew.bat run --console=plain
pause
