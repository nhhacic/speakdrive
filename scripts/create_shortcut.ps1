$WshShell = New-Object -ComObject WScript.Shell
$DesktopPath = [System.Environment]::GetFolderPath('Desktop')
$StartupPath = [System.Environment]::GetFolderPath('Startup')

# 1. Sync to Linux Desktop Shortcut
$Shortcut1 = $WshShell.CreateShortcut([System.IO.Path]::Combine($DesktopPath, 'Sync SpeakDrive to Linux.lnk'))
$Shortcut1.TargetPath = 'd:\@Vibe_code_projects\English Speaking App\sync_to_linux.bat'
$Shortcut1.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut1.Description = 'Push Code and Conversations to Linux'
$Shortcut1.Save()

# 2. Pull from Linux Desktop Shortcut
$Shortcut2 = $WshShell.CreateShortcut([System.IO.Path]::Combine($DesktopPath, 'Pull SpeakDrive from Linux.lnk'))
$Shortcut2.TargetPath = 'd:\@Vibe_code_projects\English Speaking App\pull_from_linux.bat'
$Shortcut2.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut2.Description = 'Pull Code and Conversations from Linux'
$Shortcut2.Save()

# 3. Auto-start Sync Receiver in Windows Startup
$Shortcut3 = $WshShell.CreateShortcut([System.IO.Path]::Combine($StartupPath, 'SpeakDrive_SyncReceiver.lnk'))
$Shortcut3.TargetPath = 'wscript.exe'
$Shortcut3.Arguments = '"d:\@Vibe_code_projects\English Speaking App\scripts\launch_receiver.vbs"'
$Shortcut3.WorkingDirectory = 'd:\@Vibe_code_projects\English Speaking App'
$Shortcut3.Description = 'SpeakDrive Background Sync Receiver'
$Shortcut3.Save()

Write-Host "Success: Desktop shortcuts and Startup shortcut created!"
