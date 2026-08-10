# PowerShell script to probe Tomcat and run route baseline
$url = "http://localhost:8080/Website-ban-ve-xem-phim/home"
$success = $false

for ($i = 1; $i -le 20; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        $code = $response.StatusCode
        Write-Host "Probe $i : HTTP $code"
        if ($code -eq 200) {
            $success = $true
            break
        }
    } catch {
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
            Write-Host "Probe $i : HTTP $code"
            if ($code -eq 200) {
                $success = $true
                break
            }
        } else {
            Write-Host "Probe $i : Connection failed"
        }
    }
}

if ($success) {
    Write-Host "Tomcat is UP (HTTP 200)! Capturing route baseline..."
} else {
    Write-Host "Tomcat probe timed out or did not return 200."
}
