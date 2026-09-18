[CmdletBinding()]
param(
    [string]$BaseUrl = $env:BASE_URL,
    [ValidateSet('smoke', 'baseline', 'normal', 'stress', 'saturation', 'a1-manifest')]
    [string]$Stage = 'smoke',
    [ValidateSet('mixed', 'upcoming', 'recent', 'festivals', 'festival-detail', 'artists', 'artist-detail', 'search', 'host-detail', 'fixture-manifest')]
    [string]$Scenario = 'mixed',
    [ValidateSet('performance', 'smoke', 'a1')]
    [string]$Fixture = 'performance',
    [ValidateSet('native', 'docker')]
    [string]$Runner = 'native',
    [string]$RunId,
    [string]$ImageSha = '',
    [string]$EnvironmentName = 'local',
    [ValidateSet('', 'A1_READ_ONLY_LOAD_TEST')]
    [string]$A1ProductionLoadApproval = '',
    [ValidateSet('', 'manifest-validation', 'observation', 'step', 'deployment-experiment', 'deployment-recovery')]
    [string]$A1Profile = '',
    [string]$A1ManifestFile = '',
    [string]$A1ManifestValidationResult = '',
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
$a1ManifestPath = ''
$a1ManifestSha256 = ''
$isA1Observation = $false
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
    if ($Fixture -ne 'a1' -or -not $A1ManifestFile) {
        throw 'A1 production runs require -Fixture a1 and an explicit -A1ManifestFile.'
    }
    $manifestPath = Resolve-Path -LiteralPath $A1ManifestFile -ErrorAction Stop
    try {
        $a1Manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    } catch {
        throw "A1 manifest must be valid JSON: $($_.Exception.Message)"
    }
    foreach ($property in @('festivalDetailIds', 'artistDetailIds', 'hostDetailIds', 'festivalPages', 'artistPages', 'searchCorpus')) {
        if ($null -eq $a1Manifest.$property -or @($a1Manifest.$property).Count -eq 0) {
            throw "A1 manifest '$property' must be a non-empty array."
        }
    }
    if (@($a1Manifest.searchCorpus | Where-Object { -not $_.bucket -or -not $_.query -or -not $_.type }).Count -gt 0) {
        throw 'Each A1 manifest searchCorpus entry requires bucket, query, and type.'
    }
    $a1ManifestPath = $manifestPath.Path
    $a1ManifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
    if ($Scenario -eq 'fixture-manifest') {
        if (-not $A1Profile) { $A1Profile = 'manifest-validation' }
        if ($A1Profile -ne 'manifest-validation' -or $Stage -ne 'a1-manifest' -or $Rate -ne 0 -or $Duration -or $PreAllocatedVUs -ne 0 -or $MaxVUs -ne 0) {
            throw 'A1 manifest validation is fixed to the a1-manifest stage, one VU, one iteration, and no configurable rate or duration.'
        }
    } elseif ($A1Profile -in @('observation', 'deployment-recovery')) {
        $expectedObservationDuration = if ($A1Profile -eq 'deployment-recovery') { '5m' } else { '3m' }
        if ($Rate -ne 0 -or $Duration -ne $expectedObservationDuration -or $Scenario -ne 'mixed' -or $Stage -ne 'baseline' -or $PreAllocatedVUs -ne 0 -or $MaxVUs -ne 0) {
            throw "A1 $A1Profile requires 0 RPS for $expectedObservationDuration with no VU configuration."
        }
        $isA1Observation = $true
    } elseif ($Scenario -eq 'mixed' -and $Stage -eq 'baseline' -and $A1Profile -in @('step', 'deployment-experiment')) {
        if ($A1Profile -eq 'step' -and $Rate -notin @(1, 5, 10, 25, 50, 100, 150)) {
            throw 'A1 step rate must be one of 1, 5, 10, 25, 50, 100, 150 RPS.'
        }
        if ($A1Profile -eq 'deployment-experiment' -and $Rate -ne 50) {
            throw 'A1 deployment experiment requires exactly 50 RPS.'
        }
        if ($Duration -notmatch '^(\d+)([sm])$' -or [int]$Matches[1] -le 0) {
            throw 'A1 production load duration must use a positive whole-second or whole-minute value.'
        }
        $durationSeconds = [int]$Matches[1] * $(if ($Matches[2] -eq 'm') { 60 } else { 1 })
        if ($A1Profile -eq 'step' -and $durationSeconds -gt 180) {
            throw 'A1 step duration must not exceed 180 seconds.'
        }
        if ($A1Profile -eq 'deployment-experiment' -and $durationSeconds -ne 300) {
            throw 'A1 deployment experiment requires exactly 5 minutes.'
        }
        $expectedPreAllocatedVUs = $Rate
        $expectedMaxVUs = $Rate * 10
        if (($PreAllocatedVUs -ne 0 -and $PreAllocatedVUs -ne $expectedPreAllocatedVUs) -or ($MaxVUs -ne 0 -and $MaxVUs -ne $expectedMaxVUs)) {
            throw "A1 $A1Profile VUs must be preAllocated=$expectedPreAllocatedVUs, max=$expectedMaxVUs."
        }
        $PreAllocatedVUs = $expectedPreAllocatedVUs
        $MaxVUs = $expectedMaxVUs
        if (-not $A1ManifestValidationResult) {
            throw 'A1 mixed load requires -A1ManifestValidationResult from a successful A1 manifest validation.'
        }
        $validation = Get-Content -Raw -LiteralPath (Resolve-Path -LiteralPath $A1ManifestValidationResult -ErrorAction Stop) | ConvertFrom-Json
        if ($validation.exitCode -ne 0 -or $validation.baseUrl.TrimEnd('/') -ne $BaseUrl.TrimEnd('/') -or
            $validation.stage -ne 'a1-manifest' -or $validation.scenario -ne 'fixture-manifest' -or
            $validation.fixture -ne 'a1' -or $validation.manifestSha256 -ne $a1ManifestSha256) {
            throw 'A1 mixed load requires a successful matching A1 manifest validation result.'
        }
    } else {
        throw 'A1 production runs allow manifest validation, a 0-RPS observation, a step, or the deployment experiment.'
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
    a1Profile = if ($isA1Production) { $A1Profile } else { $null }
    rate = $Rate
    duration = $Duration
    preAllocatedVUs = $PreAllocatedVUs
    maxVUs = $MaxVUs
    manifestSha256 = if ($isA1Production) { $a1ManifestSha256 } else { $null }
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
    $a1ManifestFileForK6 = if ($Runner -eq 'docker') { '/a1-manifest.json' } else { $a1ManifestPath }
    $environmentArguments += @('-e', "A1_PRODUCTION_LOAD_APPROVAL=$A1ProductionLoadApproval", '-e', "A1_MANIFEST_FILE=$a1ManifestFileForK6")
    $environmentArguments += @('-e', "A1_PROFILE=$A1Profile")
    if ($Scenario -eq 'mixed') {
        $environmentArguments += @('-e', 'A1_MANIFEST_VALIDATION_RECEIPT=validated')
    }
}
if ($ImageSha) {
    $environmentArguments += @('-e', "IMAGE_SHA=$ImageSha")
}

if ($isA1Observation) {
    $observationSeconds = [int]$Duration.TrimEnd('m') * 60
    Start-Sleep -Seconds $observationSeconds
    $metadata.completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    $metadata.exitCode = 0
    $metadata | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $resultDirectory 'run-metadata.json')
    Write-Host "Saved observation metadata to $resultDirectory"
    exit 0
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
    $a1ManifestVolume = if ($isA1Production) { @('-v', "${a1ManifestPath}:/a1-manifest.json:ro") } else { @() }
    & docker run --rm -i --add-host host.docker.internal:host-gateway -v "${loadRoot}:/scripts" @a1ManifestVolume -w /scripts @environmentArguments grafana/k6:0.54.0 run /scripts/scenario.js
}

$exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
$metadata.completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
$metadata.exitCode = $exitCode
$metadata | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $resultDirectory 'run-metadata.json')
if ($exitCode -ne 0) {
    exit $exitCode
}

Write-Host "Saved run artifacts to $resultDirectory"
