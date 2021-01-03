$installDir = "C:\Program Files\SkyWall"
cd .\ui
cp .\setup\winsw.exe $installDir\skywall-ui.exe
cp -Recurse .\data $installDir
.\deploy.ps1

cd ..\filter
cp .\setup\winsw.exe $installDir\skywall-filter.exe
cp -Recurse .\filter $installDir
.\deploy.ps1
Unblock-File -Path $installDir\launchProxy.ps1
cd ..