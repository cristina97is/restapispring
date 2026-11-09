$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
Write-Host 'Step 1: logging in as demo...'
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method POST -ContentType 'application/json' -Body '{"username":"demo","password":"Demo12345!"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Write-Host 'Step 2: getting hotels...'
$hotelResponse = Invoke-WebRequest -Uri "$base/api/hotels" -Headers $headers -UseBasicParsing
$hotelJson = [System.Text.Encoding]::UTF8.GetString($hotelResponse.RawContentStream.ToArray())
$hotels = $hotelJson | ConvertFrom-Json
$hotels | Format-Table
Write-Host 'Step 3: finding recommended rooms...'
$start = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
$end = (Get-Date).AddDays(33).ToString('yyyy-MM-dd')
$rooms = Invoke-RestMethod -Uri "$base/api/rooms/recommend?startDate=$start&endDate=$end" -Headers $headers
if ($rooms.Count -eq 0) { throw 'No available rooms' }
$roomId = $rooms[0].id
Write-Host "Step 4: booking room $roomId ($start .. $end)..."
$key = [guid]::NewGuid().ToString()
$h = @{ Authorization = $headers.Authorization; 'Idempotency-Key' = $key }
$body = @{ startDate = $start; endDate = $end; roomId = $roomId; autoSelect = $false } | ConvertTo-Json
$first = Invoke-RestMethod -Uri "$base/api/bookings" -Method POST -Headers $h -ContentType 'application/json' -Body $body
$second = Invoke-RestMethod -Uri "$base/api/bookings" -Method POST -Headers $h -ContentType 'application/json' -Body $body
if ($first.id -ne $second.id -or $first.status -ne 'CONFIRMED') { throw 'Idempotency/confirmation test failed' }
Write-Host "Step 5: repeat key preserved bookingId=$($first.id)"
Write-Host 'Step 6: cancelling booking...'
$cancelled = Invoke-RestMethod -Uri "$base/api/bookings/$($first.id)" -Method DELETE -Headers $headers
if ($cancelled.status -ne 'CANCELLED') { throw 'Cancellation did not succeed' }
Write-Host 'SUCCESS. Booking confirmed, duplicate prevented, booking cancelled.' -ForegroundColor Green
