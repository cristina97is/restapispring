# Test of simultaneous bookings by two separate users.
# Compatible with Windows PowerShell 5.1; no additional modules required.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'

Write-Host 'Checking Hotel Service and preparing two test users...'
$tag = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$tokens = @()
foreach ($username in @("parallel_a_$tag", "parallel_b_$tag")) {
    $data = @{ username = $username; password = 'Parallel123!' } | ConvertTo-Json -Compress
    $user = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post `
        -ContentType 'application/json; charset=utf-8' -Body $data
    $tokens += $user.accessToken
    Write-Host "Registered $username"
}

# Choose a fresh future date range and a currently free room.
$startDay = (Get-Date).Date.AddDays((Get-Random -Minimum 130 -Maximum 300))
$endDay = $startDay.AddDays(3)
$start = $startDay.ToString('yyyy-MM-dd')
$end = $endDay.ToString('yyyy-MM-dd')
$headers = @{ Authorization = "Bearer $($tokens[0])" }
$rooms = Invoke-RestMethod -Uri "$base/api/rooms?startDate=$start&endDate=$end&hotelId=1" -Headers $headers
$room = $rooms | Select-Object -First 1
if ($null -eq $room) { throw "No free rooms in hotel 1 for $start .. $end" }
$roomId = [int]$room.id
Write-Host "Both users will try room $roomId, dates $start .. $end"

# Start both HTTP requests without waiting for the first one to finish.
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(45)
$body = @{ startDate = $start; endDate = $end; roomId = $roomId; hotelId = 1; autoSelect = $false } | ConvertTo-Json -Compress
$requests = @()
for ($i = 0; $i -lt 2; $i++) {
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$base/api/bookings")
    $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $tokens[$i])
    [void]$req.Headers.TryAddWithoutValidation('Idempotency-Key', [Guid]::NewGuid().ToString('N'))
    $req.Content = [System.Net.Http.StringContent]::new($body, [System.Text.Encoding]::UTF8, 'application/json')
    $requests += $req
}

Write-Host 'Sending requests simultaneously...'
$task1 = $client.SendAsync($requests[0])
$task2 = $client.SendAsync($requests[1])
$response1 = $task1.GetAwaiter().GetResult()
$response2 = $task2.GetAwaiter().GetResult()

$codes = @()
for ($i = 0; $i -lt 2; $i++) {
    if ($i -eq 0) { $response = $response1 } else { $response = $response2 }
    $code = [int]$response.StatusCode
    $codes += $code
    $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $data = $null
    try { $data = $responseText | ConvertFrom-Json } catch { }
    Write-Host "User $($i + 1): HTTP $code; bookingId=$($data.id); status=$($data.status); message=$($data.message)"
}

if (@($codes | Where-Object { $_ -eq 201 }).Count -eq 1 -and
    @($codes | Where-Object { $_ -eq 409 }).Count -eq 1) {
    Write-Host 'SUCCESS: one booking confirmed, the other rejected (409).' -ForegroundColor Green
} else {
    Write-Host 'CHECK NEEDED: expected exactly one HTTP 201 and one HTTP 409.' -ForegroundColor Yellow
    Write-Host 'Please share this output, and do not restart the services yet.'
}

foreach ($req in $requests) { $req.Dispose() }
$response1.Dispose()
$response2.Dispose()
$client.Dispose()
