$BaseUrl = "http://localhost:8080"
$UserId = [guid]::NewGuid().ToString()
$IdempotencyUserId = [guid]::NewGuid().ToString()
$LowBalanceUserId = [guid]::NewGuid().ToString()
$FixedIdempotencyKey = "550e8400-e29b-41d4-a716-446655440000"

function Invoke-WalletRequest {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Body
    )

    Write-Host ""
    Write-Host "### $Name"

    try {
        $params = @{
            Method = $Method
            Uri = $Url
        }

        if ($Body) {
            $params.ContentType = "application/json"
            $params.Body = ($Body | ConvertTo-Json)
        }

        Invoke-RestMethod @params | ConvertTo-Json -Depth 10
    }
    catch {
        if ($_.Exception.Response) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $reader.ReadToEnd()
        }
        else {
            $_.Exception.Message
        }
    }
}

Invoke-WalletRequest `
    -Name "Health check" `
    -Method "GET" `
    -Url "$BaseUrl/actuator/health"

Invoke-WalletRequest `
    -Name "Get wallet - empty/new user" `
    -Method "GET" `
    -Url "$BaseUrl/wallets/$UserId"

Invoke-WalletRequest `
    -Name "Deposit 100" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$UserId/deposit" `
    -Body @{
        amount = 100.00
        idempotencyKey = [guid]::NewGuid().ToString()
    }

Invoke-WalletRequest `
    -Name "Trade 25" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$UserId/trade" `
    -Body @{
        amount = 25.00
        idempotencyKey = [guid]::NewGuid().ToString()
    }

Invoke-WalletRequest `
    -Name "Get wallet - after deposit/trade" `
    -Method "GET" `
    -Url "$BaseUrl/wallets/$UserId"

Invoke-WalletRequest `
    -Name "Deposit idempotency - first call" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$IdempotencyUserId/deposit" `
    -Body @{
        amount = 100.00
        idempotencyKey = $FixedIdempotencyKey
    }

Invoke-WalletRequest `
    -Name "Deposit idempotency - retry same key" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$IdempotencyUserId/deposit" `
    -Body @{
        amount = 100.00
        idempotencyKey = $FixedIdempotencyKey
    }

Invoke-WalletRequest `
    -Name "Trade - insufficient balance" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$LowBalanceUserId/trade" `
    -Body @{
        amount = 9999.00
        idempotencyKey = [guid]::NewGuid().ToString()
    }

Invoke-WalletRequest `
    -Name "Deposit - invalid negative amount" `
    -Method "POST" `
    -Url "$BaseUrl/wallets/$UserId/deposit" `
    -Body @{
        amount = -50.00
        idempotencyKey = [guid]::NewGuid().ToString()
    }
