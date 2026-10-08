Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

pythonwPath = "pythonw.exe"
If fso.FileExists("C:\Python314\pythonw.exe") Then
    pythonwPath = "C:\Python314\pythonw.exe"
ElseIf fso.FileExists("C:\Users\hoang\AppData\Local\Programs\Python\Python312\pythonw.exe") Then
    pythonwPath = "C:\Users\hoang\AppData\Local\Programs\Python\Python312\pythonw.exe"
End If

scriptPath = "d:\@Vibe_code_projects\English Speaking App\scripts\sync_receiver.py"
WshShell.Run """" & pythonwPath & """ """ & scriptPath & """", 0, False
