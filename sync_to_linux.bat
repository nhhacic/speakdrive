@echo off
chcp 65001 >nul
echo ========================================================
echo 🚀 ĐANG ĐỒNG BỘ TOÀN BỘ CODE VÀ PHIÊN LÀM VIỆC SANG LINUX...
echo ========================================================
python "%~dp0scripts\sync_workspace.py" --direction to-linux
pause
