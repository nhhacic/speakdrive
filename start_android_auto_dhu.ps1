# Script khoi chay Android Auto Desktop Head Unit (DHU) cho SpeakDrive
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhuDir = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
$dhuExe = "$dhuDir\desktop-head-unit.exe"
$defaultWifiIp = "192.168.0.55:5555"

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

# 3. Kiem tra thiet bi ket noi (tu dong thu Wi-Fi neu chua co USB)
function Check-Devices {
    $devicesOutput = & $adbPath devices
    $lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices attached" }
    return $lines
}

$connected = Check-Devices
if (-not $connected) {
    Write-Host "-> Đang thử tự động kết nối không dây tới $defaultWifiIp qua Wi-Fi..." -ForegroundColor Cyan
    & $adbPath connect $defaultWifiIp | Out-Null
    Start-Sleep -Milliseconds 800
    $connected = Check-Devices
}

while (-not $connected) {
    Write-Host ""
    Write-Host "[CHÚ Ý] Chưa phát hiện thiết bị Android nào được kết nối!" -ForegroundColor Yellow
    Write-Host "Vui lòng thực hiện một trong các cách sau:" -ForegroundColor White
    Write-Host " 1. Kết nối qua Wi-Fi: Đảm bảo điện thoại và máy tính cùng mạng Wi-Fi." -ForegroundColor White
    Write-Host " 2. Hoặc cắm cáp USB nối điện thoại với máy tính (đã bật Gỡ lỗi USB)." -ForegroundColor White
    Write-Host " 3. Trên điện thoại Android:" -ForegroundColor White
    Write-Host "    - Mở Cài đặt -> Android Auto -> Chạm 10 lần vào 'Version' để bật Chế độ nhà phát triển." -ForegroundColor Gray
    Write-Host "    - Menu 3 chấm -> Developer settings -> Tích chọn 'Unknown sources'." -ForegroundColor Gray
    Write-Host "    - Menu 3 chấm -> Chọn 'Start head unit server'." -ForegroundColor Cyan
    Write-Host ""
    $choice = Read-Host "Cắm cáp hoặc bật Wi-Fi xong, nhấn Enter để thử lại (hoặc gõ Q rồi Enter để thoát)"
    if ($choice -match "^[Qq]") {
        exit 0
    }
    # Thu ket noi lai Wi-Fi
    & $adbPath connect $defaultWifiIp | Out-Null
    Start-Sleep -Milliseconds 800
    $connected = Check-Devices
}

Write-Host ""
Write-Host "[OK] Đã phát hiện thiết bị kết nối:" -ForegroundColor Green
$targetSerial = $null
foreach ($d in $connected) {
    Write-Host "  -> $d" -ForegroundColor White
    if (-not $targetSerial) {
        $targetSerial = ($d -split "`t")[0].Trim()
    }
}

# 4. Chuyen tiep cong 5277
Write-Host ""
Write-Host "-> Đang chuyển tiếp cổng 5277 qua ADB (Thiết bị: $targetSerial)..." -ForegroundColor Cyan
if ($targetSerial) {
    & $adbPath -s $targetSerial forward tcp:5277 tcp:5277
} else {
    & $adbPath forward tcp:5277 tcp:5277
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Chuyển tiếp cổng tcp:5277 thành công!" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Không thể forward cổng 5277. Đang thử tiếp..." -ForegroundColor Yellow
}

# 5. Huong dan truoc khi khoi chay
Write-Host ""
Write-Host "----------------------------------------------------------" -ForegroundColor Yellow
Write-Host " [LƯU Ý]: Hãy đảm bảo trên điện thoại đã bấm" -ForegroundColor Yellow
Write-Host " 'Start head unit server' trong cài đặt Android Auto." -ForegroundColor Yellow
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
if ($targetSerial) {
    & $adbPath -s $targetSerial forward --remove tcp:5277 2>$null
} else {
    & $adbPath forward --remove tcp:5277 2>$null
}
Write-Host "[XONG] Đã đóng trình giả lập Android Auto." -ForegroundColor Green
