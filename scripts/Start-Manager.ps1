param([string]$ServersRoot = (Join-Path $PSScriptRoot '../servers'), [string]$Configuration = (Join-Path $PSScriptRoot '../runtime/application-manager.yml'), [string]$Bind = '127.0.0.1')
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskValues = @{}
foreach ($taskLine in [IO.File]::ReadAllLines((Join-Path $taskRoot '.env'))) {
  if ($taskLine -match '^([^#=]+)=(.*)$') { $taskValues[$Matches[1]] = $Matches[2] }
}
$taskJar = Join-Path $taskRoot 'dist/caenis-manager.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Package the JVM artifacts first with scripts/Package-Caenis.ps1.' }
$taskConfigPath = [IO.Path]::GetFullPath($Configuration)
if (-not (Test-Path -LiteralPath $taskConfigPath)) { throw 'Copy docker/manager/application-manager.example.yml to runtime/application-manager.yml and configure your server directories.' }
$taskLogs = Join-Path $taskRoot 'runtime/manager'
[IO.Directory]::CreateDirectory($taskLogs) | Out-Null
$taskStart = [Diagnostics.ProcessStartInfo]::new('java')
$taskStart.UseShellExecute = $false
$taskStart.CreateNoWindow = $true
$taskStart.WorkingDirectory = $taskRoot
$taskStart.Environment['CAENIS_MANAGER_KEY'] = $taskValues['CAENIS_MANAGER_KEY']
$taskStart.Environment['CAENIS_SERVERS_ROOT'] = [IO.Path]::GetFullPath($ServersRoot)
$taskStart.Environment['CAENIS_MANAGER_BIND'] = $Bind
foreach ($taskArgument in @('-jar',$taskJar,('--spring.config.additional-location=file:' + ($taskConfigPath -replace '\\','/')))) { $taskStart.ArgumentList.Add($taskArgument) }
$taskProcess = [Diagnostics.Process]::Start($taskStart)
[IO.File]::WriteAllText((Join-Path $taskLogs 'manager.pid'), [string]$taskProcess.Id)
Write-Output ('Host manager started in the background. PID ' + $taskProcess.Id + '. It can control only configured server directories.')
