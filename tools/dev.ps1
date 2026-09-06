# Control the dev RuneLite client. Copied into the Windows dev folder and
# invoked from WSL by dev.sh; the jar lives next to this script.
param(
    [ValidateSet('start', 'stop', 'restart', 'status')][string]$Action = 'restart',
    [string]$JarName = 'arceuus-rc-helper-dev.jar'
)

$dir  = $PSScriptRoot
$jar  = Join-Path $dir $JarName
$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\java.exe'
$out  = Join-Path $dir 'dev-client.log'
$err  = Join-Path $dir 'dev-client.err.log'

function Get-DevProc {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*$JarName*" }
}

function Stop-Dev {
    $p = @(Get-DevProc)
    if ($p.Count -eq 0) { Write-Host 'not running'; return }
    foreach ($proc in $p) { Stop-Process -Id $proc.ProcessId -Force }
    Write-Host "stopped pid(s): $($p.ProcessId -join ',')"
}

function Start-Dev {
    if (@(Get-DevProc).Count -gt 0) { Write-Host 'already running'; return }
    Remove-Item $out, $err -ErrorAction SilentlyContinue
    $javaArgs = @('-ea', '-Xmx768m', '-Xss2m', '-jar', "`"$jar`"", '--developer-mode')
    $p = Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $dir `
        -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host "started pid $($p.Id)"
}

switch ($Action) {
    'start'   { Start-Dev }
    'stop'    { Stop-Dev }
    'restart' { Stop-Dev; Start-Sleep -Milliseconds 800; Start-Dev }
    'status'  {
        $p = @(Get-DevProc)
        if ($p.Count -gt 0) { Write-Host "running pid(s): $($p.ProcessId -join ',')" }
        else { Write-Host 'not running' }
    }
}
