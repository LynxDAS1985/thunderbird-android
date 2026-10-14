#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [switch]$Json
)

function Find-EnvironmentCommand {
    param([string]$Name)
    $command = Get-Command $Name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { $command.Source }
}

function Invoke-EnvironmentCommand {
    param([string]$Executable, [string[]]$Arguments)
    # stderr Java содержит версию; наружу передаются только разобранные значения.
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Executable @Arguments 2>&1
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Text = ($output | Out-String) }
    } catch {
        [pscustomobject]@{ ExitCode = 1; Text = '' }
    }
}

function Get-EnvironmentCheck {
    param([string]$Id, [bool]$Passed, [string]$Message)
    [pscustomobject]@{ id = $Id; passed = $Passed; message = $Message }
}

function Get-EnvironmentReport {
    param([string]$Root)
    $ErrorActionPreference = 'Stop'
    $checks = New-Object 'System.Collections.Generic.List[object]'
    try {
        $rootPath = (Resolve-Path -LiteralPath $Root).ProviderPath
        if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) { throw 'root' }
    } catch {
        $checks.Add((Get-EnvironmentCheck 'project' $false 'Каталог проекта недоступен.'))
        return [pscustomobject]@{ schemaVersion = 1; blocked = $true; buildVerified = $false; checks = @($checks.ToArray()) }
    }

    try {
        $git = Find-EnvironmentCommand 'git'
        $repoOk = $false
        if ($git) {
            $result = Invoke-EnvironmentCommand $git @('-C', $rootPath, 'rev-parse', '--show-toplevel')
            if ($result.ExitCode -eq 0) {
                $repoRoot = [IO.Path]::GetFullPath($result.Text.Trim()).TrimEnd('\', '/')
                $repoOk = $repoRoot -eq $rootPath.TrimEnd('\', '/')
            }
        }
        $checks.Add((Get-EnvironmentCheck 'git' ([bool]$git) 'Наличие команды git.'))
        $checks.Add((Get-EnvironmentCheck 'repository' $repoOk 'ProjectRoot должен совпадать с корнем рабочего дерева Git.'))
    } catch {
        $checks.Add((Get-EnvironmentCheck 'repository' $false 'Не удалось проверить Git-репозиторий.'))
    }

    try {
        $required = (Get-Content -LiteralPath (Join-Path $rootPath '.java-version') -Raw).Trim()
        if ($required -notmatch '^([1-9][0-9]*)(?:\.[0-9]+)*$') { throw 'version' }
        $requiredMajor = [int]$Matches[1]
        $checks.Add((Get-EnvironmentCheck 'javaRequirement' $true "Требуемая основная версия Java: $requiredMajor (.java-version)."))
        $java = $null
        $source = 'PATH'
        if ($env:JAVA_HOME) {
            $source = 'JAVA_HOME'
            $candidate = Join-Path $env:JAVA_HOME.Replace('"', '') 'bin/java.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { $java = $candidate }
        } else {
            $java = Find-EnvironmentCommand 'java'
        }
        $javaOk = $false
        $message = "Java недоступна через $source."
        if ($java) {
            $result = Invoke-EnvironmentCommand $java @('-version')
            if ($result.ExitCode -eq 0 -and $result.Text -match '(?m)^(?:openjdk|java) version "(?<major>[0-9]+)(?:\.(?<minor>[0-9]+))?[^"\r\n]*"') {
                $major = [int]$Matches.major
                if ($major -eq 1) { $major = [int]$Matches.minor }
                $javaOk = $major -eq $requiredMajor
                $message = "Java через ${source}: основная версия $major; требуется $requiredMajor."
            } else {
                $message = "Не удалось определить версию Java через $source."
            }
        }
        $checks.Add((Get-EnvironmentCheck 'java' $javaOk $message))
    } catch {
        $checks.Add((Get-EnvironmentCheck 'java' $false 'Не удалось прочитать требование или проверить Java.'))
    }

    try {
        $wrapperFiles = @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties')
        $missing = @($wrapperFiles | Where-Object {
            $file = Join-Path $rootPath $_
            -not (Test-Path -LiteralPath $file -PathType Leaf) -or (Get-Item -LiteralPath $file).Length -eq 0
        })
        $checks.Add((Get-EnvironmentCheck 'wrapper' ($missing.Count -eq 0) 'Проверено наличие непустых файлов Wrapper; запуск и загрузка не выполнялись.'))
    } catch {
        $checks.Add((Get-EnvironmentCheck 'wrapper' $false 'Файлы Wrapper недоступны.'))
    }

    $compileSdk = $null
    try {
        $config = Get-Content -LiteralPath (Join-Path $rootPath 'build-plugin/src/main/kotlin/ThunderbirdProjectConfig.kt') -Raw
        # Поддерживается текущая декларация константы; неизвестный формат блокирует проверку.
        $config = [regex]::Replace($config, '(?s)/\*.*?\*/|(?m)//[^\r\n]*', '')
        $values = [regex]::Matches($config, '(?m)^\s*const\s+val\s+sdkCompile\s*=\s*([1-9][0-9]*)\s*$')
        if ($values.Count -ne 1) { throw 'compileSdk' }
        $compileSdk = [int]$values[0].Groups[1].Value
        $checks.Add((Get-EnvironmentCheck 'compileSdk' $true "compileSdk: $compileSdk (ThunderbirdProjectConfig.kt)."))
    } catch {
        $checks.Add((Get-EnvironmentCheck 'compileSdk' $false 'Не удалось однозначно прочитать sdkCompile из конфигурации.'))
    }

    try {
        $sdk = $null
        $source = 'не задан'
        $localFile = Join-Path $rootPath 'local.properties'
        if (Test-Path -LiteralPath $localFile -PathType Leaf) {
            $entries = @(Get-Content -LiteralPath $localFile | Where-Object { $_ -match '^\s*sdk\.dir(?:\s*[:=]|\s+)' })
            if ($entries.Count -gt 0) {
                $source = 'local.properties'
                $sdk = ($entries[-1] -replace '^\s*sdk\.dir(?:\s*[:=]\s*|\s+)', '').Trim()
                # Java properties: типичные пути Windows (C\:\\...) и Unicode escapes.
                $sdk = [regex]::Replace($sdk, '\\(u[0-9a-fA-F]{4}|.)', {
                    param($match)
                    $value = $match.Groups[1].Value
                    if ($value -match '^u[0-9a-fA-F]{4}$') { return [string][char][Convert]::ToInt32($value.Substring(1), 16) }
                    return $value
                })
                if (-not $sdk -or $sdk.EndsWith('\')) { throw 'sdkPath' }
            }
        }
        if ($source -ne 'local.properties') {
            if ($env:ANDROID_HOME) { $sdk = $env:ANDROID_HOME; $source = 'ANDROID_HOME' }
            elseif ($env:ANDROID_SDK_ROOT) { $sdk = $env:ANDROID_SDK_ROOT; $source = 'ANDROID_SDK_ROOT' }
        }
        if ($env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) {
            $same = [IO.Path]::GetFullPath($env:ANDROID_HOME).TrimEnd('\', '/') -eq [IO.Path]::GetFullPath($env:ANDROID_SDK_ROOT).TrimEnd('\', '/')
            $checks.Add((Get-EnvironmentCheck 'sdkVariables' $same 'ANDROID_HOME и ANDROID_SDK_ROOT должны указывать на один каталог.'))
        }
        if ($sdk -and -not [IO.Path]::IsPathRooted($sdk)) { $sdk = Join-Path $rootPath $sdk }
        $sdkOk = $sdk -and (Test-Path -LiteralPath $sdk -PathType Container)
        $checks.Add((Get-EnvironmentCheck 'sdk' ([bool]$sdkOk) "Источник Android SDK: $source; проверено наличие каталога."))
        $platformOk = $false
        if ($sdkOk -and $compileSdk) {
            $androidJar = Join-Path $sdk "platforms/android-$compileSdk/android.jar"
            $platformOk = (Test-Path -LiteralPath $androidJar -PathType Leaf) -and (Get-Item -LiteralPath $androidJar).Length -gt 0
        }
        $checks.Add((Get-EnvironmentCheck 'platform' $platformOk 'Проверено наличие непустого android.jar для требуемого compileSdk.'))
    } catch {
        $checks.Add((Get-EnvironmentCheck 'sdk' $false 'Не удалось проверить настройки Android SDK.'))
    }

    [pscustomobject]@{
        schemaVersion = 1
        blocked = @($checks | Where-Object { -not $_.passed }).Count -gt 0
        buildVerified = $false
        checks = @($checks.ToArray())
    }
}

function Start-EnvironmentDoctor {
    param([string]$Root, [switch]$AsJson)
    $report = Get-EnvironmentReport $Root
    if ($AsJson) { $report | ConvertTo-Json -Depth 5 }
    else {
        foreach ($check in $report.checks) {
            $state = if ($check.passed) { 'OK' } else { 'BLOCKED' }
            '[{0}] {1}: {2}' -f $state, $check.id, $check.message
        }
        'Сборка и задачи Gradle не запускались; результат не подтверждает успешную сборку.'
    }
    if ($report.blocked) { exit 1 }
    exit 0
}

# Dot-sourcing позволяет тестировать логику без выхода из процесса тестов.
if ($MyInvocation.InvocationName -ne '.') {
    Start-EnvironmentDoctor -Root $ProjectRoot -AsJson:$Json
}
