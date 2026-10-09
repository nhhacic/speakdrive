@echo off
chcp 65001 >nul
title SpeakDrive - Android Auto Head Unit Emulator
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_android_auto_dhu.ps1"
pause
