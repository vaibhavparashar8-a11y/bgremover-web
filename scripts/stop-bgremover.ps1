# Fully stops BGRemover: kills the process trees listening on the app ports,
# then closes the spawned console windows. Targets only port owners and
# BGRemover-titled windows so unrelated java/python processes are untouched.

$ports = @(8000, 8080)
$stopped = @()

foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in ($conns | Select-Object -Unique OwningProcess)) {
        $procId = $conn.OwningProcess
        if ($procId -gt 0) {
            $name = (Get-Process -Id $procId -ErrorAction SilentlyContinue).ProcessName
            # /T kills the whole tree (cmd -> mvn -> java / cmd -> python)
            taskkill /PID $procId /T /F 2>$null | Out-Null
            $stopped += "port ${port}: $name (pid $procId)"
        }
    }
}

# Close leftover console windows opened by the launcher/start scripts
taskkill /FI "WINDOWTITLE eq BGRemover inference*" /T /F 2>$null | Out-Null
taskkill /FI "WINDOWTITLE eq BGRemover backend*" /T /F 2>$null | Out-Null

if ($stopped.Count -gt 0) {
    Write-Host "BGRemover stopped:" -ForegroundColor Green
    $stopped | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "BGRemover was not running (ports 8000/8080 already free)." -ForegroundColor Yellow
}
Start-Sleep -Seconds 2
