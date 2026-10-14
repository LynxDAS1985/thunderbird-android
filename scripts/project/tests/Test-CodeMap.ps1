#Requires -Version 5.1
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$exporter = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../Export-CodeMap.ps1'))
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..')).TrimEnd('\', '/')
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$fixture = Join-Path $tempParent ('mail-code-map-' + [guid]::NewGuid().ToString('N'))
$engine = Join-Path $PSHOME 'powershell.exe'
$passed = 0

function Assert([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAIL: $Name" }
    $script:passed++
    Write-Output "PASS: $Name"
}
function Write-Fixture([string]$Path, [string]$Content) {
    $destination = Join-Path $fixture $Path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
    [IO.File]::WriteAllText($destination, $Content, (New-Object Text.UTF8Encoding($false)))
}
function Git([string[]]$Arguments) {
    & git -C $fixture @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Fixture git failed: $Arguments" }
}
function Run([string]$Root = $fixture, [switch]$Check, [switch]$Fail, [string]$Message, [switch]$DefaultRoot) {
    $arguments = @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $exporter)
    if (-not $DefaultRoot) { $arguments += @('-ProjectRoot', $Root) }
    if ($Check) { $arguments += '-Check' }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $result = (& $engine @arguments 2>&1 | Out-String); $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    Assert (($Fail -and $code -eq 1) -or (-not $Fail -and $code -eq 0)) "exit code $code; $result"
    if ($Message) { Assert ($result.Contains($Message)) "diagnostic: $Message" }
}
function Snapshot {
    $result = @{}
    foreach ($name in @('files.csv', 'modules.csv', 'summary.json')) {
        $path = Join-Path $fixture "docs/code-map/$name"
        $result[$name] = (Get-FileHash -LiteralPath $path).Hash + ':' + (Get-Item -LiteralPath $path).LastWriteTimeUtc.Ticks
    }
    return $result
}
function Assert-Unchanged($Before) {
    $after = Snapshot
    foreach ($name in $Before.Keys) { Assert ($Before[$name] -ceq $after[$name]) "no writes: $name" }
}

try {
    Assert (-not $fixture.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) 'fixtures outside repository'
    [void][IO.Directory]::CreateDirectory($fixture)
    Run -Root (Join-Path $fixture 'absent') -Fail -Message 'existing Git working-tree root'
    Run -Fail -Message 'Git failed'
    Git @('init', '--quiet')
    Write-Fixture 'build.gradle.kts' '// root'
    Write-Fixture 'parent/build.gradle.kts' 'implementation(projects.core.api); api(project(":legacy")); api(project('':other''))'
    Write-Fixture 'parent/src/main/kotlin/A.kt' 'abcd'
    Write-Fixture 'parent/src/androidUnitTest/kotlin/A.kt' 'test'
    Write-Fixture 'parent/src/main/res/raw/a.xml' '<a/>'
    Write-Fixture 'parent/child/build.gradle.kts' ''
    Write-Fixture 'parent/child/src/commonMain/kotlin/B.kt' 'nested'
    Write-Fixture 'parent/child/src/commonTest/resources/data.txt' 'fixture'
    Write-Fixture 'docs/guide.md' '# Guide'
    $special = 'docs/comma, [1] ' + [char]0x044F + '.md'
    Write-Fixture $special 'UTF8'
    Write-Fixture 'data.bin' 'data'
    Git @('add', '--all')
    # A local fixture commit gives the exporter a HEAD without touching the real index.
    Git @('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'fixture')
    Write-Fixture 'secret-untracked.txt' 'must not appear'
    Write-Fixture 'parent/src/main/build.gradle.kts' 'untracked module must not own files'
    Run -Root (Join-Path $fixture 'parent') -Fail -Message 'not a subdirectory'
    Run -Root (Join-Path $fixture 'data.bin') -Fail -Message 'existing Git working-tree root'
    Run -Check -Fail -Message 'Stale or missing snapshot'
    Assert (-not (Test-Path -LiteralPath (Join-Path $fixture 'docs/code-map'))) 'check creates no output'
    Run
    $rows = @(Import-Csv -LiteralPath (Join-Path $fixture 'docs/code-map/files.csv') -Encoding UTF8)
    $modules = @(Import-Csv -LiteralPath (Join-Path $fixture 'docs/code-map/modules.csv') -Encoding UTF8)
    Assert ($rows.Count -eq 11 -and $modules.Count -eq 3) 'all and only tracked files; nested modules'
    Assert (@($rows | Where-Object { $_.path -ceq $special }).Count -eq 1) 'literal Unicode and comma CSV round trip'
    $main = $rows | Where-Object { $_.path -eq 'parent/src/main/kotlin/A.kt' }
    Assert ($main.moduleDirectory -eq 'parent' -and $main.sourceSet -eq 'main' -and $main.kind -eq 'code' -and $main.bytes -eq 4) 'ownership source set kind and bytes'
    $child = $modules | Where-Object { $_.path -eq 'parent/child' }
    Assert ($child.fileCount -eq 3 -and $child.sourceCount -eq 1 -and $child.testCount -eq 1) 'nearest module counts exclude child from parent'
    $parent = $modules | Where-Object { $_.path -eq 'parent' }
    Assert ($parent.fileCount -eq 4 -and $parent.sourceCount -eq 1 -and $parent.testCount -eq 1) 'parent counts'
    Assert ($parent.explicitProjectReferences.Contains('projects.core.api') -and $parent.explicitProjectReferences.Contains(':legacy') -and $parent.explicitProjectReferences.Contains(':other')) 'both explicit project reference syntaxes'
    Assert (($rows | Where-Object { $_.path -eq 'parent/src/main/res/raw/a.xml' }).kind -eq 'resource') 'resource classification'
    Assert (($rows | Where-Object { $_.path -eq 'data.bin' }).kind -eq 'other') 'other classification'
    [string[]]$sorted = @($rows.path)
    [Array]::Sort($sorted, [StringComparer]::Ordinal)
    Assert (($rows.path -join '|') -ceq ($sorted -join '|')) 'ordinal ordering'
    $before = Snapshot
    Run -Check
    Assert-Unchanged $before
    Run
    $after = Snapshot
    foreach ($name in $before.Keys) { Assert ($before[$name].Split(':')[0] -eq $after[$name].Split(':')[0]) "deterministic bytes: $name" }
    Git @('add', 'docs/code-map')
    Run -Check
    Assert $true 'generated tracked snapshots excluded from self-inclusion'
    $before = Snapshot
    Write-Fixture 'parent/src/main/kotlin/A.kt' 'wxyz'
    Run -Check -Fail -Message 'Stale or missing snapshot'
    Assert-Unchanged $before
    Write-Fixture 'parent/src/main/kotlin/A.kt' 'abcd'
    Remove-Item -LiteralPath (Join-Path $fixture 'parent/src/main/kotlin/A.kt')
    Run -Fail -Message 'Missing tracked file'
    Run -Check -Fail -Message 'Missing tracked file'
    Assert-Unchanged $before
    Write-Fixture 'parent/src/main/kotlin/A.kt' 'abcd'
    Write-Fixture 'docs/code-map/modules.csv' 'corrupt'
    Run -Check -Fail -Message 'modules.csv'
    Run
    Write-Fixture 'new.txt' 'new tracked file'
    Git @('add', 'new.txt')
    Run -Check -Fail -Message 'Stale or missing snapshot'
    Run
    # Exercise PSScriptRoot while the caller remains in the original repository.
    $originalExporter = $exporter
    $exporter = Join-Path $fixture 'scripts/project/Export-CodeMap.ps1'
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($exporter))
    [IO.File]::Copy($originalExporter, $exporter)
    Run -DefaultRoot -Check
    Git @('rm', '--cached', '--quiet', '--', 'build.gradle.kts', 'parent/build.gradle.kts', 'parent/child/build.gradle.kts')
    Run
    Run -Check
    $rows = @(Import-Csv -LiteralPath (Join-Path $fixture 'docs/code-map/files.csv') -Encoding UTF8)
    Assert (@($rows | Where-Object { $_.moduleDirectory }).Count -eq 0) 'untracked build files ignored; no-module repository supported'
    Assert ((Get-Content -LiteralPath (Join-Path $fixture 'docs/code-map/modules.csv')).Count -eq 1) 'empty modules retain CSV header'
    Write-Output "Tests passed: $passed. PowerShell $($PSVersionTable.PSVersion)."
}
finally {
    $resolved = [IO.Path]::GetFullPath($fixture)
    if ((Split-Path -Parent $resolved) -ne $tempParent -or
        (Split-Path -Leaf $resolved) -notmatch '^mail-code-map-[a-f0-9]{32}$' -or
        $resolved.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing cleanup: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) {
        if (@(Get-ChildItem -LiteralPath $resolved -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
            throw "Refusing cleanup with reparse points: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
