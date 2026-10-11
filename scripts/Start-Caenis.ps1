$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $taskRoot
try {
  if (-not (Test-Path -LiteralPath '.env')) { & (Join-Path $PSScriptRoot 'Initialize-Caenis.ps1') }
  & docker compose up --build -d
  if ($LASTEXITCODE -ne 0) { throw 'Docker could not start the platform. See its output above.' }
  Write-Output 'Caenis services started. Open the public origin configured in .env and sign in with its administrator credentials.'
} finally { Pop-Location }
