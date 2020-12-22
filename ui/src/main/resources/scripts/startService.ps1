param(
    [Parameter(Mandatory=$true)]
    [string]$serviceName
)

Write-Host "Got serviceName" $serviceName

Start-Service -Name $serviceName