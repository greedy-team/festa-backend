[CmdletBinding()]
param(
    [string]$BaseUrl = $env:BASE_URL,
    [ValidateSet('smoke', 'baseline', 'normal', 'stress', 'saturation')]
    [string]$Stage = 'smoke',
    [ValidateSet('mixed', 'upcoming', 'recent', 'festivals', 'festival-detail', 'artists', 'artist-detail', 'search', 'host-detail')]
    [string]$Scenario = 'mixed',
    [ValidateSet('performance', 'smoke')]
    [string]$Fixture = 'performance',
    [ValidateSet('native', 'docker')]
    [string]$Runner = 'native',
    [string]$RunId,
    [string]$ImageSha = '',
    [string]$EnvironmentName = 'local',
    [int]$Rate = 0,
    [string]$Duration = '',
    [int]$PreAllocatedVUs = 0,
    [int]$MaxVUs = 0,
    [ValidateSet('', 'common_1', 'common_2', 'rare', 'space_normalized', 'english')]
    [string]$SearchBucket = '',
    [ValidateSet('', 'ALL', 'ARTIST', 'HOST', 'FESTIVAL')]
    [string]$SearchType = '',
    [string]$FestivalId = '',
    [string]$ArtistId = '',
    [string]$HostId = ''
)

$ErrorActionPreference = 'Stop'
if (-not $BaseUrl) {
    throw 'BASE_URL is required. Pass -BaseUrl or set BASE_URL explicitly.'
}
$uri = [Uri]$BaseUrl
if (-not $uri.IsAbsoluteUri) {
    throw 'BASE_URL must be an absolute URL.'
}
$normalizedHost = $uri.Host.TrimEnd('.').ToLowerInvariant()
if ($normalizedHost -in @('api.every-festa.com', 'dev-api.every-festa.com')) {
    throw 'Refusing to load test production or the shared development server. Use a dedicated local or temporary load-test stack.'
}

$loadRoot = $PSScriptRoot
$repositoryRoot = (Resolve-Path (Join-Path $loadRoot '..\..')).Path
$runId = if ($RunId) { $RunId } else { "$((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ'))-$Stage-$Scenario" }
$resultDirectory = Join-Path $loadRoot "results\$runId"
New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null

$gitSha = (git -C $repositoryRoot rev-parse HEAD).Trim()
$metadata = [ordered]@{
    schemaVersion = 1
    runId = $runId
    startedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    baseUrl = $BaseUrl.TrimEnd('/')
    stage = $Stage
    scenario = $Scenario
    fixture = $Fixture
    environment = $EnvironmentName
    gitSha = $gitSha
    imageSha = if ($ImageSha) { $ImageSha } else { $null }
    runner = $Runner
}
$metadata | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $resultDirectory 'run-metadata.json')

$environmentArguments = @(
    '-e', "BASE_URL=$($metadata.baseUrl)",
    '-e', "STAGE=$Stage",
    '-e', "SCENARIO=$Scenario",
    '-e', "FIXTURE=$Fixture",
    '-e', "RUN_ID=$runId",
    '-e', "GIT_SHA=$gitSha"
)
if ($ImageSha) {
    $environmentArguments += @('-e', "IMAGE_SHA=$ImageSha")
}
foreach ($setting in @{
        RATE = $Rate
        DURATION = $Duration
        PRE_ALLOCATED_VUS = $PreAllocatedVUs
        MAX_VUS = $MaxVUs
        SEARCH_BUCKET = $SearchBucket
        SEARCH_TYPE = $SearchType
        FESTIVAL_ID = $FestivalId
        ARTIST_ID = $ArtistId
        HOST_ID = $HostId
    }.GetEnumerator()) {
    if ($setting.Value -ne 0 -and $setting.Value -ne '') {
        $environmentArguments += @('-e', "$($setting.Key)=$($setting.Value)")
    }
}

if ($Runner -eq 'native') {
    if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
        throw 'k6 is not installed. Install k6 or rerun with -Runner docker.'
    }
    $env:RESULTS_DIR = $resultDirectory
    & k6 run @environmentArguments (Join-Path $loadRoot 'scenario.js')
} else {
    $containerResultDirectory = "/scripts/results/$runId"
    $environmentArguments += @('-e', "RESULTS_DIR=$containerResultDirectory")
    & docker run --rm --entrypoint sh -v "${resultDirectory}:/results" grafana/k6:0.54.0 -c 'test -w /results'
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        throw 'The Docker k6 user cannot write the result directory. Fix its ownership/permissions before running the load test.'
    }
    & docker run --rm -i --add-host host.docker.internal:host-gateway -v "${loadRoot}:/scripts" -w /scripts @environmentArguments grafana/k6:0.54.0 run /scripts/scenario.js
}

$exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
$metadata.completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
$metadata.exitCode = $exitCode
$metadata | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $resultDirectory 'run-metadata.json')
if ($exitCode -ne 0) {
    exit $exitCode
}

Write-Host "Saved run artifacts to $resultDirectory"
