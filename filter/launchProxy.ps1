$json = Get-Content .\filter\hosts.json | ConvertFrom-Json
if ($json.filterActive -eq $false) {
    Write-Host "Aborting startup as filter set to OFF, re-enable from UI"
    return
}

Start-Sleep -Seconds 5

Write-Host "Checking internet connection..."
# https://stackoverflow.com/questions/33283848/determining-internet-connection-using-powershell
$results = Get-NetRoute | ? DestinationPrefix -eq '0.0.0.0/0' | Get-NetIPInterface | Where ConnectionState -eq 'Connected'
$hasConnection = $results -ne $null
$tries = 0
while ($hasConnection -eq $false -and $tries -lt 3) {
    Write-Host "Waiting for connection, period " ($tries + 1)
    $tries = $tries + 1
    Start-Sleep -Seconds 30
    $results = Get-NetRoute | ? DestinationPrefix -eq '0.0.0.0/0' | Get-NetIPInterface | Where ConnectionState -eq 'Connected'
    $hasConnection = $results -ne $null
}

if ($hasConnection -eq $false) {
    Write-Host "No internet connection was found after 3 wait periods, aborting launch"
    return
}

$principal = New-ScheduledTaskPrincipal -UserID "NT AUTHORITY\SYSTEM" -LogonType ServiceAccount -RunLevel Highest

if ((Get-ScheduledTask -TaskName "Ping SkyWall" -ErrorAction SilentlyContinue) -eq $null) {
    $actionStr = @'
-Command "& {if ((Get-AppxPackage -Name SkyWall) -ne $null -or $env:SKYWALL_SCRIPT_INSTALL) { Invoke-RestMethod -Uri http://localhost:9090/ping -Method Post } else { Unregister-ScheduledTask -TaskName 'Ping SkyWall' -Confirm:$false; Unregister-ScheduledTask -TaskName 'Restart SkyWall on Network Change' -Confirm:$false; Remove-NetFirewallRule -DisplayName "SkyWall - Block QUIC Protocol" -ErrorAction SilentlyContinue; Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Subject -match 'mitmproxy' } | Remove-Item; Remove-Item ~\.mitmproxy\ -Recurse; rm -Recurse -Force ~\AppData\Local\SkyWall }}"
'@

    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $actionStr
    $stateChangeTrigger = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskSessionStateChangeTrigger
    # TASK_SESSION_STATE_CHANGE_TYPE.TASK_SESSION_UNLOCK (taskschd.h)
    $onUnlockTrigger = New-CimInstance -CimClass $stateChangeTrigger -Property @{ StateChange = 8 } -ClientOnly
    $pingSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
    Register-ScheduledTask -Action $action -Trigger $onUnlockTrigger -TaskName "Ping SkyWall" -Principal $principal -Settings $pingSettings
}

if ((Get-ScheduledTask -TaskName "Restart SkyWall on Network Change" -ErrorAction SilentlyContinue) -eq $null) {
    $actionStr = @'
-Command "& {if ((Get-AppxPackage -Name SkyWall) -ne $null -or $env:SKYWALL_SCRIPT_INSTALL) { Restart-Service -Name "SkyWall Filter" } else { Unregister-ScheduledTask -TaskName 'Restart SkyWall on Network Change' -Confirm:$false }}"
'@
    $restartAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $actionStr
    $networkChangeClass = Get-CimClass -Namespace ROOT\Microsoft\Windows\TaskScheduler -ClassName MSFT_TaskEventTrigger
    $subscription = @"
<QueryList><Query Id="0" Path="Microsoft-Windows-NetworkProfile/Operational"><Select Path="Microsoft-Windows-NetworkProfile/Operational">*[System[Provider[@Name='Microsoft-Windows-NetworkProfile'] and EventID=10000]]</Select></Query></QueryList>
"@
    $onNetworkChange = New-CimInstance -CimClass $networkChangeClass -Property @{ Subscription = $subscription } -ClientOnly
    $restartSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries
    Register-ScheduledTask -Action $restartAction -Trigger $onNetworkChange -TaskName "Restart SkyWall on Network Change" -Principal $principal -Settings $restartSettings
}

if ((Get-NetFirewallRule -DisplayName "SkyWall - Block QUIC Protocol" -ErrorAction SilentlyContinue) -eq $null) {
    New-NetFirewallRule -Name "SkyWall - Block QUIC Protocol" -DisplayName "SkyWall - Block QUIC Protocol" -Action Block -Profile Any -Direction Outbound -Protocol UDP -RemotePort 80,443
}

Set-Location $PSScriptRoot

$json = Get-Content ~\AppData\Local\SkyWall\filter\hosts.json | ConvertFrom-Json
$ignoredHosts = $json.ignoredHosts -join "|"

if ([string]::IsNullOrEmpty($ignoredHosts)) {
    Write-Host "Launching with no ignored hosts"
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py
} else {
    Write-Host "Launching with ignored hosts" $ignoredHosts
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py --ignore-hosts $ignoredHosts
}