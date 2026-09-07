$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $taskRoot
try {
  & (Join-Path $taskRoot 'gradlew.bat') ':paper-plugin:shadowJar' ':backend-service:bootJar' ':backend-service-servermanager:bootJar' '--no-daemon' '-x' 'test'
  if ($LASTEXITCODE -ne 0) { throw 'Artifact compilation failed.' }
  $taskOutput = Join-Path $taskRoot 'dist'
  [IO.Directory]::CreateDirectory($taskOutput) | Out-Null
  foreach ($taskJar in @('paper-plugin/build/libs/caenis-overseer.jar','backend-service/build/libs/caenis-core.jar','backend-service-servermanager/build/libs/caenis-manager.jar')) {
    Copy-Item -LiteralPath (Join-Path $taskRoot $taskJar) -Destination $taskOutput
  }
  Write-Output 'Packaged the agent, core and host manager in dist. No tests were run.'
} finally { Pop-Location }
