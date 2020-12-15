Stop-Service -Name "Jarvis-Filter"
cp .\jarvis-filter.py C:\Users\ben-local\Code\jarvis-filter\jarvis-filter.py
cp .\setup\winsw.xml C:\Users\ben-local\Code\jarvis-filter\winsw.xml
cp .\launchProxy.ps1 C:\Users\ben-local\Code\jarvis-filter\launchProxy.ps1
cp C:\Users\ben-local\Code\jarvis-filter\filter\hosts.json .\filter\hosts.json
cp C:\Users\ben-local\Code\jarvis-filter\ignored-hosts.txt .\ignored-hosts.txt
Remove-Item -Recurse C:\Users\Public\Documents\jarvis-filter\*
Start-Service -Name "Jarvis-Filter"