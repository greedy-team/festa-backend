[CmdletBinding()]
param(
    [int]$WarmupRuns = 2,
    [int]$MeasuredRuns = 5
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $PSCommandPath
$results = Join-Path $here 'results'
New-Item -ItemType Directory -Force -Path $results | Out-Null

Push-Location $here
try {
    docker compose up -d --wait

    foreach ($run in 1..$WarmupRuns) {
        docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U festa_perf -d festa_perf -f /perf/01-baseline.sql *> $null
    }

    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    foreach ($run in 1..$MeasuredRuns) {
        $target = Join-Path $results "baseline-$stamp-run-$run.txt"
        docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U festa_perf -d festa_perf -f /perf/01-baseline.sql 2>&1 |
            Tee-Object -FilePath $target
    }
}
finally {
    Pop-Location
}
