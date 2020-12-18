if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    Stop-Service -Name "SkyWall Filter"
}
cp .\skywall-filter.py C:\Users\skywall\skywall\skywall-filter.py
cp .\launchProxy.ps1 C:\Users\skywall\skywall\launchProxy.ps1
cp .\setup\winsw.xml C:\Users\skywall\skywall\skywall-filter.xml
if (Get-Service "SkyWall Filter" -ErrorAction SilentlyContinue) {
    cp C:\Users\skywall\skywall\filter\hosts.json .\filter\hosts.json
    Remove-Item -Recurse C:\Users\Public\Documents\skywall-logs\filter\*
    Start-Service -Name "SkyWall Filter"
}