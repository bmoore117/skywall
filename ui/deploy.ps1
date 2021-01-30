$installDir = 'C:\Program Files\SkyWall'
cp .\dist\SkyWall.jar $installDir
cp .\setup\winsw.xml $installDir\skywall-ui.xml
if (Get-Service "SkyWall UI" -ErrorAction SilentlyContinue) {
    Stop-Service -Name "SkyWall UI"
    Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\ui\*
    Start-Service -Name "SkyWall UI"
}