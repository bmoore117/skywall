if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] “Administrator”)) {
    Write-Warning “Please re-run this script as an Administrator”
    exit 1
}

$installDir = "C:\Program Files\SkyWall"
$null = New-Item -Type Directory -Force -Path $installDir

Write-Host "Copying dist files"
cp -Recurse .\dist $installDir

New-NetFirewallRule -Name "SkyWall - Allow Filter Inbound" -DisplayName "SkyWall - Allow Filter Inbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Inbound
New-NetFirewallRule -Name "SkyWall - Allow Filter Outbound" -DisplayName "SkyWall - Allow Filter Outbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Outbound
New-NetFirewallRule -Name "SkyWall - Block QUIC Protocol" -DisplayName "SkyWall - Block QUIC Protocol" -Action Block -Profile Any -Direction Outbound -Protocol UDP -RemotePort 80,443

$principal = New-ScheduledTaskPrincipal -UserID "NT AUTHORITY\SYSTEM" -LogonType ServiceAccount -RunLevel Highest

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-File $installDir\scripts\ping.ps1"
$stateChangeTrigger = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskSessionStateChangeTrigger
# TASK_SESSION_STATE_CHANGE_TYPE.TASK_SESSION_UNLOCK (taskschd.h)
$onUnlockTrigger = New-CimInstance -CimClass $stateChangeTrigger -Property @{ StateChange = 8 } -ClientOnly
$pingSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $action -Trigger $onUnlockTrigger -TaskName "Ping SkyWall" -Principal $principal -Settings $pingSettings

$restartAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-File $installDir\scripts\restartService.ps1 -serviceName ""SkyWall Filter"""
$networkChangeClass = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskEventTrigger
$subscription = @"
<QueryList><Query Id="0" Path="Microsoft-Windows-NetworkProfile/Operational"><Select Path="Microsoft-Windows-NetworkProfile/Operational">*[System[Provider[@Name='Microsoft-Windows-NetworkProfile'] and EventID=10000]]</Select></Query></QueryList>
"@
$onNetworkChange = New-CimInstance -CimClass $networkChangeClass -Property @{ Subscription = $subscription } -ClientOnly
$restartSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $restartAction -Trigger $onNetworkChange -TaskName "Restart SkyWall on Network Change" -Principal $principal -Settings $restartSettings

.\deploy.ps1

$folder = Get-Item $installDir\data
$folder.Attributes = $folder.Attributes -bor "Hidden"
$folder = Get-Item $installDir\filter
$folder.Attributes = $folder.Attributes -bor "Hidden"

cd $installDir
.\skywall-ui.exe install
.\skywall-filter.exe install

[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine) + ";$installDir\dist\Python39\Scripts", [EnvironmentVariableTarget]::Machine)

Start-Service "SkyWall UI"
Start-Service "SkyWall Filter"

Write-Host "Install completed, exiting in 5 seconds"
Start-Sleep -Seconds 5
