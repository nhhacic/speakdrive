# Script khoi chay Android Auto Desktop Head Unit (DHU) cho SpeakDrive
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhuDir = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
$dhuExe = "$dhuDir\desktop-head-unit.exe"
$knownWifiIps = @("192.168.1.46:5555", "192.168.0.55:5555")

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SpeakDrive - Trinh Gia Lap Man Hinh Xe Hoi Android Auto" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Kiem tra file DHU
if (-not (Test-Path $dhuExe)) {
    Write-Host "[LOI] Khong tim thay file desktop-head-unit.exe tai: $dhuExe" -ForegroundColor Red
    Read-Host "Nhan Enter de thoat..."
    exit 1
}

# 2. Kiem tra ADB
if (-not (Test-Path $adbPath)) {
    Write-Host "[LOI] Khong tim thay adb.exe tai: $adbPath" -ForegroundColor Red
    Read-Host "Nhan Enter de thoat..."
    exit 1
}

# 3. Kiem tra thiet bi ket noi
function Check-Devices {
    $devicesOutput = & $adbPath devices
    $lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices attached" }
    return $lines
}

$connected = Check-Devices
if (-not $connected) {
    foreach ($wifiIp in $knownWifiIps) {
        Write-Host "-> Dang thu tu dong ket noi khong day toi $wifiIp qua Wi-Fi..." -ForegroundColor Cyan
        & $adbPath connect $wifiIp | Out-Null
        Start-Sleep -Milliseconds 600
        $connected = Check-Devices
        if ($connected) { break }
    }
}

while (-not $connected) {
    Write-Host ""
    Write-Host "[CHU Y] Chua phat hien thiet bi Android nao duoc ket noi!" -ForegroundColor Yellow
    Write-Host "Vui long thuc hien mot trong cac cach sau:" -ForegroundColor White
    Write-Host " 1. Ket noi qua Wi-Fi: Dam bao dien thoai va may tinh cung mang Wi-Fi." -ForegroundColor White
    Write-Host " 2. Hoac cam cap USB noi dien thoai voi may tinh (da bat Go loi USB)." -ForegroundColor White
    Write-Host " 3. Tren dien thoai: Bat 'Start head unit server' trong cai dat Android Auto." -ForegroundColor White
    Write-Host ""
    $choice = Read-Host "Cam cap hoac bat Wi-Fi xong, nhan Enter de thu lai (hoac go Q roi Enter de thoat)"
    if ($choice -match "^[Qq]") {
        exit 0
    }
    foreach ($wifiIp in $knownWifiIps) {
        & $adbPath connect $wifiIp | Out-Null
        Start-Sleep -Milliseconds 500
        $connected = Check-Devices
        if ($connected) { break }
    }
}

Write-Host ""
Write-Host "[OK] Da phat hien thiet bi ket noi:" -ForegroundColor Green
$targetSerial = $null
foreach ($d in $connected) {
    Write-Host "  -> $d" -ForegroundColor White
    if (-not $targetSerial) {
        $targetSerial = ($d -split "`t")[0].Trim()
    }
}

# 4. Chuyen tiep cong 5277
Write-Host ""
Write-Host "-> Dang chuyen tiep cong 5277 qua ADB (Thiet bi: $targetSerial)..." -ForegroundColor Cyan
if ($targetSerial) {
    & $adbPath -s $targetSerial forward tcp:5277 tcp:5277
} else {
    & $adbPath forward tcp:5277 tcp:5277
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Chuyen tiep cong tcp:5277 thanh cong!" -ForegroundColor Green
} else {
    Write-Host "[CANH BAO] Khong the forward cong 5277. Dang thu tiep..." -ForegroundColor Yellow
}

# 5. Kiem tra Head Unit Server tren dien thoai
Write-Host ""
$serverRunning = $false
try {
    $portCheck = & $adbPath -s $targetSerial shell "netstat -an 2>/dev/null | grep 5277"
    if ($portCheck -match "LISTEN|5277") {
        $serverRunning = $true
    }
} catch {}

if (-not $serverRunning) {
    Write-Host "----------------------------------------------------------" -ForegroundColor Yellow
    Write-Host " [CHU Y]: Chua phat hien 'May chu bo phan dau xe' tren dien thoai!" -ForegroundColor Yellow
    Write-Host " -> Dang tu dong mo man hinh Cai dat Android Auto tren dien thoai..." -ForegroundColor Cyan
    & $adbPath -s $targetSerial shell "am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity" | Out-Null
    Write-Host ""
    Write-Host " Vui long thuc hien tren dien thoai:" -ForegroundColor White
    Write-Host " 1. Cham vao dau 3 cham o goc tren ben phai." -ForegroundColor White
    Write-Host " 2. Chon 'Bat dau may chu bo phan dau xe' (Start head unit server)." -ForegroundColor Cyan
    Write-Host " 3. Dam bao man hinh dien thoai dang mo khoa." -ForegroundColor White
    Write-Host "----------------------------------------------------------" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Sau khi da bam 'Bat dau may chu' tren dien thoai, nhan Enter de tiep tuc..."
} else {
    Write-Host "[OK] Da phat hien 'May chu bo phan dau xe' dang chay tren dien thoai!" -ForegroundColor Green
}

Write-Host ""
Write-Host "-> Dang khoi chay Android Auto Desktop Head Unit (Do phan giai 720p)..." -ForegroundColor Green
Write-Host "   (Ban co the dong cua so gia lap xe hoi de ket thuc)" -ForegroundColor Gray
Write-Host ""

Push-Location $dhuDir
try {
    .\desktop-head-unit.exe --config=config/default_720p.ini
} finally {
    Pop-Location
}

# 6. Don dep sau khi dong DHU
Write-Host ""
Write-Host "-> Dang don dep ket noi ADB..." -ForegroundColor Cyan
if ($targetSerial) {
    & $adbPath -s $targetSerial forward --remove tcp:5277 2>$null
} else {
    & $adbPath forward --remove tcp:5277 2>$null
}
Write-Host "[XONG] Da dong trinh gia lap Android Auto." -ForegroundColor Green
