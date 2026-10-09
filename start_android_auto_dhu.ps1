# Script khoi chay Android Auto Desktop Head Unit (DHU) cho SpeakDrive
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhuDir = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
$dhuExe = "$dhuDir\desktop-head-unit.exe"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SpeakDrive - Trình Giả Lập Màn Hình Xe Hơi Android Auto" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Kiem tra file DHU
if (-not (Test-Path $dhuExe)) {
    Write-Host "[LỖI] Không tìm thấy file desktop-head-unit.exe tại: $dhuExe" -ForegroundColor Red
    Read-Host "Nhấn Enter để thoát..."
    exit 1
}

# 2. Kiem tra ADB
if (-not (Test-Path $adbPath)) {
    Write-Host "[LỖI] Không tìm thấy adb.exe tại: $adbPath" -ForegroundColor Red
    Read-Host "Nhấn Enter để thoát..."
    exit 1
}

# 3. Kiem tra thiet bi ket noi
function Check-Devices {
    $devicesOutput = & $adbPath devices
    $lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices attached" }
    return $lines
}

$connected = Check-Devices
while (-not $connected) {
    Write-Host ""
    Write-Host "[CHÚ Ý] Chưa phát hiện thiết bị Android nào được kết nối!" -ForegroundColor Yellow
    Write-Host "Vui lòng thực hiện các bước sau:" -ForegroundColor White
    Write-Host " 1. Cắm cáp USB điện thoại với máy tính (đã bật Gỡ lỗi USB / USB Debugging)," -ForegroundColor White
    Write-Host "    HOẶC khởi động một máy ảo Android Emulator." -ForegroundColor White
    Write-Host " 2. Trên điện thoại Android:" -ForegroundColor White
    Write-Host "    - Mở Cài đặt -> Android Auto -> Chạm 10 lần vào 'Version' để bật Chế độ nhà phát triển." -ForegroundColor Gray
    Write-Host "    - Menu 3 chấm -> Developer settings -> Tích chọn 'Unknown sources'." -ForegroundColor Gray
    Write-Host "    - Menu 3 chấm -> Chọn 'Start head unit server'." -ForegroundColor Cyan
    Write-Host ""
    $choice = Read-Host "Cắm cáp xong, nhấn Enter để thử lại (hoặc gõ Q rồi Enter để thoát)"
    if ($choice -match "^[Qq]") {
        exit 0
    }
    $connected = Check-Devices
}

Write-Host ""
Write-Host "[OK] Đã phát hiện thiết bị:" -ForegroundColor Green
foreach ($d in $connected) {
    Write-Host "  -> $d" -ForegroundColor White
}

# 4. Chuyen tiep cong 5277
Write-Host ""
Write-Host "-> Đang chuyển tiếp cổng 5277 qua ADB..." -ForegroundColor Cyan
& $adbPath forward tcp:5277 tcp:5277
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Chuyển tiếp cổng tcp:5277 thành công!" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Không thể forward cổng 5277. Đang thử tiếp..." -ForegroundColor Yellow
}

# 5. Huong dan truoc khi khoi chay
Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Yellow
Write-Host " [QUAN TRỌNG KHI KẾT NỐI]:" -ForegroundColor Yellow
Write-Host " 1. Hãy kiểm tra màn hình ĐIỆN THOẠI." -ForegroundColor White
Write-Host "    Nếu điện thoại hiện màn hình chào mừng/thiết lập Android Auto," -ForegroundColor White
Write-Host "    hãy bấm 'Tiếp tục' / 'Đồng ý' và cấp đủ quyền trên điện thoại." -ForegroundColor White
Write-Host " 2. Khi điện thoại chấp nhận, cửa sổ ô tô sẽ hiển thị giao diện chính." -ForegroundColor White
Write-Host "----------------------------------------------------------" -ForegroundColor Yellow
Write-Host ""

Write-Host "-> Đang khởi chạy Android Auto Desktop Head Unit (Độ phân giải 720p)..." -ForegroundColor Green
Write-Host "   (Bạn có thể đóng cửa sổ giả lập xe hơi để kết thúc)" -ForegroundColor Gray
Write-Host ""

Push-Location $dhuDir
try {
    .\desktop-head-unit.exe --config=config/default_720p.ini
} finally {
    Pop-Location
}

# 6. Don dep sau khi dong DHU
Write-Host ""
Write-Host "-> Đang dọn dẹp kết nối ADB..." -ForegroundColor Cyan
& $adbPath forward --remove tcp:5277 2>$null
Write-Host "[XONG] Đã đóng trình giả lập Android Auto." -ForegroundColor Green
