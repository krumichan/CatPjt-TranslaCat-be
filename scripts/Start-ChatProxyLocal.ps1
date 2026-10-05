param(
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][string]$ConfigurationFile,
    [Parameter(Mandatory)][string]$LocalSecretsConfigurationFile,
    [Parameter(Mandatory)][string]$BeToChatSigningKeyFile,
    [Parameter(Mandatory)][string]$ChatToBeSigningKeyFile,
    [ValidateSet('Development')][string]$Environment = 'Development',
    [string]$ChatBaseUrl = 'http://127.0.0.1:5079',
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [string]$Java = 'java',
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

function Read-ServiceKey([string]$Path) {
    # 방향별 기존 파일을 읽기만 한다. 비밀값과 원본 예외는 진단에 포함하지 않는다.
    try {
        if (![IO.Path]::IsPathFullyQualified($Path)) { throw 'Invalid path' }
        $bytes = [IO.File]::ReadAllBytes($Path)
        if ($bytes.Length -lt 1 -or $bytes.Length -gt 16384) { throw 'Invalid size' }
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191) {
            throw 'Invalid encoding'
        }
        $value = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
        if ($value -match '\s' -or $value -match '[\x00-\x1f\x7f]') { throw 'Invalid whitespace' }
        $decoded = [Convert]::FromBase64String($value)
        if ($decoded.Length -lt 32 -or $decoded.Length -gt 128) { throw 'Invalid key length' }
        if ([Convert]::ToBase64String($decoded) -cne $value) { throw 'Invalid Base64 encoding' }
        return $value
    } catch {
        throw 'Development service key file must contain one valid Base64 key of 32 to 128 bytes.'
    }
}

# 실행 산출물의 실제 class를 검사한다. 옛 LL 검증 JAR이나 legacy writer를 포함한 JAR은 거부한다.
foreach ($path in @($JarPath, $ConfigurationFile, $LocalSecretsConfigurationFile)) {
    if (![IO.Path]::IsPathFullyQualified($path) -or !(Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Existing absolute JAR, public configuration and local secret configuration files are required.'
    }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($JarPath)
try {
    $names = @($archive.Entries | ForEach-Object { $_.FullName })
    if ('BOOT-INF/classes/application-local.properties' -in $names) {
        throw 'JAR embeds local secret configuration. Rebuild with external local configuration.'
    }
    $legacy = @($names | Where-Object {
        $_ -match '^BOOT-INF/classes/jp/co/translacat/(domain/chat/|batch/chat/|infrastructure/redis/|infrastructure/chat/(ai|translation)/)' -or
        $_ -match '^BOOT-INF/classes/jp/co/translacat/global/config/WebSocketConfig\.class$' -or
        $_ -match '^BOOT-INF/classes/jp/co/translacat/infrastructure/chat/gateway/(ChatLegacyComponentFilter|ChatGatewayPersistenceConfiguration)\.class$' -or
        $_ -match '^BOOT-INF/lib/spring-data-redis-'
    })
    if ($legacy.Count -ne 0) { throw 'Legacy Chat classes or Redis ownership remain in this JAR. Rebuild the proxy-only source.' }

    foreach ($required in @(
        'BOOT-INF/classes/jp/co/translacat/TranslacatApplication.class',
        'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/gateway/ChatGatewayConfiguration.class',
        'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/gateway/ChatGatewayDisabledSecurity.class',
        'BOOT-INF/classes/jp/co/translacat/infrastructure/chat/core/ChatCoreIdentityController.class'
    )) {
        if ($required -notin $names) { throw 'JAR does not contain the required current Chat proxy and Core implementation.' }
    }
} finally {
    $archive.Dispose()
}

# Development의 loopback upstream만 허용한다. 사용자 JWT와 LL 키는 기존 Spring local 공급 경로를 유지한다.
try { $target = [Uri]$ChatBaseUrl } catch { throw 'ChatBaseUrl must be a Development loopback HTTP origin.' }
if (!$target.IsAbsoluteUri -or $target.Scheme -ne 'http' -or
    $target.Host -notin @('127.0.0.1', 'localhost', '[::1]', '::1') -or
    $target.UserInfo -or $target.Query -or $target.Fragment -or $target.AbsolutePath -ne '/') {
    throw 'ChatBaseUrl must be a Development loopback HTTP origin.'
}
# 공개 파일은 실제 필요한 단일행 properties만 허용한다. escaped key/continuation/YAML로 비밀 검사를 우회하지 않는다.
if ([IO.Path]::GetExtension($ConfigurationFile) -ne '.properties' -or
    [IO.Path]::GetExtension($LocalSecretsConfigurationFile) -ne '.properties' -or
    $ConfigurationFile.Contains(',') -or $LocalSecretsConfigurationFile.Contains(',') -or
    [IO.Path]::GetFullPath($ConfigurationFile).Equals([IO.Path]::GetFullPath($LocalSecretsConfigurationFile),
        [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Separate public and secret .properties files without commas in their paths are required.'
}
$allowedProperties = @(
    'server.address', 'server.port', 'spring.jpa.hibernate.ddl-auto', 'spring.jpa.show-sql',
    'spring.datasource.url', 'spring.datasource.driver-class-name',
    'chat.gateway.enabled', 'chat.gateway.base-url', 'chat.gateway.environment',
    'chat.gateway.issuer', 'chat.gateway.audience', 'chat.gateway.service',
    'chat.gateway.timeout-seconds', 'chat.gateway.token-lifetime-seconds',
    'chat.core.identity.enabled', 'chat.core.identity.environment',
    'chat.core.identity.issuer', 'chat.core.identity.audience', 'chat.core.identity.service',
    'language-learning.url', 'language-learning.remote.enabled', 'language-learning.growth.enabled',
    'language-learning.internal-jwt.issuer', 'language-learning.internal-jwt.audience',
    'language-learning.internal-jwt.caller-service', 'language-learning.internal-jwt.ttl-seconds',
    'ai-server.url', 'external.google.proxy-url', 'external.use-proxy',
    'translacat.storage.type', 'translacat.storage.local.root-path', 'translacat.storage.local.public-base-url',
    'sudachi.dictionary.path', 'exchange-rate.frankfurter.base-url', 'translacat.batch.fixed-cost.enabled',
    'logging.level.root', 'logging.level.jdbc', 'logging.level.jp.co.translacat.global.logging',
    'logging.level.jdbc.sqlonly', 'logging.level.jdbc.sqltiming', 'logging.level.jdbc.audit',
    'logging.level.jdbc.resultset', 'logging.level.jdbc.connection', 'logging.level.jdbc.resultsettable',
    'cors.allowed-origin'
)
$configuration = [IO.File]::ReadAllText($ConfigurationFile)
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$publicValues = @{}
foreach ($line in $configuration -split '\r?\n') {
    if (!$line.Trim() -or $line.TrimStart().StartsWith('#')) { continue }
    if ($line.Contains('\') -or $line -notmatch '^([a-z][a-z0-9.-]*)=([^\r\n\x00-\x1f\x7f]*)$') {
        throw 'Public configuration requires canonical single-line key=value properties without escapes.'
    }
    $propertyName = $matches[1]
    $value = $matches[2]
    if ($propertyName -cnotin $allowedProperties -or !$seen.Add($propertyName)) {
        throw 'Public configuration contains an unsupported or duplicate key. Put secret inputs in the separate secret file.'
    }
    $publicValues[$propertyName] = $value

    # 전체 로컬 결합에서도 HTTP 의존성과 CORS는 loopback으로 제한한다.
    if ($propertyName -in @('language-learning.url', 'ai-server.url', 'external.google.proxy-url',
        'translacat.storage.local.public-base-url', 'exchange-rate.frankfurter.base-url', 'cors.allowed-origin')) {
        $urls = if ($propertyName -eq 'cors.allowed-origin') { $value.Split(',') } else { @($value) }
        foreach ($url in $urls) {
            $localUri = $null
            if (![Uri]::TryCreate($url.Trim(), [UriKind]::Absolute, [ref]$localUri) -or
                $localUri.Scheme -ne 'http' -or $localUri.Host -notin @('localhost', '127.0.0.1', '[::1]', '::1') -or
                $localUri.UserInfo -or $localUri.Query -or $localUri.Fragment) {
                throw 'Development HTTP dependencies and CORS origins must use loopback HTTP without credentials.'
            }
            if ($propertyName -in @('language-learning.url', 'ai-server.url', 'cors.allowed-origin') -and
                $localUri.AbsolutePath -ne '/') {
                throw 'Development service URLs and CORS values must be origins without paths.'
            }
        }
    }
    if ($propertyName -eq 'translacat.storage.type' -and $value -cne 'local') {
        throw 'Development storage must remain local.'
    }

    # JDBC URL에도 자격증명이나 임의 연결 옵션을 끼워 넣지 않는다. 실제 확인한 로컬 계정 catalog만 허용한다.
    if ($propertyName -eq 'spring.datasource.url') {
        if ($value -notmatch '^jdbc:(?:log4jdbc:)?mysql://(?:127\.0\.0\.1|localhost|\[::1\]):[0-9]+/translacat(?:\?([^#]*))?$') {
            throw 'Public datasource URL must identify the verified local translacat catalog without credentials.'
        }
        $query = $matches[1]
        foreach ($part in @($query -split '&' | Where-Object { $_ })) {
            if ($part -notmatch '^(?:serverTimezone|allowPublicKeyRetrieval|useSSL|characterEncoding|useUnicode|connectionTimeZone|forceConnectionTimeZoneToSession)=[A-Za-z0-9_+:/%.-]+$') {
                throw 'Public datasource URL contains an unsupported connection option.'
            }
        }
    }
}

# CLI 안전 기본값과 공개 파일의 선언이 다르면 조용히 덮어쓰지 않는다.
$fixedValues = @{
    'server.address' = '127.0.0.1'
    'server.port' = [string]$Port
    'spring.jpa.hibernate.ddl-auto' = 'none'
    'chat.gateway.enabled' = 'true'
    'chat.gateway.base-url' = $ChatBaseUrl.TrimEnd('/')
    'chat.gateway.environment' = $Environment
    'chat.core.identity.enabled' = 'true'
    'chat.core.identity.environment' = $Environment
}
foreach ($logger in @('sqlonly', 'sqltiming', 'audit', 'resultset', 'connection', 'resultsettable')) {
    # 기존 local의 구체적인 logger INFO가 부모 jdbc=OFF보다 우선할 수 있다.
    $fixedValues["logging.level.jdbc.$logger"] = 'OFF'
}
foreach ($name in $fixedValues.Keys) {
    if ($publicValues.ContainsKey($name) -and $publicValues[$name].TrimEnd('/') -cne $fixedValues[$name]) {
        throw 'Public configuration conflicts with the explicit Development launch settings.'
    }
}

$ingressKey = Read-ServiceKey $BeToChatSigningKeyFile
$coreKey = Read-ServiceKey $ChatToBeSigningKeyFile
if ($ingressKey -eq $coreKey) { throw 'BE-to-CHAT and CHAT-to-BE service keys must be distinct.' }

# Spring이 기본적으로 읽지 않는 FILE 이름과 환경/JSON의 우회 덮어쓰기를 거절한다.
$fixedEnvironment = @{
    SPRING_PROFILES_ACTIVE = 'local'
    CHAT_GATEWAY_ENVIRONMENT = $Environment
    CHAT_CORE_IDENTITY_ENVIRONMENT = $Environment
}
foreach ($name in ($publicValues.Keys + $fixedValues.Keys)) {
    # Spring relaxed binding은 점을 밑줄로, 하이픈은 제거한다. 실제 소비하는 이름만 검사한다.
    $environmentName = $name.Replace('.', '_').Replace('-', '').ToUpperInvariant()
    $fixedEnvironment[$environmentName] = if ($fixedValues.ContainsKey($name)) {
        $fixedValues[$name]
    } else { $publicValues[$name] }
}
foreach ($name in $fixedEnvironment.Keys) {
    $existing = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ($existing -and $existing -cne $fixedEnvironment[$name]) {
        throw 'Process environment conflicts with the Development launch environment.'
    }
}
foreach ($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) {
    if ($entry.Value -and ($entry.Key -in @('SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION',
        'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME') -or
        $entry.Key -match '^CHAT_(GATEWAY|CORE_IDENTITY)_.*_FILE$')) {
        throw 'Unsupported Spring JSON or Chat FILE environment input. Use the explicit launcher file parameters.'
    }
}

# 기존 local 설정에 같은 방향키가 있으면 파일 원본과 비교한다. 사용자 JWT/LL 키는 읽거나 교체하지 않는다.
$secretConfiguration = [IO.File]::ReadAllText($LocalSecretsConfigurationFile)
$directionProperties = @{
    'chat.gateway.secret-base64' = $ingressKey
    'chat.core.identity.secret-base64' = $coreKey
}
foreach ($name in $directionProperties.Keys) {
    $matchesForKey = [regex]::Matches($secretConfiguration,
        '(?m)^\s*' + [regex]::Escape($name) + '\s*[=:]\s*([^\r\n]*)')
    if ($matchesForKey.Count -gt 1 -or ($matchesForKey.Count -eq 1 -and
        $matchesForKey[0].Groups[1].Value.Trim() -cne $directionProperties[$name])) {
        throw 'Existing Spring secret configuration conflicts with a directional key file.'
    }
}

# 파일 입력은 실제 Spring이 읽는 환경변수에만 전달한다. 기존 값이 다르면 덮어쓰지 않는다.
$inputs = @{
    CHAT_GATEWAY_SECRETBASE64 = $ingressKey
    CHAT_CORE_IDENTITY_SECRETBASE64 = $coreKey
}
$previous = @{}
foreach ($name in $inputs.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ($previous[$name] -and $previous[$name] -ne $inputs[$name]) {
        throw 'Existing service authentication environment conflicts with the supplied key file.'
    }
}
if ($ValidateOnly) {
    Write-Output 'VALID: current proxy-only JAR, Development loopback, external configuration and directional key files.'
    return
}

# 현재 호출에서만 키를 공급하고 종료 뒤 원래 환경으로 돌린다. DB 초기화나 migration은 실행하지 않는다.
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach ($name in $inputs.Keys) { [Environment]::SetEnvironmentVariable($name, $inputs[$name], 'Process') }
    $secretsUri = [Uri]::new([IO.Path]::GetFullPath($LocalSecretsConfigurationFile)).AbsoluteUri
    $configurationUri = [Uri]::new([IO.Path]::GetFullPath($ConfigurationFile)).AbsoluteUri
    $arguments = @('-jar', $JarPath, '--spring.profiles.active=local',
        "--spring.config.additional-location=$secretsUri,$configurationUri", '--server.address=127.0.0.1', "--server.port=$Port",
        '--spring.jpa.hibernate.ddl-auto=none', '--spring.sql.init.mode=never',
        '--chat.gateway.enabled=true', "--chat.gateway.environment=$Environment", "--chat.gateway.base-url=$ChatBaseUrl",
        '--chat.core.identity.enabled=true', "--chat.core.identity.environment=$Environment")
    foreach ($logger in @('sqlonly', 'sqltiming', 'audit', 'resultset', 'connection', 'resultsettable')) {
        $arguments += "--logging.level.jdbc.$logger=OFF"
    }
    & $Java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Local BE Chat proxy stopped with a nonzero exit code.' }
} finally {
    foreach ($name in $inputs.Keys) {
        # 원래 없는 값과 기존 빈 값을 구분한다. 빈 환경변수도 Spring에 별도 입력으로 전달될 수 있다.
        $restoreValue = if ($null -eq $previous[$name]) { [NullString]::Value } else { $previous[$name] }
        [Environment]::SetEnvironmentVariable($name, $restoreValue, 'Process')
    }
    Pop-Location
}
