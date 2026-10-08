$WshShell = New-Object -ComObject WScript.Shell
$DesktopPath = [System.Environment]::GetFolderPath('Desktop')

# 1. Sync to Linux
$Shortcut1 = $WshShell.CreateShortcut([System.IO.Path]::Combine($DesktopPath, 'Sync SpeakDrive to Linux.lnk'))
$Shortcut1.TargetPath = 'd:\@Vibe_code_projects\English Speaking App\sync_to_linux.bat'
$Shortcut1.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut1.Description = 'Đẩy Code và Phiên làm việc SpeakDrive sang máy Linux'
$Shortcut1.Save()

# 2. Pull from Linux
$Shortcut2 = $WshShell.CreateShortcut([System.IO.Path]::Combine($DesktopPath, 'Pull SpeakDrive from Linux.lnk'))
$Shortcut2.TargetPath = 'd:\@Vibe_code_projects\English Speaking App\pull_from_linux.bat'
$Shortcut2.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut2.Description = 'Kéo Code và Phiên làm việc SpeakDrive từ máy Linux về Windows'
$Shortcut2.Save()

Write-Host "Cả 2 Desktop shortcuts đã được tạo thành công!"
