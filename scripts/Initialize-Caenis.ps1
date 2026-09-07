param([string]$PublicOrigin = 'http://localhost:3000')
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskEnvironment = Join-Path $taskRoot '.env'
if (Test-Path -LiteralPath $taskEnvironment) { throw 'A .env already exists. It was left unchanged.' }
$taskUri = [Uri]$PublicOrigin
if ($taskUri.Scheme -notin @('http','https') -or $taskUri.AbsolutePath -ne '/') { throw 'PublicOrigin must be an HTTP(S) origin without a path.' }
function New-CaenisSecret {
  $taskBytes = [byte[]]::new(32)
  [Security.Cryptography.RandomNumberGenerator]::Fill($taskBytes)
  [Convert]::ToBase64String($taskBytes)
}
$taskSecureCookies = if ($taskUri.Scheme -eq 'https') { 'true' } else { 'false' }
$taskValues = [ordered]@{
  CAENIS_PUBLIC_ORIGIN = $PublicOrigin.TrimEnd('/')
  CAENIS_SECURE_COOKIES = $taskSecureCookies
  CAENIS_ADMIN_USERNAME = 'admin'
  CAENIS_ADMIN_PASSWORD = (New-CaenisSecret)
  CAENIS_SESSION_SECRET = (New-CaenisSecret)
  CAENIS_ENCRYPTION_KEY = (New-CaenisSecret)
  DATABASE_PASSWORD = (New-CaenisSecret)
  DATABASE_OWNER_PASSWORD = (New-CaenisSecret)
  CAENIS_MANAGER_KEY = (New-CaenisSecret)
  NIFI_USERNAME = 'caenis'
  NIFI_PASSWORD = (New-CaenisSecret)
  NIFI_SENSITIVE_KEY = (New-CaenisSecret)
  CAENIS_BIND = '127.0.0.1'
  CAENIS_HTTP_PORT = '3000'
}
$taskLines = $taskValues.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }
[IO.File]::WriteAllLines($taskEnvironment, $taskLines, [Text.UTF8Encoding]::new($false))
if ($IsWindows) {
  $taskIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  $taskAcl = Get-Acl -LiteralPath $taskEnvironment
  $taskAcl.SetAccessRuleProtection($true, $false)
  $taskAcl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new($taskIdentity, 'FullControl', 'Allow'))
  Set-Acl -LiteralPath $taskEnvironment -AclObject $taskAcl
} else { & chmod 600 $taskEnvironment }
Write-Output 'Created .env with private local credentials. No services have been started.'
