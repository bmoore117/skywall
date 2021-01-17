Write-Host "Clearing old package files"
rm -Recurse -Force .\package\VFS\ProgramFilesX64\SkyWall\*

Write-Host "Copying new package files"
cp .\filter\setup\winsw.exe .\package\VFS\ProgramFilesX64\SkyWall\skywall-filter.exe
cp .\filter\setup\winsw.xml .\package\VFS\ProgramFilesX64\SkyWall\skywall-filter.xml
cp .\filter\launchProxy.ps1 .\package\VFS\ProgramFilesX64\SkyWall
cp .\filter\mitmdump.py .\package\VFS\ProgramFilesX64\SkyWall
cp .\filter\skywall-filter.py .\package\VFS\ProgramFilesX64\SkyWall
cp -Recurse .\filter\filter .\package\VFS\ProgramFilesX64\SkyWall\

cp .\ui\dist\SkyWall.jar .\package\VFS\ProgramFilesX64\SkyWall
cp -Recurse .\ui\data .\package\VFS\ProgramFilesX64\SkyWall
cp .\ui\setup\winsw.exe .\package\VFS\ProgramFilesX64\SkyWall\skywall-ui.exe
cp .\ui\setup\winsw.xml .\package\VFS\ProgramFilesX64\SkyWall\skywall-ui.xml

cp .\launcher\dist\launcher.jar .\package\VFS\ProgramFilesX64\SkyWall

cp -Recurse .\dist .\package\VFS\ProgramFilesX64\SkyWall

rm .\SkyWall.msix

MakeAppx pack /v /h SHA256 /d .\package /p SkyWall.msix
signtool.exe sign /fd SHA256 /f C:\Users\User\Desktop\signing-cert.pfx /p asdf .\SkyWall.msix