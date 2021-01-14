if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] “Administrator”)) {
    Write-Warning “Please re-run this script as an Administrator”
    exit 1
}

$origLocation = Get-Location

$installDir = "C:\Program Files\SkyWall"
$null = New-Item -Type Directory -Force -Path $installDir

Write-Host "Copying dist files"
cp -Recurse .\dist $installDir

New-NetFirewallRule -Name "SkyWall - Allow Filter Inbound" -DisplayName "SkyWall - Allow Filter Inbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Inbound
New-NetFirewallRule -Name "SkyWall - Allow Filter Outbound" -DisplayName "SkyWall - Allow Filter Outbound" -Program "$installDir\dist\Python39\python.exe" -Action Allow -Profile Any -Direction Outbound

.\deploy.ps1

cd $installDir
.\skywall-ui.exe install
.\skywall-filter.exe install

[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine) + ";$installDir\dist\Python39\Scripts", [EnvironmentVariableTarget]::Machine)

Start-Service "SkyWall UI"
Start-Service "SkyWall Filter"

cd $origLocation
Write-Host "Install completed, exiting in 5 seconds"
Start-Sleep -Seconds 5