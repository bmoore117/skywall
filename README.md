# SkyWall Install Guide
To install, launch the command prompt (cmd.exe) as administrator, navigate to this folder, and run install.cmd

To uninstall, launch powershell as administrator, navigate to this folder, and run uninstall.ps1


### MSIX Creation Notes
Install the MSIX creation tool, then follow the steps here to generate a certificate: https://docs.microsoft.com/en-us/windows/msix/package/create-certificate-package-signing. The msix creation tool will not actually sign the package, so you will need to run a command of the form: .\signtool.exe sign /fd SHA256 /a /f C:\Users\User\cert.pfx /p asdf C:\Users\User\Desktop\skywall_1.0.0.0_x64__4fnvry8mcp9ym.msix. More details here: https://docs.microsoft.com/en-us/windows/win32/seccrypto/using-signtool-to-sign-a-file.

You may also need to add the certificate to trusted root, but I'm not sure on that.

The created msix should install, however it will not appear under the old control panel, instead go to the new Settings app and look under Apps, you'll have the option to uninstall there.