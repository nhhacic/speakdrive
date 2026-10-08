@echo off
chcp 65001 >nul
echo Đang khởi động SpeakDrive Sync Receiver chạy ngầm...
wscript "%~dp0scripts\launch_receiver.vbs"
echo ✅ Sync Receiver đã khởi động thành công (cổng 49200)!
echo Đang lắng nghe thông báo đồng bộ từ máy Linux...
timeout /t 2 >nul
