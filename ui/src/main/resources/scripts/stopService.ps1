param(
    [Parameter(Mandatory=$true)]
    [string]$serviceName
)

Write-Host "Got serviceName" $serviceName

Stop-Service -Name $serviceName