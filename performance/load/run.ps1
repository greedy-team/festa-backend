[CmdletBinding()]
param(
    [string]$BaseUrl = $env:BASE_URL,
    [ValidateSet('smoke', 'baseline', 'normal', 'stress', 'saturation')]
    [string]$Stage = 'smoke',
    [ValidateSet('mixed', 'upcoming', 'recent', 'festivals', 'festival-detail', 'artists', 'artist-detail', 'search', 'host-detail', 'fixture-manifest')]
    [string]$Scenario = 'mixed',
    [ValidateSet('performance', 'smoke')]
    [string]$Fixture = 'performance',
    [ValidateSet('native', 'docker')]
    [string]$Runner = 'native',
    [string]$RunId,
    [string]$ImageSha = '',
    [string]$EnvironmentName = 'local',
    [ValidateSet('', 'A1_READ_ONLY_LOAD_TEST')]
    [string]$A1ProductionLoadApproval = '',
    [switch]$ValidateOnly,
    [int]$Rate = 0,
    [string]$Duration = '',
    [int]$PreAllocatedVUs = 0,
    [int]$MaxVUs = 0,
    [ValidateSet('', 'common_1', 'common_2', 'rare', 'space_normalized', 'english')]
    [string]$SearchBucket = '',
    [ValidateSet('', 'ALL', 'ARTIST', 'HOST', 'FESTIVAL')]
    [string]$SearchType = '',
    [string]$FestivalHostId = '',
    [string]$FestivalQuery = '',
    [string]$FestivalPage = '',
    [string]$FestivalId = '',
    [string]$ArtistPage = '',
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
$isA1Production = $normalizedHost -eq 'api.every-festa.com'
if ($normalizedHost -eq 'dev-api.every-festa.com') {
    throw 'Refusing to load test the shared development server.'
}
if ($isA1Production) {
    if (-not $PSBoundParameters.ContainsKey('BaseUrl')) {
        throw 'A1 production load tests require -BaseUrl explicitly; BASE_URL alone is not accepted.'
    }
    if ($A1ProductionLoadApproval -ne 'A1_READ_ONLY_LOAD_TEST') {
        throw 'Refusing to load test production without A1_READ_ONLY_LOAD_TEST approval.'
    }
    if ($Scenario -ne 'mixed' -or $Stage -ne 'baseline') {
        throw 'A1 production load tests allow only the mixed scenario at the baseline stage.'
    }
    if ($Rate -notin @(1, 3, 5, 10)) {
        throw 'A1 production load rate must be one of 1, 3, 5, 10 RPS.'
    }
    if ($Duration -notmatch '^(\d+)([sm])$' -or [int]$Matches[1] -le 0) {
        throw 'A1 production load duration must use a positive whole-second or whole-minute value.'
    }
    $durationSeconds = [int]$Matches[1] * $(if ($Matches[2] -eq 'm') { 60 } else { 1 })
    if ($durationSeconds -gt 180) {
        throw 'A1 production load duration must not exceed 180 seconds.'
    }
    if ($PreAllocatedVUs -gt 10 -or $MaxVUs -gt 10) {
        throw 'A1 production load tests must not configure more than 10 VUs.'
    }
}
if ($ValidateOnly) {
    Write-Host "Validation passed for $($BaseUrl.TrimEnd('/')) (targetEnvironment=$(if ($isA1Production) { 'a1-production' } else { $EnvironmentName }), productionOptIn=$isA1Production)."
    exit 0
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
    targetEnvironment = if ($isA1Production) { 'a1-production' } else { $EnvironmentName }
    productionOptIn = $isA1Production
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
if ($isA1Production) {
    $environmentArguments += @('-e', "A1_PRODUCTION_LOAD_APPROVAL=$A1ProductionLoadApproval")
}
if ($ImageSha) {
    $environmentArguments += @('-e', "IMAGE_SHA=$ImageSha")
}
foreach ($setting in @{
        RATE = $Rate
        PRE_ALLOCATED_VUS = $PreAllocatedVUs
        MAX_VUS = $MaxVUs
    }.GetEnumerator()) {
    if ($setting.Value -ne 0) {
        $environmentArguments += @('-e', "$($setting.Key)=$($setting.Value)")
    }
}
foreach ($setting in @{
        DURATION = $Duration
        SEARCH_BUCKET = $SearchBucket
        SEARCH_TYPE = $SearchType
        FESTIVAL_HOST_ID = $FestivalHostId
        FESTIVAL_QUERY = $FestivalQuery
        FESTIVAL_PAGE = $FestivalPage
        FESTIVAL_ID = $FestivalId
        ARTIST_PAGE = $ArtistPage
        ARTIST_ID = $ArtistId
        HOST_ID = $HostId
    }.GetEnumerator()) {
    if ($setting.Value -ne '') {
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
