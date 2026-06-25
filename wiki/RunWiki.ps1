$ErrorActionPreference = 'Stop'

param(
    [ValidateSet('serve', 'build', 'setup', 'help')]
    [string] $Command = 'serve'
)

$Root = Resolve-Path (Join-Path $PSScriptRoot '..')
$SystemPython = if ($env:PYTHON) { $env:PYTHON } else { 'python' }
$Venv = Join-Path $Root '.venv-wiki'
$VenvPython = Join-Path $Venv 'Scripts\python.exe'

function Show-Usage {
    Write-Host 'Usage: .\wiki\RunWiki.ps1 [serve|build|setup]'
    Write-Host ''
    Write-Host 'Commands:'
    Write-Host '  serve   Start the local wiki server. This is the default.'
    Write-Host '  build   Build the static wiki site with strict validation.'
    Write-Host '  setup   Create/update the local wiki Python environment.'
}

function Install-WikiEnvironment {
    Write-Host "Preparing wiki environment at $Venv"
    & $SystemPython -m venv $Venv
    & $VenvPython -m pip install --upgrade pip
    & $VenvPython -m pip install -r (Join-Path $Root 'requirements-docs.txt')
}

function Ensure-WikiEnvironment {
    if (-not (Test-Path $VenvPython)) {
        Install-WikiEnvironment
        return
    }

    & $VenvPython -m mkdocs --version *> $null
    if ($LASTEXITCODE -ne 0) {
        Install-WikiEnvironment
    }
}

switch ($Command) {
    'setup' {
        Install-WikiEnvironment
        Write-Host 'Wiki environment is ready.'
    }
    'serve' {
        Ensure-WikiEnvironment
        Push-Location $Root
        try {
            & $VenvPython -m mkdocs serve
        } finally {
            Pop-Location
        }
    }
    'build' {
        Ensure-WikiEnvironment
        Push-Location $Root
        try {
            & $VenvPython -m mkdocs build --strict
        } finally {
            Pop-Location
        }
    }
    'help' {
        Show-Usage
    }
}
