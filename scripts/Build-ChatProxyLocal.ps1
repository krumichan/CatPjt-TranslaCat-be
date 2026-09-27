param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Test,
    [string[]]$TestFilter = @()
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$buildDirectory = Join-Path $repository 'build/chat-local'

# 설치된 JDK 21과 저장소 wrapper를 그대로 사용한다. 전역 환경이나 의존성 버전을 바꾸지 않는다.
if (!$JavaHome) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $JavaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
}
if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)) {
    throw 'An existing JDK 21 is required. Supply -JavaHome or JAVA_HOME.'
}
if ($TestFilter.Count -gt 0 -and !$Test) {
    throw 'TestFilter requires -Test.'
}
foreach ($filter in $TestFilter) {
    if ($filter -notmatch '^[A-Za-z0-9_.*$]+$') {
        throw 'TestFilter must contain only Java test class names or wildcards.'
    }
}

$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
Push-Location $repository
try {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $JavaHome, 'Process')
    $arguments = @('--no-daemon', '--console=plain', '-I', (Join-Path $PSScriptRoot 'chat-local.gradle'))
    $arguments += if ($TestFilter.Count -gt 0) { @('bootJar', 'test') } elseif ($Test) { @('build') } else { @('bootJar') }
    foreach ($filter in $TestFilter) { $arguments += @('--tests', $filter) }

    # 별도 build/chat-local 아래에서만 빌드한다. 서버·DB·Redis를 기동하거나 초기화하지 않는다.
    & (Join-Path $repository 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw 'BE Chat local build failed. Inspect the Gradle result; no service was started.' }

    $jars = @(Get-ChildItem -LiteralPath (Join-Path $buildDirectory 'libs') -File -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' })
    if ($jars.Count -ne 1) { throw 'Expected exactly one current Chat proxy boot JAR in build/chat-local/libs.' }
    Write-Output ('BUILT: ' + $jars[0].FullName)
} finally {
    Pop-Location
    # PowerShell/.NET에서 원래 없던 환경변수는 빈 문자열이 아니라 명시적 null로 제거한다.
    $restoreJavaHome = if ($null -eq $previousJavaHome) { [NullString]::Value } else { $previousJavaHome }
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $restoreJavaHome, 'Process')
}
