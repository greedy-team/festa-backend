[CmdletBinding()]
param(
    [switch]$RemoveVolume
)

$env:LOAD_SMOKE_APP_IMAGE = 'festa-load-smoke:local'
$arguments = @('--project-name', 'festa-load-smoke', '--file', (Join-Path $PSScriptRoot 'smoke-compose.yaml'), 'down')
if ($RemoveVolume) {
    $arguments += '--volumes'
}
& docker compose @arguments
exit $(if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE })
