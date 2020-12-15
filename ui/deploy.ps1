cp .\dist\SkyWall.jar C:\Users\skywall\skywall\
cp .\setup\winsw.xml C:\Users\skywall\skywall\skywall-ui.xml
cp .\setup\winsw.exe C:\Users\skywall\skywall\skywall-ui.exe
if (Get-Service "SkyWall UI" -ErrorAction SilentlyContinue) {
    cp C:\Users\skywall\skywall\data\config.json .\data\config.json
    Stop-Service -Name "SkyWall UI"
    Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\ui\*
    Start-Service -Name "SkyWall UI"
}