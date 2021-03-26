$orig = Get-Location
cd .\src\main\resources\default-config
cp C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall\data\config.json .
cp C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall\filter\hosts\hosts.json .
cp C:\Windows\System32\config\systemprofile\AppData\Local\SkyWall\filter\processes\processes.json .
cd $orig