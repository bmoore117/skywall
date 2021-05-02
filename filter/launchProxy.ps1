

$jsonLocation = "~\AppData\Local\SkyWall\filter\hosts\hosts.json"
$json = Get-Content $jsonLocation | ConvertFrom-Json
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