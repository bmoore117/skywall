$password = Invoke-RestMethod -Uri http://localhost:9090/rest/getStockPassword

$user = Get-LocalUser -Name "skywall"

# Convert to SecureString
$secStringPassword = ConvertTo-SecureString $password -AsPlainText -Force
$credObject = New-Object System.Management.Automation.PSCredential ($user.Name, $secStringPassword)

$shouldResetPassword = "false"
Start-Process -FilePath cmd.exe /c -Credential $credObject
if ($?) {
    $shouldResetPassword = "true"
}

Invoke-RestMethod -Uri http://localhost:9090/rest/ping -Method Post -Body "{ ""shouldResetPassword"": $shouldResetPassword }" -ContentType 'application/json'