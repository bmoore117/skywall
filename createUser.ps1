$password = ConvertTo-SecureString -AsPlainText "P@ssw0rd" -Force
New-LocalUser "skywall" -Password $password -FullName "SkyWall" -Description "Admin account managed by SkyWall"
Add-LocalGroupMember -Group "Administrators" -Member "skywall"