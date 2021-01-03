$installDir = "C:\Program Files\SkyWall"
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    Stop-Service -Name "SkyWall Filter"
}
cp .\skywall-filter.py $installDir\skywall-filter.py
cp .\launchProxy.ps1 $installDir\launchProxy.ps1
cp .\setup\winsw.xml $installDir\skywall-filter.xml
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    cp $installDir\filter\hosts.json .\filter\hosts.json
    Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\filter\*
    Start-Service -Name "SkyWall Filter"
}