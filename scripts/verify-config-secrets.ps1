param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string[]] $Path
)

$pattern = '(?im)^\h*\K(?:api-key|app-secret|password)(?=\h*:(?!\h*\$\{[A-Z][A-Z0-9_]*(?::)?\}(?:\h*#.*)?\h*$))'
$matches = & rg --pcre2 --line-number --with-filename --no-heading --color never --only-matching $pattern @Path
$rgExitCode = $LASTEXITCODE

if ($rgExitCode -eq 0) {
    $matches
    exit 1
}

if ($rgExitCode -eq 1) {
    Write-Output 'PASS: no literal credential scalars detected'
    exit 0
}

Write-Error "Credential scan could not run (rg exit code $rgExitCode)"
exit 2
