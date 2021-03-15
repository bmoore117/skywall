# methinks this would have worked except for the whole WinDivert stealth service thing not being listed in the registry,
# not sure how to add it
#$uiKey = "HKLM:\SYSTEM\CurrentControlSet\Control\SafeBoot\Network\SkyWall UI"
#if (-not (Test-Path -Path $uiKey)) {
#    Write-Host "UI SafeBoot key does not exist, creating"
#    New-Item -Path $uiKey -Value "Service"
#}

#$filterKey = "HKLM:\SYSTEM\CurrentControlSet\Control\SafeBoot\Network\SkyWall Filter"
#if (-not (Test-Path -Path $filterKey)) {
#    Write-Host "Filter SafeBoot key does not exist, creating"
#    New-Item -Path $filterKey -Value "Service"
#}
Write-Host "Setting bootloader advanced options off, to disable Safe Mode"
bcdedit /set "{current}" advancedoptions off