# ~\.mitmproxy should be C:\WINDOWS\system32\config\systemprofile\.mitmproxy
if ((Test-Path -Path ~\.mitmproxy) -eq $false) {
    Write-Host "Adding certificate to store on first run"
    do {
        Start-Sleep -Seconds 3
    } while ((Test-Path -Path ~\.mitmproxy) -eq $false)
    Import-Certificate -FilePath ~\.mitmproxy\mitmproxy-ca-cert.cer -CertStoreLocation "Cert:\LocalMachine\Root"
}