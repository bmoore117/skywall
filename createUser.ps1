if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] “Administrator”)) {
    Write-Warning “Please re-run this script as an Administrator”
    exit 1
}

$password = ConvertTo-SecureString -AsPlainText "P@ssw0rd" -Force
New-LocalUser "skywall" -Password $password -FullName "SkyWall" -Description "Admin account managed by SkyWall"
Add-LocalGroupMember -Group "Administrators" -Member "skywall"

$cred = New-Credential "skywall" $password
$cwd = Get-Location
Start-Process powershell.exe -WorkingDirectory $cwd -ArgumentList "-File",".\doInstall.ps1"