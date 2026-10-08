@echo off
chcp 65001 >nul
echo ========================================================
echo 📥 ĐANG KÉO TOÀN BỘ CODE VÀ PHIÊN LÀM VIỆC TỪ LINUX VỀ WINDOWS...
echo ========================================================
python "%~dp0scripts\sync_workspace.py" --direction from-linux
pause
