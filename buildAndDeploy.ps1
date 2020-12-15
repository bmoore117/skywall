cd .\ui
..\dist\apache-maven-3.6.3\bin\mvn.cmd clean install -Pproduction
cp .\target\skywall-1.0-SNAPSHOT.jar C:\Users\skywall\skywall\skywall-1.0-SNAPSHOT.jar
cp .\setup\winsw.xml C:\Users\skywall\skywall\skywall-ui.xml
cp .\setup\winsw.exe C:\Users\skywall\skywall\skywall-ui.exe
if (Get-Service "SkyWall UI" -ErrorAction SilentlyContinue) {
    cp C:\Users\skywall\skywall\data\config.json .\data\config.json
    Stop-Service -Name "SkyWall UI"
    Remove-Item -Recurse C:\Users\Public\Documents\skywall\ui\*
    Start-Service -Name "SkyWall UI"
}


cd ..\filter
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    Stop-Service -Name "SkyWall Filter"
}
cp .\skywall-filter.py C:\Users\skywall\skywall\skywall-filter.py
cp .\launchProxy.ps1 C:\Users\skywall\skywall\launchProxy.ps1
cp .\setup\winsw.xml C:\Users\skywall\skywall\skywall-filter.xml
cp .\setup\winsw.exe C:\Users\skywall\skywall\skywall-filter.exe
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    cp C:\Users\skywall\skywall\filter\hosts.json .\filter\hosts.json
    Remove-Item -Recurse C:\Users\Public\Documents\skywall\filter\*
    Start-Service -Name "SkyWall Filter"
}
cd ..