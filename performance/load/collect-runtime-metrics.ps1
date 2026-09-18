[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ResultDirectory,
    [Parameter(Mandatory)]
    [string]$AppContainer,
    [Parameter(Mandatory)]
    [string]$PostgresContainer,
    [int]$DurationSeconds = 180,
    [ValidateRange(1, 60)]
    [int]$IntervalSeconds = 10
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $ResultDirectory | Out-Null
$output = Join-Path $ResultDirectory 'runtime-metrics.csv'
'timestamp_utc,container,cpu,memory,network,block_io,db_connections' | Set-Content -Encoding utf8 $output

$deadline = (Get-Date).ToUniversalTime().AddSeconds($DurationSeconds)
while ((Get-Date).ToUniversalTime() -lt $deadline) {
    $timestamp = (Get-Date).ToUniversalTime().ToString('o')
    $connections = (& docker exec $PostgresContainer psql -U festa_perf -d festa_perf -tAc 'SELECT count(*) FROM pg_stat_activity' 2>$null).Trim()
    foreach ($line in (& docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}' $AppContainer $PostgresContainer)) {
        "$timestamp,$line,$connections" | Add-Content -Encoding utf8 $output
    }
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "Saved runtime metrics to $output"
