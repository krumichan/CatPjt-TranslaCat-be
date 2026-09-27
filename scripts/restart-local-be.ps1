$ErrorActionPreference = 'Stop'
$beRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

# 준비: 작업 전용 loopback 포트와 설정 파일을 가진 BE 프로세스만 확인한다.
$listener = Get-NetTCPConnection -LocalPort 18767 -State Listen | Select-Object -First 1
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
$jarPath = [IO.Path]::GetFullPath((Join-Path $beRoot 'build/libs/spring-boot-translacat-0.0.1-SNAPSHOT.jar'))
$configPath = [IO.Path]::GetFullPath((Join-Path $beRoot 'build/live-application.properties'))
$ownedJar = $process.CommandLine -match '-jar\s+"?build/libs/spring-boot-translacat-0\.0\.1-SNAPSHOT\.jar"?(\s|$)'
if ($process.Name -ne 'java.exe' -or -not $ownedJar -or
    $process.CommandLine -notlike '*--spring.config.additional-location=file:build/live-application.properties*') {
    throw 'Expected owned scratch BE process'
}
if (-not $jarPath.StartsWith($beRoot + [IO.Path]::DirectorySeparatorChar) -or
    -not $configPath.StartsWith($beRoot + [IO.Path]::DirectorySeparatorChar) -or
    -not (Test-Path -LiteralPath $jarPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw 'Expected BE jar and scratch configuration inside verified workspace'
}
$prefix = if ($process.CommandLine.StartsWith('"')) { '"' + $process.ExecutablePath + '"' } else { $process.ExecutablePath }
if (-not $process.CommandLine.StartsWith($prefix)) { throw 'Unexpected BE executable prefix' }
$arguments = $process.CommandLine.Substring($prefix.Length).Trim()
$entry = docker inspect --format '{{json .Config.Env}}' 9d6e91ffcea2 | ConvertFrom-Json |
    Where-Object { $_ -like 'MYSQL_ROOT_PASSWORD=*' } | Select-Object -First 1
if (-not $entry) { throw 'Scratch DB credentials unavailable' }
$env:TEST_DB_PASSWORD = $entry.Substring('MYSQL_ROOT_PASSWORD='.Length)
$env:TEST_LL_KEY = [Convert]::ToBase64String([byte[]](0..31))
$env:TEST_JWT_KEY = [Convert]::ToBase64String([byte[]](0..63))

# 실행: 확인한 PID만 재시작하며 자격증명은 환경 변수로만 전달한다.
Stop-Process -Id $process.ProcessId
$stamp = [guid]::NewGuid().ToString('N')
$started = Start-Process -FilePath $process.ExecutablePath -ArgumentList $arguments -WorkingDirectory $beRoot -WindowStyle Hidden `
    -RedirectStandardOutput "$beRoot/build/listening-be-$stamp.out.log" `
    -RedirectStandardError "$beRoot/build/listening-be-$stamp.err.log" -PassThru

# 검증: 인증 없는 설정 조회의 거부 응답으로 Spring 준비를 확인한다.
$ready = $false
$until = (Get-Date).AddSeconds(55)
while (-not $ready -and (Get-Date) -lt $until) {
    try {
        $response = Invoke-WebRequest 'http://127.0.0.1:18767/api/v1/language-learning/settings' -SkipHttpErrorCheck -TimeoutSec 2
        $ready = $response.StatusCode -in @(401, 403)
    } catch { Start-Sleep -Milliseconds 300 }
}
if (-not $ready) { throw "Scratch BE startup failed; inspect sanitized log listening-be-$stamp.err.log" }
Write-Output "Scratch BE ready: PID=$($started.Id), local DB and LL only"
