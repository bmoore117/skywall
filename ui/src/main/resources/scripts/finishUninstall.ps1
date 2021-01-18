Unregister-ScheduledTask -TaskName 'Ping SkyWall' -Confirm:$false
Unregister-ScheduledTask -TaskName 'Restart SkyWall on Network Change' -Confirm:$false
Remove-NetFirewallRule -DisplayName "SkyWall - Block QUIC Protocol" -ErrorAction SilentlyContinue
Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Subject -match 'mitmproxy' } | Remove-Item
Remove-Item ~\.mitmproxy\ -Recurse

Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs

$json = Get-Content ~\AppData\Local\SkyWall\data\config.json | ConvertFrom-Json
if ($json.formerAdminUsers) {
    $formerAdminUsers = $json.formerAdminUsers -join "~,~"
    ~\AppData\Local\SkyWall\scripts\toggleStrictMode.ps1 -mode off -usersToRestore $formerAdminUsers
}

~\AppData\Local\SkyWall\scripts\changePassword.ps1 -password "P@ssw0rd"

rm -Recurse -Force ~\AppData\Local\SkyWall