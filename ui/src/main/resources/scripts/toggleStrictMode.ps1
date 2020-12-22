param (
    [Parameter(Mandatory=$true)]
    [ValidateSet("on", "off")]
    [string]$mode,
    [Parameter(Mandatory=$false)]
    [string]$usersToRestore
)

if ($mode -eq "on") {
    $returnUsers = [System.Collections.ArrayList]@()
    Disable-LocalUser -Name "Administrator"
    $users = Get-LocalUser | where {$_.Name -ne "skywall"}
    foreach ($user in $users) {
        if ($user.Enabled -eq $true) {
            Remove-LocalGroupMember -Group "Administrators" -Member $user.Name
            $returnUsers.Add($user.Name)
        }
    }
    Write-Host $($returnUsers -join "~,~")
} else {
    if ($usersToRestore) {
        $users = $usersToRestore -split "~,~"
        foreach ($user in $users) {
            Add-LocalGroupMember -Group "Administrators" -Member $user
        }
    }
}