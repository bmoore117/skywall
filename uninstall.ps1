$startLocation = Get-Location

Write-Host "Stopping Services"
Stop-Service -Name "SkyWall UI"
Stop-Service -Name "SkyWall Filter"

# the method used in pydivert - WinDivert is a hidden service that does not show up in services.msc
sc.exe stop WinDivert1.3

Write-Host "Unregistering Services"
$installDir = "C:\Program Files\SkyWall"
if (Test-Path $installDir) {
    cd $installDir
    .\skywall-ui.exe uninstall
    .\skywall-filter.exe uninstall

    Write-Host "Restoring former admin users"
    $json = Get-Content C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall\data\config.json | ConvertFrom-Json
    if ($json.formerAdminUsers) {
        $formerAdminUsers = $json.formerAdminUsers -join "~,~"
        C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall\scripts\toggleStrictMode.ps1 -mode off -usersToRestore $formerAdminUsers
    }

    Write-Host "Deleting SkyWall install files"
    cd ..
    rm -Recurse -Force .\SkyWall

    Write-Host "Deleting SkyWall config files"
    cd ..
    rm -Recurse -Force C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall
}

Write-Host "Deleting firewall rules"
Remove-NetFirewallRule -Name "SkyWall - Allow Filter Inbound" -ErrorAction SilentlyContinue
Remove-NetFirewallRule -Name "SkyWall - Allow Filter Outbound" -ErrorAction SilentlyContinue
Remove-NetFirewallRule -Name "SkyWall - Block QUIC Protocol" -ErrorAction SilentlyContinue

Write-Host "Deleting Scheduled Tasks"
Unregister-ScheduledTask -TaskName "Restart SkyWall on Network Change" -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "Ping SkyWall" -Confirm:$false -ErrorAction SilentlyContinue

$user = Get-LocalUser -Name "skywall" -ErrorAction SilentlyContinue
if ($user -ne $null) {
    Write-Host "Deleting SkyWall User"
    Remove-LocalUser $user
    rm -Recurse -Force C:\Users\skywall
}

Write-Host "Removing installed certificates"
Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Subject -match 'mitmproxy' } | Remove-Item
if (Test-Path C:\Windows\System32\config\systemprofile\.mitmproxy) {
    Remove-Item C:\Windows\System32\config\systemprofile\.mitmproxy -Recurse
}

Write-Host "Clearing logs"
Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\filter\*
Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\ui\*

Write-Host "Uninstall completed"
cd $startLocation