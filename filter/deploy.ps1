$installDir = "C:\Program Files\SkyWall"
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    Stop-Service -Name "SkyWall Filter"
}
cp .\skywall-filter.py $installDir
cp .\launchProxy.ps1 $installDir
cp .\mitmdump.py $installDir
cp .\setup\winsw.xml $installDir\skywall-filter.xml
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\filter\*
    Start-Service -Name "SkyWall Filter"
}