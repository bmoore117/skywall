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

if ((Test-Path -Path "C:\WINDOWS\system32\config\systemprofile\.mitmproxy") -eq $false) {
    Write-Host "Adding certificate to store on first run"
    do {
        Start-Sleep -Seconds 3
    } while ((Test-Path -Path "C:\WINDOWS\system32\config\systemprofile\.mitmproxy") -eq $false)
    certutil -addstore root C:\WINDOWS\system32\config\systemprofile\.mitmproxy\mitmproxy-ca-cert.cer
}

Set-Location $PSScriptRoot

$json = Get-Content .\filter\monitor\hosts.json | ConvertFrom-Json
$ignoredHosts = $json.ignoredHosts -join "|"

if ([string]::IsNullOrEmpty($ignoredHosts)) {
    Write-Host "Launching with no ignored hosts"
    mitmdump.exe --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py
} else {
    Write-Host "Launching with ignored hosts" $ignoredHosts
    mitmdump.exe --mode transparent --set block_global=false --ssl-insecure --no-http2 -s .\skywall-filter.py --ignore-hosts $ignoredHosts
}
