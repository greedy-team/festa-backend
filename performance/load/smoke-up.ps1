[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$loadRoot = $PSScriptRoot
$repositoryRoot = (Resolve-Path (Join-Path $loadRoot '..\..')).Path
$imageContext = Join-Path $loadRoot '.smoke-image'
$imageName = 'festa-load-smoke:local'

function Select-GradleJavaHome {
    $roots = @(
        $env:LOAD_TEST_JAVA_HOME,
        $env:JAVA_HOME,
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java')
    ) | Where-Object { $_ -and (Test-Path $_) }

    $homes = foreach ($root in $roots) {
        if (Test-Path (Join-Path $root 'bin\java.exe')) {
            $root
        }
        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
            Select-Object -ExpandProperty FullName
    }
    $candidates = foreach ($candidateHome in $homes | Select-Object -Unique) {
        $releaseFile = Join-Path $candidateHome 'release'
        $versionLine = if (Test-Path $releaseFile) {
            Get-Content $releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        }
        if ("$versionLine" -match '"(\d+)') {
            [PSCustomObject]@{ JavaHome = $candidateHome; Major = [int]$Matches[1] }
        }
    }
    $supported = $candidates | Where-Object { $_.Major -ge 17 }
    $selected = $supported | Where-Object { $_.Major -eq 21 } | Select-Object -First 1
    if (-not $selected) {
        $selected = $supported | Sort-Object Major -Descending | Select-Object -First 1
    }
    if (-not $selected) {
        throw 'A JDK 17+ is required for bootJar. Set LOAD_TEST_JAVA_HOME to a compatible JDK.'
    }
    return $selected.JavaHome
}

$env:JAVA_HOME = Select-GradleJavaHome
Push-Location $repositoryRoot
try {
    & .\gradlew.bat bootJar --no-daemon
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $jar = Get-ChildItem (Join-Path $repositoryRoot 'build\libs\*.jar') |
        Where-Object { $_.Name -notmatch 'plain' } |
        Select-Object -First 1
    if (-not $jar) { throw 'bootJar did not produce an executable JAR.' }

    New-Item -ItemType Directory -Force -Path $imageContext | Out-Null
    Copy-Item $jar.FullName (Join-Path $imageContext 'app.jar') -Force
    & docker build --tag $imageName --file (Join-Path $repositoryRoot 'Dockerfile') $imageContext
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}

$env:LOAD_SMOKE_APP_IMAGE = $imageName
& docker compose --project-name festa-load-smoke --file (Join-Path $loadRoot 'smoke-compose.yaml') up --detach --wait
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& docker compose --project-name festa-load-smoke --file (Join-Path $loadRoot 'smoke-compose.yaml') --profile seed run --rm seed
exit $(if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE })
