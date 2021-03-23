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

if ([string]::IsNullOrEmpty($ignoredHosts)) {
    Write-Host "Launching with no ignored hosts"
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py
} else {
    Write-Host "Launching with ignored hosts" $ignoredHosts
    .\dist\Python39\python.exe .\mitmdump.py --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py --ignore-hosts $ignoredHosts
}