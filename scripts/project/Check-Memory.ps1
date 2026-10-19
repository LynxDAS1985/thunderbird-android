#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-LocalMarkdownTargets {
    param([string]$Text)

    # Deliberately limited to inline [label](path) links, including images.
    # Reference links, HTML, titles, escaped/nested parentheses are unsupported.
    $withoutCode = [regex]::Replace($Text, '(?ms)^\s*(`{3,}|~{3,})[^\r\n]*\r?\n.*?^\s*\1\s*$', '')
    $withoutCode = [regex]::Replace($withoutCode, '`[^`\r\n]*`', '')
    foreach ($match in [regex]::Matches($withoutCode, '\[[^\[\]\r\n]*\]\((?<target>[^\s()<>]+)\)')) {
        $target = $match.Groups['target'].Value
        if ($target -match '^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//|#)') { continue }
        $path = ($target -split '[?#]', 2)[0]
        if ($path.Length -eq 0) { continue }
        [Uri]::UnescapeDataString($path).Replace('\', '/')
    }
}

try {
    if (-not $PSBoundParameters.ContainsKey('ProjectRoot')) {
        $ProjectRoot = Join-Path $PSScriptRoot '../..'
    }
    $root = (Get-Item -LiteralPath $ProjectRoot).FullName
    $memoryRoot = Join-Path $root '.memory-bank'
    if (-not (Test-Path -LiteralPath $memoryRoot -PathType Container)) {
        throw "Missing memory directory: $memoryRoot"
    }

    # Traverse explicitly so junctions cannot silently hide files or leave the tree.
    $pending = New-Object 'System.Collections.Generic.Queue[string]'
    $pending.Enqueue($memoryRoot)
    $files = New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'
    while ($pending.Count -gt 0) {
        $directory = Get-Item -LiteralPath $pending.Dequeue() -Force
        if ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Unsupported reparse point: $($directory.FullName)"
        }
        foreach ($item in Get-ChildItem -LiteralPath $directory.FullName -Force) {
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Unsupported reparse point: $($item.FullName)"
            }
            if ($item.PSIsContainer) { $pending.Enqueue($item.FullName) }
            else { $files.Add($item) }
        }
    }

    $issues = New-Object 'System.Collections.Generic.List[string]'
    $indexes = @(
        [pscustomobject]@{ Path = (Join-Path $root 'CLAUDE.md'); Prefix = '.memory-bank/'; Targets = @() },
        [pscustomobject]@{ Path = (Join-Path $memoryRoot 'README.md'); Prefix = ''; Targets = @() }
    )
    foreach ($index in $indexes) {
        if (-not (Test-Path -LiteralPath $index.Path -PathType Leaf)) {
            $issues.Add("Missing index: $($index.Path)")
            continue
        }
        $index.Targets = @(Get-LocalMarkdownTargets ([IO.File]::ReadAllText($index.Path)) | ForEach-Object {
            $_ -replace '^(\./)+', ''
        })
    }

    foreach ($file in $files) {
        $relative = $file.FullName.Substring($memoryRoot.Length + 1).Replace('\', '/')
        foreach ($index in $indexes) {
            $expected = $index.Prefix + $relative
            if ($index.Targets -cnotcontains $expected) {
                $issues.Add("Unregistered: $expected in $($index.Path)")
            }
        }
    }

    $linkCount = 0
    $documents = @($files | ForEach-Object { $_.FullName })
    if (Test-Path -LiteralPath $indexes[0].Path -PathType Leaf) { $documents += $indexes[0].Path }
    foreach ($document in $documents) {
        $content = [IO.File]::ReadAllText($document)
        if ([string]::IsNullOrWhiteSpace($content)) { $issues.Add("Empty file: $document") }
        if ([IO.Path]::GetExtension($document) -ine '.md') { continue }
        foreach ($target in @(Get-LocalMarkdownTargets $content)) {
            $linkCount++
            $destination = Join-Path (Split-Path -Parent $document) $target
            if (-not (Test-Path -LiteralPath $destination)) {
                $issues.Add("Broken link: $document -> $target")
            }
        }
    }

    foreach ($file in $files) {
        $limitKb = if ($file.Name -ieq 'common-mistakes.md') { 10 } else { 100 }
        $sizeKb = [math]::Round($file.Length / 1KB, 1)
        if ($file.Length -gt ($limitKb * 1024)) {
            $issues.Add("File size limit exceeded: $($file.FullName) ($sizeKb KB > $limitKb KB)")
        }
    }

    # Version synchronization check
    $gradlePath = Join-Path $root 'app-thunderbird/build.gradle.kts'
    if (Test-Path -LiteralPath $gradlePath -PathType Leaf) {
        $gradleText = [IO.File]::ReadAllText($gradlePath)
        $gradleMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"(?<ver>[^"]+)"')
        if ($gradleMatch.Success) {
            $gradleVer = $gradleMatch.Groups['ver'].Value
            $claudeText = if (Test-Path -LiteralPath $indexes[0].Path) { [IO.File]::ReadAllText($indexes[0].Path) } else { '' }
            $claudeMatch = [regex]::Match($claudeText, '(?i)(?:Current version|version)\D*(?<ver>[0-9]+(?:\.[0-9]+)+)')
            $featuresPath = Join-Path $memoryRoot 'features.md'
            $featuresText = if (Test-Path -LiteralPath $featuresPath) { [IO.File]::ReadAllText($featuresPath) } else { '' }
            $featuresMatch = [regex]::Match($featuresText, '(?i)(?:Current version|version)\D*(?<ver>[0-9]+(?:\.[0-9]+)+)')

            if (-not $claudeMatch.Success -or $claudeMatch.Groups['ver'].Value -ne $gradleVer) {
                $issues.Add("Version mismatch in CLAUDE.md: expected '$gradleVer', found '$($claudeMatch.Groups['ver'].Value)'")
            }
            if (-not $featuresMatch.Success -or $featuresMatch.Groups['ver'].Value -ne $gradleVer) {
                $issues.Add("Version mismatch in .memory-bank/features.md: expected '$gradleVer', found '$($featuresMatch.Groups['ver'].Value)'")
            }
        }
    }

    foreach ($issue in $issues) { Write-Output "ERROR: $issue" }
    Write-Output ("Memory check: {0} files, {1} local links, {2} errors." -f $files.Count, $linkCount, $issues.Count)
    if ($issues.Count -gt 0) { exit 1 }
    exit 0
}
catch {
    Write-Output "ERROR: $($_.Exception.Message)"
    exit 1
}
