powershell "Set-ExecutionPolicy Unrestricted -Force"
powershell "Get-ChildItem . -Recurse -Filter *.ps1 | Unblock-File"