@echo off
chcp 65001 >nul
echo Đang dừng SpeakDrive Sync Receiver...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*sync_receiver.py*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
echo ✅ Sync Receiver đã dừng.
pause
