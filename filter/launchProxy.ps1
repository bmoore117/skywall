$jsonLocation = "~\AppData\Local\SkyWall\filter\hosts\hosts.json"
$json = Get-Content $jsonLocation | ConvertFrom-Json
if ($json.filterActive -eq $false) {
    Write-Host "Aborting startup as filter set to OFF, re-enable from UI"
    return
}

$startupAttemptsFile = "~\AppData\Local\SkyWall\filter\startup.json"

if (-not (Test-Path $startupAttemptsFile)) {
    $file = '{"times": []}'
    $file | Out-File $startupAttemptsFile
}

$json = Get-Content $startupAttemptsFile | ConvertFrom-Json
$now = Get-Date

# Here we attempt to determine if the user has been gaming the small filter startup delay by disconnecting
# and reconnecting to the network. If more than 3 attempts in the last 15 minutes, disable internet and exit
$connections = 0
foreach ($time in $json.times) {
    $date = [DateTime]$time
    $span = New-TimeSpan -Start $date -End $now
    if ($span.Minutes -lt 15) {
        $connections = $connections + 1
    }
}

if ($connections -ge 3) {
    Write-Host "Filter restarts exceeded, disabling internet"
    $adapters = Get-NetAdapter | Where Name -NotMatch "Bluetooth"
    foreach ($adapter in $adapters) {
        Disable-NetAdapter -Name $adapter.Name -Confirm:$false
    }

    Invoke-RestMethod -Uri http://localhost:9090/rest/scheduleUnlock -Method Post
    return
} else {
    $adapters = Get-NetAdapter | Where Name -NotMatch "Bluetooth"
    foreach ($adapter in $adapters) {
        if ($adapter.Status -eq "Disabled") {
            Enable-NetAdapter $adapter.Name -Confirm:$false
        }
    }

    $dateStr = Get-Date -Format "MM/dd/yyyy HH:mm"
    if ($json.times.Length -ge 3) {
        $json.times = @()
    }
    $json.times += $dateStr
    $jsonFile = $json | ConvertTo-Json
    $jsonFile | Out-File $startupAttemptsFile
}

Write-Host "Checking internet connection..."
# https://stackoverflow.com/questions/33283848/determining-internet-connection-using-powershell
$results = Get-NetRoute | ? DestinationPrefix -eq '0.0.0.0/0' | Get-NetIPInterface | Where ConnectionState -eq 'Connected'
$hasConnection = $results -ne $null
$tries = 0
while ($hasConnection -eq $false -and $tries -lt 3) {
    Write-Host "Waiting for connection, period " ($tries + 1)
    $tries = $tries + 1
    Start-Sleep -Seconds 5
    $results = Get-NetRoute | ? DestinationPrefix -eq '0.0.0.0/0' | Get-NetIPInterface | Where ConnectionState -eq 'Connected'
    $hasConnection = $results -ne $null
}

if ($hasConnection -eq $false) {
    Write-Host "No internet connection was found after 3 wait periods, aborting launch"
    return
}

Set-Location $PSScriptRoot

$json = Get-Content $jsonLocation | ConvertFrom-Json
$ignoredHosts = $json.ignoredHosts -join "|"

# make sure no one thought they'd be clever and jam the proxy on startup by claiming the ports
$startingPort = 8080 # default taken from windows.py
$connections = Get-NetTCPConnection -LocalPort $startingPort -ErrorAction SilentlyContinue
while ($connections -ne $null) {
    $startingPort = $startingPort + 1
    $connections = Get-NetTCPConnection -LocalPort $startingPort -ErrorAction SilentlyContinue
}
$startingPort | Out-File -FilePath $env:LOCALAPPDATA\SkyWall\port.txt -Encoding ascii

$redirectAPIPort = 8085 # default taken from windows.py
$connections = Get-NetTCPConnection -LocalPort $redirectAPIPort -ErrorAction SilentlyContinue
while ($connections -ne $null) {
    $redirectAPIPort = $redirectAPIPort + 1
    $connections = Get-NetTCPConnection -LocalPort $redirectAPIPort -ErrorAction SilentlyContinue
}
$redirectAPIPort | Out-File -FilePath $env:LOCALAPPDATA\SkyWall\redirectport.txt -Encoding ascii


if ([string]::IsNullOrEmpty($ignoredHosts)) {
    Write-Host "Launching with no ignored hosts"
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py --listen-port $startingPort
} else {
    Write-Host "Launching with ignored hosts" $ignoredHosts
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py --ignore-hosts $ignoredHosts --listen-port $startingPort
}