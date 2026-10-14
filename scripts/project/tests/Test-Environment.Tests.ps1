#requires -Version 5.1
param([string]$FixtureRoot, [switch]$Child)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../Test-Environment.ps1')

# Подмена только границы внешних процессов; конфигурация читается с диска.
function Find-EnvironmentCommand {
    param([string]$Name)
    if ($Name -eq 'git' -and -not $script:NoGit) { return 'fake-git' }
    if ($Name -eq 'java' -and -not $script:NoJava) { return 'fake-java' }
}
function Invoke-EnvironmentCommand {
    param([string]$Executable, [string[]]$Arguments)
    if ($Executable -eq 'fake-git') {
        return [pscustomobject]@{ ExitCode = $script:GitExit; Text = $script:RepoRoot }
    }
    $script:JavaInvoked = $Executable
    [pscustomobject]@{ ExitCode = $script:JavaExit; Text = $script:JavaText }
}
function Reset-Fakes {
    $script:NoGit = $false
    $script:NoJava = $false
    $script:GitExit = 0
    $script:JavaExit = 0
    $script:JavaText = 'openjdk version "21.0.5"'
    $script:JavaInvoked = ''
    $script:RepoRoot = $FixtureRoot
    $env:JAVA_HOME = $null
    $env:ANDROID_HOME = Join-Path $FixtureRoot 'sdk'
    $env:ANDROID_SDK_ROOT = $null
}
if ($Child) {
    Reset-Fakes
    Start-EnvironmentDoctor -Root $FixtureRoot -AsJson
}

$savedEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$FixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('mail-doctor-' + [guid]::NewGuid().ToString('N'))
$script:Passed = 0
function Write-Fixture {
    param([string]$RelativePath, [string]$Content)
    $path = Join-Path $FixtureRoot $RelativePath
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))
    [IO.File]::WriteAllText($path, $Content)
}
function Assert-Check {
    param([string]$Name, [bool]$Condition)
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:Passed++
    "PASS: $Name"
}
function Test-BlockedCheck {
    param([string]$Id)
    $report = Get-EnvironmentReport $FixtureRoot
    $report.blocked -and @($report.checks | Where-Object { $_.id -eq $Id -and -not $_.passed }).Count -gt 0
}
try {
    Write-Fixture '.java-version' '21'
    Write-Fixture 'build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt' '    const val sdkCompile = 37'
    foreach ($file in @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties', 'sdk/platforms/android-37/android.jar', 'jdk/bin/java.exe')) {
        Write-Fixture $file 'fixture'
    }
    Reset-Fakes
    $testSubject = Get-EnvironmentReport $FixtureRoot
    Assert-Check 'Исправное окружение через PATH и ANDROID_HOME' (-not $testSubject.blocked -and -not $testSubject.buildVerified)
    Assert-Check 'JSON сохраняет массив проверок' (($testSubject | ConvertTo-Json -Depth 5 | ConvertFrom-Json).checks.Count -eq 8)

    $env:JAVA_HOME = Join-Path $FixtureRoot 'jdk'
    $testSubject = Get-EnvironmentReport $FixtureRoot
    Assert-Check 'JAVA_HOME имеет приоритет над PATH' (-not $testSubject.blocked -and $script:JavaInvoked -eq (Join-Path $env:JAVA_HOME 'bin/java.exe'))
    $env:JAVA_HOME = Join-Path $FixtureRoot 'missing-jdk'
    Assert-Check 'Ошибочный JAVA_HOME не маскируется PATH' (Test-BlockedCheck 'java')
    Reset-Fakes
    $script:NoJava = $true
    Assert-Check 'Отсутствующая Java блокирует' (Test-BlockedCheck 'java')
    Reset-Fakes
    $script:JavaText = 'java version "17.0.1"'
    Assert-Check 'Java 17 не соответствует .java-version' (Test-BlockedCheck 'java')
    $script:JavaText = 'openjdk version "21.0.5"'
    $script:JavaExit = 1
    Assert-Check 'Ненулевой код Java блокирует даже при верной версии' (Test-BlockedCheck 'java')
    Reset-Fakes
    Write-Fixture '.java-version' 'invalid SECRET_SENTINEL'
    Assert-Check 'Поврежденное требование Java блокирует' (Test-BlockedCheck 'java')
    Assert-Check 'Содержимое конфигурации не попадает в JSON' ((Get-EnvironmentReport $FixtureRoot | ConvertTo-Json -Depth 5) -notmatch 'SECRET_SENTINEL')
    Write-Fixture '.java-version' '21'

    $script:NoGit = $true
    Assert-Check 'Отсутствующий Git блокирует' (Test-BlockedCheck 'git')
    Reset-Fakes
    $script:GitExit = 128
    Assert-Check 'Ошибка Git блокирует' (Test-BlockedCheck 'repository')
    Reset-Fakes
    $script:RepoRoot = [IO.Path]::GetTempPath()
    Assert-Check 'Вложенный каталог не принимается за корень Git' (Test-BlockedCheck 'repository')
    Reset-Fakes

    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:ANDROID_HOME = $null
    Assert-Check 'SDK через ANDROID_SDK_ROOT' (-not (Get-EnvironmentReport $FixtureRoot).blocked)
    $env:ANDROID_HOME = Join-Path $FixtureRoot 'other-sdk'
    Assert-Check 'Конфликт переменных SDK блокирует' (Test-BlockedCheck 'sdkVariables')
    Reset-Fakes
    $sdkProperty = $env:ANDROID_HOME.Replace('\', '\\').Replace(':', '\:')
    Write-Fixture 'local.properties' "sdk.dir=$sdkProperty`npassword=SECRET_SENTINEL"
    $env:ANDROID_HOME = Join-Path $FixtureRoot 'absent-sdk'
    $testSubject = Get-EnvironmentReport $FixtureRoot
    Assert-Check 'local.properties приоритетнее среды, Windows escapes читаются' (-not $testSubject.blocked)
    Assert-Check 'Посторонние свойства не выводятся' (($testSubject | ConvertTo-Json -Depth 5) -notmatch 'SECRET_SENTINEL')
    Write-Fixture 'local.properties' 'sdk.dir=absent-sdk'
    Reset-Fakes
    Assert-Check 'Ошибочный sdk.dir не маскируется переменными' (Test-BlockedCheck 'sdk')
    Write-Fixture 'local.properties' '# sdk.dir отсутствует'
    $env:ANDROID_HOME = $null
    Assert-Check 'SDK без источника блокирует' (Test-BlockedCheck 'sdk')
    Reset-Fakes

    Write-Fixture 'sdk/platforms/android-37/android.jar' ''
    Assert-Check 'Пустой android.jar блокирует' (Test-BlockedCheck 'platform')
    Write-Fixture 'sdk/platforms/android-37/android.jar' 'fixture'
    Write-Fixture 'build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt' 'const val sdkCompile = 38'
    Assert-Check 'Платформа определяется конфигурацией, а не константой doctor' (Test-BlockedCheck 'platform')
    Write-Fixture 'build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt' '// const val sdkCompile = 37'
    Assert-Check 'Закомментированная версия SDK не принимается' (Test-BlockedCheck 'compileSdk')
    Write-Fixture 'build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt' 'const val sdkCompile = 37'
    Write-Fixture 'gradle/wrapper/gradle-wrapper.jar' ''
    Assert-Check 'Пустой Wrapper блокирует' (Test-BlockedCheck 'wrapper')
    Write-Fixture 'gradle/wrapper/gradle-wrapper.jar' 'fixture'

    $powershell = Join-Path $PSHOME 'powershell.exe'
    $output = & $powershell -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -Child -FixtureRoot $FixtureRoot
    $exitCode = $LASTEXITCODE
    $report = $output | Out-String | ConvertFrom-Json
    Assert-Check 'Дочерний процесс: успешный JSON и exit 0 (моки процессов)' ($exitCode -eq 0 -and -not $report.blocked)
    Write-Fixture 'gradlew.bat' ''
    $output = & $powershell -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -Child -FixtureRoot $FixtureRoot
    $exitCode = $LASTEXITCODE
    $report = $output | Out-String | ConvertFrom-Json
    Assert-Check 'Дочерний процесс: блокировка JSON и exit 1 (моки процессов)' ($exitCode -eq 1 -and $report.blocked)
    $doctor = Join-Path $PSScriptRoot '../Test-Environment.ps1'
    $output = & $powershell -NoProfile -ExecutionPolicy Bypass -File $doctor -ProjectRoot (Join-Path $FixtureRoot 'missing') -Json
    $exitCode = $LASTEXITCODE
    $report = $output | Out-String | ConvertFrom-Json
    Assert-Check 'Production CLI: несуществующий ProjectRoot, JSON и exit 1' ($exitCode -eq 1 -and $report.blocked -and -not $report.buildVerified)
    "Проверок успешно: $script:Passed"
} finally {
    foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    $resolvedFixture = [IO.Path]::GetFullPath($FixtureRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if ($resolvedFixture.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedFixture -Leaf) -match '^mail-doctor-[a-f0-9]{32}$') {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force -ErrorAction SilentlyContinue
    }
}
