Param(
    [Parameter(Mandatory=$false)]
    [String] $NodeFilePath
)

# Pull down and store current TOR node IP's

# Get new data for rules
$file = Get-Content $NodeFilePath
$lines = $file -split "`n"

# Quit the program if no data was pulled down
if($lines.Length -lt 5) {
	Write-Host Server limited to one request every 30 minutes
	exit
}

# Delete all created TorBlock rules
# Dumps all rules and parses for "TorBlock" and "TorBlocker
$total = Get-NetFireWallRule -All | Select-String TorBlock | Measure-Object -Line | Select-Object -expand Lines
for ($i = 0; $i -lt $total; $i++) {
	$name = "TorBlock$i"
	Remove-NetFireWallRule -DisplayName $name
}

$linesLength = $lines.Length
$iterations = [math]::Ceiling($lines.Length / 1000)

# Set New Firewall rules banning TOR IP's by the 1000's
# (Maximum number of IP's allowed per rule is 1000)
for($i = 0; $i -lt $iterations; $i++) {
    $ips = @()
    $endJ = [math]::Min(($i+1)*1000, $lines.Length)
    for ($j = $i*1000; $j -lt $endJ; $j++) {
        $ips += $lines[$j]
    }
	$name = "TorBlock$i"
	$null = New-NetFirewallRule -Direction Outbound -DisplayName $name -Name $name -RemoteAddress $ips -Action Block -ea "SilentlyContinue"
}
Write-Host "Finished updating tor firewall rules"