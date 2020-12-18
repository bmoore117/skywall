cd .\ui
cp .\setup\winsw.exe C:\Users\skywall\skywall\skywall-ui.exe
.\deploy.ps1

cd ..\filter
cp .\setup\winsw.exe C:\Users\skywall\skywall\skywall-filter.exe
.\deploy.ps1
Unblock-File -Path C:\Users\skywall\skywall\launchProxy.ps1
cd ..