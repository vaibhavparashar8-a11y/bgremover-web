@echo off
rem Creates/updates the two desktop shortcuts (machine-specific, so .lnk files
rem are not in the repo). Safe to re-run.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell;" ^
  "$desktop = $ws.SpecialFolders.Item('Desktop');" ^
  "$s = $ws.CreateShortcut(\"$desktop\BGRemover Start.lnk\");" ^
  "$s.TargetPath = 'E:\Projects\BGRemover\scripts\launch-bgremover.cmd';" ^
  "$s.WorkingDirectory = 'E:\Projects\BGRemover';" ^
  "$s.IconLocation = 'E:\Projects\BGRemover\scripts\icons\start.ico';" ^
  "$s.Description = 'Start BGRemover and open it in the browser';" ^
  "$s.WindowStyle = 7;" ^
  "$s.Save();" ^
  "$s = $ws.CreateShortcut(\"$desktop\BGRemover Stop.lnk\");" ^
  "$s.TargetPath = 'E:\Projects\BGRemover\scripts\stop-bgremover.cmd';" ^
  "$s.WorkingDirectory = 'E:\Projects\BGRemover';" ^
  "$s.IconLocation = 'E:\Projects\BGRemover\scripts\icons\stop.ico';" ^
  "$s.Description = 'Stop BGRemover completely';" ^
  "$s.WindowStyle = 7;" ^
  "$s.Save();" ^
  "Write-Host \"Shortcuts created on $desktop\""
pause
