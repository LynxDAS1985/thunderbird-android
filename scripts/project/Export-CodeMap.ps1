#Requires -Version 5.1
<#
.SYNOPSIS
Exports tracked working-tree files to docs/code-map, or verifies them with -Check.
.DESCRIPTION
Requires Git only. No Gradle evaluation. Run against a quiet working tree.
Module ownership uses tracked build.gradle.kts files; root module is '.'.
sourceCount counts kind=code, testCount counts kind=test (including test data).
Explicit project references are lexical matches, not resolved dependencies.
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot = (Join-Path $PSScriptRoot '../..'),
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-GitText([string]$Arguments) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = 'git'
    $info.Arguments = $Arguments
    $info.WorkingDirectory = $script:root
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $process = [Diagnostics.Process]::Start($info)
    try {
        $errorTask = $process.StandardError.ReadToEndAsync()
        $result = $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Git failed: $($errorTask.Result.Trim())" }
        return $result
    }
    finally { $process.Dispose() }
}

function Assert-PlainPath([string]$Path) {
    $current = $Path
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Reparse points are unsupported: $current"
            }
        }
        $current = [IO.Path]::GetDirectoryName($current)
    }
}

function Get-Kind([string]$Path, [string]$SourceSet) {
    if ($SourceSet -match '(?i)test' -or $Path -match '(^|/)(tests?|testFixtures)/') { return 'test' }
    if ($Path -match '(^|/)(res|resources|assets)/' -or $Path -match '/AndroidManifest\.xml$') { return 'resource' }
    if ($Path -match '(^|/)(build\.gradle\.kts|settings\.gradle\.kts|gradlew(\.bat)?|gradle\.properties)$' -or
        $Path -match '(^|/)(gradle|build-plugin)/' -or $Path -match '\.gradle(\.kts)?$') { return 'build' }
    if ($Path -match '(^|/)docs/' -or $Path -match '\.(md|rst|adoc)$' -or
        $Path -match '(^|/)(LICENSE|NOTICE)(\.|$)') { return 'docs' }
    if ($Path -match '\.(kt|kts|java|groovy|c|cpp|h|hpp|js|ts|tsx|jsx|py|sh|ps1|rb|swift)$') { return 'code' }
    return 'other'
}

try {
    if ([string]::IsNullOrWhiteSpace($ProjectRoot) -or -not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) {
        throw 'ProjectRoot must be an existing Git working-tree root.'
    }
    $root = (Get-Item -LiteralPath $ProjectRoot -Force).FullName.TrimEnd('\', '/')
    Assert-PlainPath $root
    $gitRoot = (Invoke-GitText 'rev-parse --show-toplevel').TrimEnd("`r", "`n").Replace('/', [IO.Path]::DirectorySeparatorChar)
    if (-not [string]::Equals($root, $gitRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ProjectRoot must be the Git working-tree root, not a subdirectory.'
    }
    $commit = (Invoke-GitText 'rev-parse --verify HEAD').Trim()
    [string[]]$paths = (Invoke-GitText 'ls-files --cached -z').Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)
    $unique = New-Object 'Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    $paths = @($paths | Where-Object {
        $_ -cnotin @('docs/code-map/files.csv', 'docs/code-map/modules.csv', 'docs/code-map/summary.json') -and $unique.Add($_)
    })
    [Array]::Sort($paths, [StringComparer]::Ordinal)
    $modules = New-Object 'Collections.Generic.Dictionary[string,object]' ([StringComparer]::Ordinal)
    $files = New-Object 'Collections.Generic.List[object]'
    $fingerprints = New-Object Text.StringBuilder
    $output = Join-Path $root 'docs/code-map'
    $names = @('files.csv', 'modules.csv', 'summary.json')
    foreach ($name in $names) { Assert-PlainPath (Join-Path $output $name) }

    # Validate every input before creating or replacing any snapshot file.
    foreach ($path in $paths) {
        $fullPath = Join-Path $root $path
        Assert-PlainPath $fullPath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw "Missing tracked file: $path" }
        if ([IO.Path]::GetFileName($path) -ceq 'build.gradle.kts') {
            $directory = [IO.Path]::GetDirectoryName($path).Replace('\', '/')
            if (-not $directory) { $directory = '.' }
            $content = [IO.File]::ReadAllText($fullPath)
            [string[]]$references = @([regex]::Matches($content,
                '\bprojects(?:\.[A-Za-z_][A-Za-z0-9_]*)+|\bproject\s*\(\s*["''](:[^"'']*)["'']\s*\)') |
                ForEach-Object { $_.Value } | Select-Object -Unique)
            [Array]::Sort($references, [StringComparer]::Ordinal)
            $modules.Add($directory, [pscustomobject][ordered]@{
                path = $directory; buildFile = $path; fileCount = 0; sourceCount = 0; testCount = 0
                explicitProjectReferences = $references -join ';'
            })
        }
    }
    [long]$totalBytes = 0
    $kindCounts = [ordered]@{ code = 0; test = 0; resource = 0; build = 0; docs = 0; other = 0 }
    foreach ($path in $paths) {
        $fullPath = Join-Path $root $path
        $directory = [IO.Path]::GetDirectoryName($path).Replace('\', '/')
        if (-not $directory) { $directory = '.' }
        while (-not $modules.ContainsKey($directory) -and $directory -ne '.') {
            $directory = [IO.Path]::GetDirectoryName($directory).Replace('\', '/')
            if (-not $directory) { $directory = '.' }
        }
        $moduleDirectory = ''
        if ($modules.ContainsKey($directory)) { $moduleDirectory = $directory }
        $sourceSet = ''
        if ($path -cmatch '(?:^|/)src/([^/]+)/') { $sourceSet = $Matches[1] }
        $kind = Get-Kind $path $sourceSet
        $bytes = (Get-Item -LiteralPath $fullPath -Force).Length
        $files.Add([pscustomobject][ordered]@{
            path = $path; moduleDirectory = $moduleDirectory; sourceSet = $sourceSet; kind = $kind; bytes = $bytes
        })
        $kindCounts[$kind]++
        $totalBytes += $bytes
        if ($moduleDirectory) {
            $modules[$directory].fileCount++
            if ($kind -eq 'code') { $modules[$directory].sourceCount++ }
            if ($kind -eq 'test') { $modules[$directory].testCount++ }
        }
        $hash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash
        [void]$fingerprints.Append($path.Length).Append(':').Append($path).Append(':').Append($hash).Append("`n")
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $digest = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($fingerprints.ToString()))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
    [string[]]$modulePaths = @($modules.Keys)
    [Array]::Sort($modulePaths, [StringComparer]::Ordinal)
    $moduleRows = @($modulePaths | ForEach-Object { $modules[$_] })
    $summary = [ordered]@{
        schemaVersion = 1; commit = $commit; fileCount = $files.Count; moduleCount = $modules.Count
        totalBytes = $totalBytes; kindCounts = $kindCounts; workingTreeSha256 = $digest
        scopeCaveat = 'Navigation index, not an audit. Git tracked working-tree files only; excludes the three generated snapshots. No untracked files or Gradle evaluation. Modules use nearest tracked build.gradle.kts (root = ., none = empty). Kinds/source sets are path heuristics; sourceCount = code, testCount = test including fixtures/resources. Project references are lexical mentions, possibly comments/strings, not resolved dependencies. Reparse points/submodules are unsupported. Requires HEAD and a quiet working tree; no atomic multi-file snapshot.'
    }
    $fileHeader = '"path","moduleDirectory","sourceSet","kind","bytes"'
    $moduleHeader = '"path","buildFile","fileCount","sourceCount","testCount","explicitProjectReferences"'
    $fileCsv = @($fileHeader)
    if ($files.Count) { $fileCsv = @($files | ConvertTo-Csv -NoTypeInformation) }
    $moduleCsv = @($moduleHeader)
    if ($moduleRows.Count) { $moduleCsv = @($moduleRows | ConvertTo-Csv -NoTypeInformation) }
    $expected = @{
        'files.csv' = ($fileCsv -join "`r`n") + "`r`n"
        'modules.csv' = ($moduleCsv -join "`r`n") + "`r`n"
        'summary.json' = ($summary | ConvertTo-Json -Depth 4) + "`r`n"
    }
    if ($Check) {
        foreach ($name in $names) {
            $destination = Join-Path $output $name
            if (-not (Test-Path -LiteralPath $destination -PathType Leaf) -or
                [IO.File]::ReadAllText($destination) -cne $expected[$name]) { throw "Stale or missing snapshot: $name" }
        }
        Write-Output "Code map current: $($files.Count) files, $($modules.Count) modules."
    }
    else {
        [void][IO.Directory]::CreateDirectory($output)
        if ($files.Count) { $files | Export-Csv -LiteralPath (Join-Path $output 'files.csv') -NoTypeInformation -Encoding UTF8 }
        else { Set-Content -LiteralPath (Join-Path $output 'files.csv') -Value $fileHeader -Encoding UTF8 }
        if ($moduleRows.Count) { $moduleRows | Export-Csv -LiteralPath (Join-Path $output 'modules.csv') -NoTypeInformation -Encoding UTF8 }
        else { Set-Content -LiteralPath (Join-Path $output 'modules.csv') -Value $moduleHeader -Encoding UTF8 }
        [IO.File]::WriteAllText((Join-Path $output 'summary.json'), $expected['summary.json'], (New-Object Text.UTF8Encoding($true)))
        Write-Output "Code map exported: $($files.Count) files, $($modules.Count) modules."
    }
    exit 0
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
