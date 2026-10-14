#Requires -Version 5.1
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$checker = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../Check-Memory.ps1'))
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')).TrimEnd('\', '/')
$engine = Join-Path $PSHOME 'powershell.exe'
if (-not (Test-Path -LiteralPath $engine)) { $engine = Join-Path $PSHOME 'pwsh' }
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$fixtureRoot = Join-Path $tempParent ('mail-memory-tests-' + [guid]::NewGuid().ToString('N'))
if ($fixtureRoot.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Fixtures must be outside the repository.'
}
$passed = 0
$failed = 0

function Write-Fixture {
    param([string]$Root, [string]$Path, [string]$Content)
    $destination = Join-Path $Root $Path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
    [IO.File]::WriteAllText($destination, $Content, (New-Object Text.UTF8Encoding($false)))
}

function New-Fixture {
    param([string]$Name)
    $root = Join-Path $fixtureRoot $Name
    Write-Fixture $root 'CLAUDE.md' "[index](.memory-bank/README.md)`n[note](.memory-bank/note.md)`n"
    Write-Fixture $root '.memory-bank/README.md' "[index](README.md)`n[note](note.md)`n"
    Write-Fixture $root '.memory-bank/note.md' "# Note`n[index](README.md#heading)`n"
    return $root
}

function Assert-Check {
    param([string]$Name, [string]$Root, [int]$ExpectedExit, [string]$ExpectedText, [switch]$DefaultRoot)
    $scriptPath = $checker
    $arguments = @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass')
    if ($DefaultRoot) {
        $scriptPath = Join-Path $Root 'scripts/project/Check-Memory.ps1'
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $scriptPath))
        [IO.File]::Copy($checker, $scriptPath)
        $arguments += @('-File', $scriptPath)
    }
    else { $arguments += @('-File', $scriptPath, '-ProjectRoot', $Root) }
    $output = (& $engine @arguments 2>&1 | Out-String)
    $actualExit = $LASTEXITCODE
    if ($actualExit -eq $ExpectedExit -and $output.Contains($ExpectedText)) {
        $script:passed++
        Write-Output "PASS: $Name (exit $actualExit)"
    }
    else {
        $script:failed++
        Write-Output "FAIL: $Name; expected exit $ExpectedExit and '$ExpectedText'; got exit $actualExit`n$output"
    }
}

try {
    $root = New-Fixture 'valid project'
    Assert-Check 'valid memory' $root 0 '2 files, 5 local links, 0 errors.'
    Assert-Check 'default root independent of current directory' $root 0 '0 errors.' -DefaultRoot

    $root = New-Fixture 'missing-claude-entry'
    Write-Fixture $root 'CLAUDE.md' '[index](.memory-bank/README.md)'
    Assert-Check 'missing CLAUDE registration' $root 1 'Unregistered: .memory-bank/note.md'

    $root = New-Fixture 'missing-readme-entry'
    Write-Fixture $root '.memory-bank/README.md' '[index](README.md)'
    Assert-Check 'missing README registration' $root 1 'Unregistered: note.md'

    $root = New-Fixture 'missing-self-entry'
    Write-Fixture $root '.memory-bank/README.md' '[note](note.md)'
    Assert-Check 'README must register itself' $root 1 'Unregistered: README.md'

    $root = New-Fixture 'missing-index'
    Remove-Item -LiteralPath (Join-Path $root 'CLAUDE.md')
    Assert-Check 'missing index file' $root 1 'Missing index:'

    $root = New-Fixture 'broken-link'
    Write-Fixture $root '.memory-bank/note.md' '[missing](missing.md#section)'
    Assert-Check 'broken local link' $root 1 '-> missing.md'

    $root = New-Fixture 'empty'
    Write-Fixture $root '.memory-bank/note.md' ''
    Assert-Check 'zero-byte file' $root 1 'Empty file:'

    $root = New-Fixture 'whitespace'
    Write-Fixture $root '.memory-bank/note.md' " `r`n`t"
    Assert-Check 'whitespace-only file' $root 1 'Empty file:'

    $root = New-Fixture 'nested-unregistered'
    Write-Fixture $root '.memory-bank/nested/note.md' '# Nested'
    Assert-Check 'same basename cannot register nested file' $root 1 'Unregistered: .memory-bank/nested/note.md'

    $root = New-Fixture 'nested-valid'
    Write-Fixture $root '.memory-bank/nested/note.md' '[parent](../note.md)'
    Write-Fixture $root 'CLAUDE.md' "[index](.memory-bank/README.md)`n[note](.memory-bank/note.md)`n[nested](.memory-bank/nested/note.md)"
    Write-Fixture $root '.memory-bank/README.md' "[index](README.md)`n[note](note.md)`n[nested](nested/note.md)"
    Assert-Check 'registered nested file and parent link' $root 0 '3 files, 8 local links, 0 errors.'

    $root = New-Fixture 'ignored-links'
    Write-Fixture $root '.memory-bank/note.md' @'
# Note
[web](https://invalid.example/no-page)
[mail](mailto:example@example.invalid)
[network](//invalid.example/no-page)
[anchor](#missing-heading)
`[example](missing.md)`
```markdown
[example](missing.md)
```
[directory](../.memory-bank)
'@
    Assert-Check 'external anchors and code skipped; directory exists' $root 0 '0 errors.'

    $root = New-Fixture 'non-markdown'
    Write-Fixture $root '.memory-bank/nested/data.txt' 'data'
    Assert-Check 'non-Markdown files also need registration' $root 1 'Unregistered: .memory-bank/nested/data.txt'

    $root = New-Fixture 'mention-only'
    Write-Fixture $root 'CLAUDE.md' '[index](.memory-bank/README.md) Mention: `.memory-bank/note.md`'
    Assert-Check 'plain mention is not registration' $root 1 'Unregistered: .memory-bank/note.md'

    $root = New-Fixture 'literal-and-encoded'
    Write-Fixture $root 'file [1].txt' 'target'
    Write-Fixture $root '.memory-bank/note.md' '[target](../file%20%5B1%5D.txt)'
    Assert-Check 'URL decoding and literal file paths' $root 0 '0 errors.'
}
catch {
    $failed++
    Write-Output "FAIL: $($_.Exception.Message)`n$($_.ScriptStackTrace)"
}
finally {
    # Only delete this run's exact direct child of the system temp directory.
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    if ((Split-Path -Parent $resolved) -ne $tempParent -or
        (Split-Path -Leaf $resolved) -notmatch '^mail-memory-tests-[a-f0-9]{32}$' -or
        $resolved.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup outside verified temporary directory: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) {
        $entries = @((Get-Item -LiteralPath $resolved -Force)) + @(Get-ChildItem -LiteralPath $resolved -Recurse -Force)
        if (@($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -gt 0) {
            throw "Refusing cleanup with reparse points: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

Write-Output "Tests: $passed passed, $failed failed. PowerShell $($PSVersionTable.PSVersion)."
if ($failed -gt 0) { exit 1 }
exit 0
