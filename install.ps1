if ($env:USERNAME -ne "skywall") {
    Write-Host "Script must be run as skywall user created by createUser.ps1"
    exit
}

$null = New-Item -Type Directory -Force -Path C:\Users\skywall\skywall

Write-Host "Copying dist files"
cp -Recurse .\dist C:\Users\skywall\skywall\

New-NetFirewallRule -Name "SkyWall - Allow Filter Inbound" -DisplayName "SkyWall - Allow Filter Inbound" -Program "C:\Users\skywall\skywall\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Inbound
New-NetFirewallRule -Name "SkyWall - Allow Filter Outbound" -DisplayName "SkyWall - Allow Filter Outbound" -Program "C:\Users\skywall\skywall\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Outbound
New-NetFirewallRule -Name "SkyWall - Block QUIC Protocol" -DisplayName "SkyWall - Block QUIC Protocol" -Action Block -Profile Any -Direction Outbound -Protocol UDP -RemotePort 80,443

$principal = New-ScheduledTaskPrincipal -UserID "NT AUTHORITY\SYSTEM" -LogonType ServiceAccount -RunLevel Highest

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-File C:\Users\skywall\skywall\scripts\ping.ps1'
$stateChangeTrigger = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskSessionStateChangeTrigger
# TASK_SESSION_STATE_CHANGE_TYPE.TASK_SESSION_UNLOCK (taskschd.h)
$onUnlockTrigger = New-CimInstance -CimClass $stateChangeTrigger -Property @{ StateChange = 8 } -ClientOnly
$pingSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $action -Trigger $onUnlockTrigger -TaskName "Ping SkyWall" -Principal $principal -Settings $pingSettings

$restartAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-File C:\Users\skywall\skywall\scripts\restartService.ps1 -serviceName "SkyWall Filter"'
$networkChangeClass = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskEventTrigger
$subscription = @"
<QueryList><Query Id="0" Path="Microsoft-Windows-NetworkProfile/Operational"><Select Path="Microsoft-Windows-NetworkProfile/Operational">*[System[Provider[@Name='Microsoft-Windows-NetworkProfile'] and EventID=10000]]</Select></Query></QueryList>
"@
$onNetworkChange = New-CimInstance -CimClass $networkChangeClass -Property @{ Subscription = $subscription } -ClientOnly
$restartSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
Register-ScheduledTask -Action $restartAction -Trigger $onNetworkChange -TaskName "Restart SkyWall on Network Change" -Principal $principal -Settings $restartSettings

.\deploy.ps1

cp -Recurse .\ui\data C:\Users\skywall\skywall\
cp -Recurse .\filter\filter C:\Users\skywall\skywall\

cd C:\Users\skywall\skywall
.\skywall-ui.exe install
.\skywall-filter.exe install

[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine) + ";C:\Users\skywall\skywall\dist\Python39\Scripts",
    [EnvironmentVariableTarget]::Machine)

Start-Service "SkyWall UI"
Start-Service "SkyWall Filter"

while ((Test-Path -Path "C:\WINDOWS\system32\config\systemprofile\.mitmproxy") -eq $false) {
    Start-Sleep -Seconds 3
}
certutil -addstore root C:\WINDOWS\system32\config\systemprofile\.mitmproxy\mitmproxy-ca-cert.cer

