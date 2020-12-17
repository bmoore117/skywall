Stop-Service -Name "SkyWall UI"
Stop-Service -Name "SkyWall Filter"

# Remove WinDivert driver
# https://docs.microsoft.com/en-us/windows-hardware/drivers/ifs/using-an-inf-file-to-uninstall-a-file-system-filter-driver
Get-CimInstance Win32_SystemDriver -Filter "name='WinDivert1.3'" | Invoke-CimMethod -MethodName Delete

cd C:\Users\skywall\skywall
.\skywall-ui.exe uninstall
.\skywall-filter.exe uninstall

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