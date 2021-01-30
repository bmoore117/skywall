if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] “Administrator”)) {
    Write-Warning “Please re-run this script as an Administrator”
    exit 1
}

$origLocation = Get-Location

$installDir = "C:\Program Files\SkyWall"
$null = New-Item -Type Directory -Force -Path $installDir

Write-Host "Copying dist files"
cp -Recurse .\dist $installDir

New-NetFirewallRule -Name "SkyWall - Allow Filter Inbound" -DisplayName "SkyWall - Allow Filter Inbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Inbound
New-NetFirewallRule -Name "SkyWall - Allow Filter Outbound" -DisplayName "SkyWall - Allow Filter Outbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Outbound
New-NetFirewallRule -Name "SkyWall - Block QUIC Protocol" -DisplayName "SkyWall - Block QUIC Protocol" -Action Block -Profile Any -Direction Outbound -Protocol UDP -RemotePort 80,443

$principal = New-ScheduledTaskPrincipal -UserID "NT AUTHORITY\SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$actionStr = "-Command ""& {Invoke-RestMethod -Uri http://localhost:9090/rest/ping -Method Post -Body '{}' -ContentType 'application/json'}"""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $actionStr
$stateChangeTrigger = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskSessionStateChangeTrigger
# TASK_SESSION_STATE_CHANGE_TYPE.TASK_SESSION_UNLOCK (taskschd.h)
$onUnlockTrigger = New-CimInstance -CimClass $stateChangeTrigger -Property @{ StateChange = 8 } -ClientOnly
$pingSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $action -Trigger $onUnlockTrigger -TaskName "Ping SkyWall" -Principal $principal -Settings $pingSettings

$actionStr = "-Command ""& {Restart-Service -Name 'SkyWall Filter' }"""
$restartAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $actionStr
$networkChangeClass = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskEventTrigger
# if you put spaces in the xml below, task scheduler will think it's a custom filter. Without spaces it will actually recognize the specific event and show it. Event 10000 is for new connection, event 10001 is for disconnection.
# turns out we only need 10000 for disconnecting and reconnecting from home wifi / ethernet
$newConnectionSub = @"
<QueryList><Query Id="0" Path="Microsoft-Windows-NetworkProfile/Operational"><Select Path="Microsoft-Windows-NetworkProfile/Operational">*[System[Provider[@Name='Microsoft-Windows-NetworkProfile'] and EventID=10000]]</Select></Query></QueryList>
"@
$onNewConnection = New-CimInstance -CimClass $networkChangeClass -Property @{ Subscription = $newConnectionSub } -ClientOnly
$restartSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $restartAction -Trigger $onNewConnection -TaskName "Restart SkyWall on Network Change" -Principal $principal -Settings $restartSettings


.\deploy.ps1

cd $installDir
.\skywall-ui.exe install
.\skywall-filter.exe install

Start-Service "SkyWall UI"
Start-Service "SkyWall Filter"

cd $origLocation
Write-Host "Install completed, exiting in 5 seconds"
Start-Sleep -Seconds 5