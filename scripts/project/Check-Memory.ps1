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

    foreach ($issue in $issues) { Write-Output "ERROR: $issue" }
    Write-Output ("Memory check: {0} files, {1} local links, {2} errors." -f $files.Count, $linkCount, $issues.Count)
    if ($issues.Count -gt 0) { exit 1 }
    exit 0
}
catch {
    Write-Output "ERROR: $($_.Exception.Message)"
    exit 1
}
