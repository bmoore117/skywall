$adapters = Get-NetAdapter | Where Name -NotMatch "Bluetooth"
foreach ($adapter in $adapters) {
    if ($adapter.Status -eq "Disabled") {
        Enable-NetAdapter $adapter.Name -Confirm:$false
    }
}
Write-Host "Enabled network adapters"