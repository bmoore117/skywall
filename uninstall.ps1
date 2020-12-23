Stop-Service -Name "SkyWall UI"
Stop-Service -Name "SkyWall Filter"

# the method used in pydivert - WinDivert is a hidden service that does not show up in services.msc
sc.exe stop WinDivert1.3

cd C:\Users\skywall\skywall
.\skywall-ui.exe uninstall
.\skywall-filter.exe uninstall

$json = Get-Content .\data\config.json | ConvertFrom-Json
if ($json.formerAdminUsers) {
    $formerAdminUsers = $json.formerAdminUsers -join "~,~"
    .\scripts\toggleStrictMode.ps1 -mode off -usersToRestore $formerAdminUsers
}


cd ..
rm -Recurse .\skywall

Remove-NetFirewallRule -Name "SkyWall - Allow Filter Inbound"
Remove-NetFirewallRule -Name "SkyWall - Allow Filter Outbound"
Remove-NetFirewallRule -Name "SkyWall - Block QUIC Protocol"

Unregister-ScheduledTask -TaskName "Restart SkyWall on Network Change" -Confirm:$false
Unregister-ScheduledTask -TaskName "Ping SkyWall" -Confirm:$false

$path = [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine)
$path = $path.Replace(";C:\Users\skywall\skywall\dist\Python39\Scripts", "");
[Environment]::SetEnvironmentVariable("Path", $path, [EnvironmentVariableTarget]::Machine)

Get-ChildItem Cert:\LocalMachine\Root |Where-Object { $_.Subject -match 'mitmproxy' } | Remove-Item
Remove-Item C:\Windows\System32\config\systemprofile\.mitmproxy\ -Recurse

Write-Host "Uninstall completed successfully"