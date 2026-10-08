$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut([System.IO.Path]::Combine([System.Environment]::GetFolderPath('Desktop'), 'Sync SpeakDrive to Linux.lnk'))
$Shortcut.TargetPath = 'd:\@Vibe_code_projects\English Speaking App\sync_to_linux.bat'
$Shortcut.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut.Description = 'Đồng bộ toàn bộ SpeakDrive sang máy Linux'
$Shortcut.Save()
Write-Host "Desktop shortcut created successfully!"
