if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] “Administrator”)) {
    Write-Warning “Please re-run this script as an Administrator”
    exit 1
}

if ($PSScriptRoot -ne 'C:\Users\Public\skywall') {
    Write-Host "Copying files to Public"
    cp -Recurse . C:\Users\Public\skywall
}

Write-Host "Creating skywall user"
$password = ConvertTo-SecureString -AsPlainText "P@ssw0rd" -Force
New-LocalUser "skywall" -Password $password -FullName "SkyWall" -Description "Admin account managed by SkyWall"
Add-LocalGroupMember -Group "Administrators" -Member "skywall"

cd ~
$cred = New-Object System.Management.Automation.PSCredential ("skywall", $password)
Start-Process powershell.exe -Credential $cred -ArgumentList "Start-Process powershell.exe -ArgumentList '-File','C:\Users\Public\skywall\doInstall.ps1' -Verb RunAs"