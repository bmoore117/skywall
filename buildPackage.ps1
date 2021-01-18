Write-Host "Clearing old package files"
rm -Recurse -Force .\package\VFS\ProgramFilesX64\SkyWall\*

Write-Host "Copying new package files"
cp .\filter\setup\winsw.exe .\package\VFS\ProgramFilesX64\SkyWall\skywall-filter.exe
cp .\filter\setup\winsw.xml .\package\VFS\ProgramFilesX64\SkyWall\skywall-filter.xml
cp .\filter\launchProxy.ps1 .\package\VFS\ProgramFilesX64\SkyWall
cp .\filter\mitmdump.py .\package\VFS\ProgramFilesX64\SkyWall
cp .\filter\skywall-filter.py .\package\VFS\ProgramFilesX64\SkyWall

cp .\ui\dist\SkyWall.jar .\package\VFS\ProgramFilesX64\SkyWall
cp .\ui\setup\winsw.exe .\package\VFS\ProgramFilesX64\SkyWall\skywall-ui.exe
cp .\ui\setup\winsw.xml .\package\VFS\ProgramFilesX64\SkyWall\skywall-ui.xml

cp .\launcher\dist\launcher.jar .\package\VFS\ProgramFilesX64\SkyWall

cp -Recurse .\dist .\package\VFS\ProgramFilesX64\SkyWall

rm .\SkyWall.msix

$certs = Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.FriendlyName -eq 'SkyWall Signing Cert' }
if ($certs -eq $null) {
    Write-Host "Generating new signing cert"
    $result = New-SelfSignedCertificate -Type Custom -Subject "CN=SkyWall, O=Me Inc, C=US" -KeyUsage DigitalSignature -FriendlyName "SkyWall Signing Cert" -CertStoreLocation "Cert:\CurrentUser\My"
    $thumbprint = $result.Thumbprint
    $password = ConvertTo-SecureString -String "asdf" -Force -AsPlainText
    Export-PfxCertificate -Cert "Cert:\CurrentUser\My\$thumbprint" -FilePath .\signing-cert.pfx -Password $password
    Import-PfxCertificate -CertStoreLocation 'Cert:\LocalMachine\Root' -FilePath .\signing-cert.pfx -Password $password
}

makeappx.exe pack /h SHA256 /d .\package /p SkyWall.msix
signtool.exe sign /fd SHA256 /f .\signing-cert.pfx /p asdf .\SkyWall.msix