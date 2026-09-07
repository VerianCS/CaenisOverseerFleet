param([string]$Destination)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $Destination) { $Destination = Join-Path $taskRoot ('runtime/backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump') }
$taskFile = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $taskFile) { throw 'The backup destination already exists.' }
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($taskFile)) | Out-Null
Push-Location -LiteralPath $taskRoot
try {
  # Let docker cp transfer the binary archive; do not pipe binary data through PowerShell.
  & docker compose exec -T postgres pg_dump -U caenis_owner -d caenis_overseer -Fc -f /tmp/caenis-backup.dump
  if ($LASTEXITCODE -ne 0) { throw 'Database export failed.' }
  & docker compose cp 'postgres:/tmp/caenis-backup.dump' $taskFile
  if ($LASTEXITCODE -ne 0) { throw 'Could not copy the database archive.' }
  Write-Output 'Database backup created. Keep the encryption key from .env with a separately protected backup of configuration.'
} finally { Pop-Location }
