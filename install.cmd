@echo off
powershell "if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] 'Administrator')) { exit 1 } else { exit 0 }"
if %ErrorLevel% equ 1 (
echo Please run this script as an administrator
) else (
powershell "Set-ExecutionPolicy Unrestricted -Force"
powershell "Get-ChildItem . -Recurse -Filter *.ps1 | Unblock-File"
powershell -File createUser.ps1
)