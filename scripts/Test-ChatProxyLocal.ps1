param([Parameter(Mandatory)][string]$WorkDirectory)

$ErrorActionPreference = 'Stop'
if (![IO.Path]::IsPathFullyQualified($WorkDirectory)) { throw 'An absolute verification directory is required.' }
$testDirectory = Join-Path $WorkDirectory ('launcher-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory | Out-Null
$launcher = Join-Path $PSScriptRoot 'Start-ChatProxyLocal.ps1'
$passed = 0
$environmentNames = @('CHAT_GATEWAY_SECRETBASE64', 'CHAT_CORE_IDENTITY_SECRETBASE64',
    'SPRING_PROFILES_ACTIVE', 'CHAT_GATEWAY_ENVIRONMENT', 'CHAT_CORE_IDENTITY_ENVIRONMENT',
    'SPRING_APPLICATION_JSON', 'CHAT_GATEWAY_SECRET_BASE64_FILE', 'CHAT_CORE_IDENTITY_SECRETBASE64_FILE',
    'SERVER_PORT', 'CHAT_GATEWAY_ENABLED', 'SPRING_JPA_HIBERNATE_DDLAUTO', 'SPRING_CONFIG_LOCATION',
    'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME',
    'BE_LAUNCH_TEST_OUTPUT', 'BE_LAUNCH_TEST_KEY_A', 'BE_LAUNCH_TEST_KEY_B', 'BE_LAUNCH_TEST_ROOT')
$previous = @{}
foreach ($name in $environmentNames) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, [NullString]::Value, 'Process')
}

function Write-Utf8([string]$Path, [string]$Value) {
    [IO.File]::WriteAllText($Path, $Value, [Text.UTF8Encoding]::new($false))
}

function New-SyntheticJar([string]$Path, [string[]]$AdditionalEntries = @(), [switch]$MissingCore) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $entries = @(
            'BOOT-INF/classes/jp/co/translacat/TranslacatApplication.class',
            'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/gateway/ChatGatewayConfiguration.class',
            'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/gateway/ChatGatewayDisabledSecurity.class')
        if (!$MissingCore) { $entries += 'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/core/ChatCoreIdentityController.class' }
        foreach ($entry in ($entries + $AdditionalEntries)) { $archive.CreateEntry($entry) | Out-Null }
    } finally { $archive.Dispose() }
}

function Assert-Launch([string]$Name, [hashtable]$Overrides = @{}, [switch]$Reject) {
    # 실행: 실제 런처의 검증 경로를 통과한다. Java 실행은 별도 한 건에서 테스트 전용 sink로 검증한다.
    $parameters = @{} + $script:common
    foreach ($key in $Overrides.Keys) { $parameters[$key] = $Overrides[$key] }
    $rejected = $false
    try { & $launcher @parameters -ValidateOnly | Out-Null } catch { $rejected = $true }

    # 검증: 실패 케이스를 PASS로 바꾸거나 실제 서버 검증으로 표시하지 않는다.
    if ($rejected -ne [bool]$Reject) { throw ('Launcher assertion failed: ' + $Name) }
    $script:passed++
}

try {
    # 준비: 실제 개발키 대신 CSPRNG 합성키와 무내용 JAR 구조 fixture를 사용한다.
    $keyA = Join-Path $testDirectory 'synthetic-a'
    $keyB = Join-Path $testDirectory 'synthetic-b'
    Write-Utf8 $keyA ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))
    Write-Utf8 $keyB ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)))
    $jar = Join-Path $testDirectory 'current.jar'
    New-SyntheticJar $jar
    $public = Join-Path $testDirectory 'public.properties'
    Write-Utf8 $public "server.port=8080`nchat.gateway.environment=Development`nspring.jpa.hibernate.ddl-auto=none`n"
    $secrets = Join-Path $testDirectory 'secrets.properties'
    Write-Utf8 $secrets '# Synthetic local properties; no real credentials.'
    $script:common = @{
        JarPath = $jar; ConfigurationFile = $public; LocalSecretsConfigurationFile = $secrets
        BeToChatSigningKeyFile = $keyA; ChatToBeSigningKeyFile = $keyB
    }
    Assert-Launch 'valid inputs'
    $quietPublic = Join-Path $testDirectory 'quiet-public.properties'
    Write-Utf8 $quietPublic "logging.level.jdbc.sqlonly=OFF`nlogging.level.jdbc.sqltiming=OFF`nlogging.level.jdbc.audit=OFF`nlogging.level.jdbc.resultset=OFF`nlogging.level.jdbc.connection=OFF`nlogging.level.jdbc.resultsettable=OFF"
    Assert-Launch 'specific JDBC loggers can be disabled' @{ ConfigurationFile = $quietPublic }
    Assert-Launch 'same direction key rejected' @{ ChatToBeSigningKeyFile = $keyA } -Reject
    Assert-Launch 'missing JAR' @{ JarPath = (Join-Path $testDirectory 'absent.jar') } -Reject
    Assert-Launch 'relative key rejected' @{ BeToChatSigningKeyFile = 'synthetic-a' } -Reject
    Assert-Launch 'relative configuration rejected' @{ ConfigurationFile = 'public.properties' } -Reject
    Assert-Launch 'secret/public overlap rejected' @{ LocalSecretsConfigurationFile = $public } -Reject
    foreach ($origin in @('https://127.0.0.1:5079', 'http://remote.invalid', 'http://127.0.0.1:5079/path',
        'http://127.0.0.1:5079?key=x', 'http://user:pass@127.0.0.1:5079')) {
        Assert-Launch 'noncanonical origin rejected' @{ ChatBaseUrl = $origin } -Reject
    }
    foreach ($entry in @('BOOT-INF/classes/application-local.properties',
        'BOOT-INF/classes/jp/co/translacat/domain/chat/Old.class',
        'BOOT-INF/classes/jp/co/translacat/batch/chat/Old.class',
        'BOOT-INF/classes/jp/co/translacat/infrastructure/redis/Old.class',
        'BOOT-INF/lib/spring-data-redis-3.5.0.jar')) {
        $invalidJar = Join-Path $testDirectory ([Guid]::NewGuid().ToString('N') + '.jar')
        New-SyntheticJar $invalidJar @($entry)
        Assert-Launch 'legacy or secret artifact rejected' @{ JarPath = $invalidJar } -Reject
    }
    $missingCore = Join-Path $testDirectory 'missing-core.jar'
    New-SyntheticJar $missingCore -MissingCore
    Assert-Launch 'missing Core rejected' @{ JarPath = $missingCore } -Reject

    # 실행/검증: 손상·공백·BOM·개행·길이와 중복/충돌 입력을 각각 확인한다.
    $invalidKey = Join-Path $testDirectory 'invalid-key'
    foreach ($value in @('', 'not-base64', ([char]0xfeff + [IO.File]::ReadAllText($keyA)),
        ([IO.File]::ReadAllText($keyA) + "`n"), (' ' + [IO.File]::ReadAllText($keyA)),
        [Convert]::ToBase64String([byte[]]::new(31)), [Convert]::ToBase64String([byte[]]::new(129)))) {
        Write-Utf8 $invalidKey $value
        Assert-Launch 'invalid secret format rejected' @{ BeToChatSigningKeyFile = $invalidKey } -Reject
    }
    $invalidPublic = Join-Path $testDirectory 'invalid-public.properties'
    foreach ($value in @('chat.gateway.secret-base64=synthetic', "server.port=8080`nserver.port=8080",
        'chat.gateway.environment=Production', 'server.port=9090', 'spring.jpa.hibernate.ddl-auto=update',
        'spring.datasource.url=jdbc:mysql://localhost:3306/translacat?password=synthetic',
        'chat.gateway.enabled=false', 'logging.level.jdbc.sqlonly=INFO')) {
        Write-Utf8 $invalidPublic $value
        Assert-Launch 'invalid public configuration rejected' @{ ConfigurationFile = $invalidPublic } -Reject
    }
    foreach ($name in @('CHAT_GATEWAY_SECRETBASE64', 'CHAT_CORE_IDENTITY_SECRETBASE64',
        'SPRING_PROFILES_ACTIVE', 'CHAT_GATEWAY_ENVIRONMENT', 'CHAT_CORE_IDENTITY_ENVIRONMENT',
        'SPRING_APPLICATION_JSON', 'CHAT_GATEWAY_SECRET_BASE64_FILE', 'CHAT_CORE_IDENTITY_SECRETBASE64_FILE',
        'SERVER_PORT', 'CHAT_GATEWAY_ENABLED', 'SPRING_JPA_HIBERNATE_DDLAUTO', 'SPRING_CONFIG_LOCATION',
        'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME')) {
        [Environment]::SetEnvironmentVariable($name, 'conflicting-synthetic-input', 'Process')
        Assert-Launch 'environment conflict rejected' -Reject
        [Environment]::SetEnvironmentVariable($name, [NullString]::Value, 'Process')
    }
    Write-Utf8 $secrets ('chat.gateway.secret-base64=' + [IO.File]::ReadAllText($keyB))
    Assert-Launch 'properties key conflict rejected' -Reject
    Write-Utf8 $secrets ('chat.gateway.secret-base64=' + [IO.File]::ReadAllText($keyA))
    Assert-Launch 'matching existing source preserved'
    Write-Utf8 $secrets '# Synthetic local properties.'

    # 실행: sink는 실제 프로세스 env와 실행 인자만 검증하며 계정·DB·HTTP 서비스를 흉내 내지 않는다.
    $sink = Join-Path $testDirectory 'java-sink.ps1'
    Write-Utf8 $sink @'
param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
$ok = $env:CHAT_GATEWAY_SECRETBASE64 -ceq [IO.File]::ReadAllText($env:BE_LAUNCH_TEST_KEY_A) -and
    $env:CHAT_CORE_IDENTITY_SECRETBASE64 -ceq [IO.File]::ReadAllText($env:BE_LAUNCH_TEST_KEY_B) -and
    '--spring.jpa.hibernate.ddl-auto=none' -in $Arguments -and '--spring.sql.init.mode=never' -in $Arguments -and
    '--spring.profiles.active=local' -in $Arguments -and '--chat.gateway.environment=Development' -in $Arguments -and
    (Get-Location).Path -eq $env:BE_LAUNCH_TEST_ROOT -and '--logging.level.jdbc.sqlonly=OFF' -in $Arguments -and
    '--logging.level.jdbc.sqltiming=OFF' -in $Arguments -and '--logging.level.jdbc.audit=OFF' -in $Arguments -and
    '--logging.level.jdbc.resultset=OFF' -in $Arguments -and '--logging.level.jdbc.connection=OFF' -in $Arguments -and
    '--logging.level.jdbc.resultsettable=OFF' -in $Arguments
[IO.File]::WriteAllText($env:BE_LAUNCH_TEST_OUTPUT, [string]$ok)
$global:LASTEXITCODE = 0
'@
    $env:BE_LAUNCH_TEST_OUTPUT = Join-Path $testDirectory 'sink-result.txt'
    $env:BE_LAUNCH_TEST_KEY_A = $keyA
    $env:BE_LAUNCH_TEST_KEY_B = $keyB
    $env:BE_LAUNCH_TEST_ROOT = Split-Path -Parent $PSScriptRoot
    Push-Location $testDirectory
    try { & $launcher @script:common -Java $sink } finally { Pop-Location }
    if ([IO.File]::ReadAllText($env:BE_LAUNCH_TEST_OUTPUT) -ne 'True') { throw 'Child environment did not receive the expected inputs.' }
    $currentEnvironment = [Environment]::GetEnvironmentVariables('Process')
    if ($currentEnvironment.Contains('CHAT_GATEWAY_SECRETBASE64') -or
        $currentEnvironment.Contains('CHAT_CORE_IDENTITY_SECRETBASE64')) {
        throw 'Launcher did not remove its temporary process environment after exit.'
    }
    $passed += 2

    # 실행/검증: 원래 존재한 빈 환경변수는 삭제하지 않고 그대로 보존한다.
    [Environment]::SetEnvironmentVariable('CHAT_GATEWAY_SECRETBASE64', '', 'Process')
    [Environment]::SetEnvironmentVariable('CHAT_CORE_IDENTITY_SECRETBASE64', '', 'Process')
    & $launcher @script:common -Java $sink
    $currentEnvironment = [Environment]::GetEnvironmentVariables('Process')
    if (!$currentEnvironment.Contains('CHAT_GATEWAY_SECRETBASE64') -or
        !$currentEnvironment.Contains('CHAT_CORE_IDENTITY_SECRETBASE64') -or
        $currentEnvironment['CHAT_GATEWAY_SECRETBASE64'] -cne '' -or
        $currentEnvironment['CHAT_CORE_IDENTITY_SECRETBASE64'] -cne '') {
        throw 'Launcher did not preserve originally empty environment values.'
    }
    $passed++

    [pscustomobject]@{ passed = $passed; failed = 0; skipped = 0; scope = 'launcher validation and synthetic child-process inputs; no HTTP/DB' } |
        ConvertTo-Json
} finally {
    foreach ($name in $environmentNames) {
        $restoreValue = if ($null -eq $previous[$name]) { [NullString]::Value } else { $previous[$name] }
        [Environment]::SetEnvironmentVariable($name, $restoreValue, 'Process')
    }
}
