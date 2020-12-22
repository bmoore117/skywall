param (
    [Parameter(Mandatory=$true)]
    [string] $password
)

$secureString = ConvertTo-SecureString $password -AsPlainText -Force
$user = Get-LocalUser -Name "skywall"
Write-Host "Changing password for $user"
$user | Set-LocalUser -Password $secureString
if ($?) {
    exit 0
} else {
    exit 1
}