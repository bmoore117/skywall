cp .\filter\setup\winsw.exe .\built-dist\skywall-filter.exe
cp .\filter\setup\winsw.xml .\built-dist\skywall-filter.xml
cp .\filter\launchProxy.ps1 .\built-dist
cp .\filter\skywall-filter.py .\built-dist
cp -Recurse .\filter\filter .\built-dist

cp .\ui\dist\SkyWall.jar .\built-dist
cp -Recurse .\ui\data .\built-dist
cp .\ui\setup\winsw.exe .\built-dist\skywall-ui.exe
cp .\ui\setup\winsw.xml .\built-dist\skywall-ui.xml

$folder = Get-Item .\built-dist\data
$folder.Attributes = $folder.Attributes -bor "Hidden"
$folder = Get-Item .\built-dist\filter
$folder.Attributes = $folder.Attributes -bor "Hidden"

cp -Recurse .\dist .\built-dist
