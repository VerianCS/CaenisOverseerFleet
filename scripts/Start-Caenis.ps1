param([switch]$WithNiFi)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $taskRoot
try {
  if (-not (Test-Path -LiteralPath '.env')) { & (Join-Path $PSScriptRoot 'Initialize-Caenis.ps1') }
  $taskArguments = @('compose')
  if ($WithNiFi) { $taskArguments += @('--profile','nifi') }
  $taskArguments += @('up','--build','-d')
  & docker @taskArguments
  if ($LASTEXITCODE -ne 0) { throw 'Docker could not start the platform. See its output above.' }
  Write-Output 'Caenis services started. Open the public origin configured in .env and sign in with its administrator credentials.'
} finally { Pop-Location }
