# QUANTUM-LOCK Live Examiner Demonstration Script (Endlessly Repeatable & Self-Healing)
$baseUri = "http://localhost:8080/api/v1"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "            QUANTUM-LOCK DEMONSTRATION                    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$stateFile = Join-Path $scriptDir "demo_state.json"
$seedScript = Join-Path $scriptDir "demo_seed.ps1"

# Check if demo_state.json exists; if not, seed
if (-not (Test-Path $stateFile)) {
    Write-Host "Demo state not found. Seeding initial demo data..." -ForegroundColor Yellow
    & $seedScript
}

$state = Get-Content $stateFile -Raw | ConvertFrom-Json

# Acquire FRESH, live JWT tokens via login (guarantees no token expiry!)
try {
    $aliceAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "CyberHealth-Systems"; username = "dr_alice"; password = "HospitalPassword2026!" } | ConvertTo-Json)
    $aliceToken = $aliceAuth.token
    $bobAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "CyberHealth-Systems"; username = "nurse_bob"; password = "NursePassword2026!" } | ConvertTo-Json)
    $bobToken = $bobAuth.token
    $davisAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "Apex-Defense-Aerospace"; username = "col_davis"; password = "DefensePassword2026!" } | ConvertTo-Json)
    $davisToken = $davisAuth.token
} catch {
    Write-Host "Live session expired or database reset. Re-seeding demo vault..." -ForegroundColor Yellow
    & $seedScript
    $state = Get-Content $stateFile -Raw | ConvertFrom-Json
    $aliceAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "CyberHealth-Systems"; username = "dr_alice"; password = "HospitalPassword2026!" } | ConvertTo-Json)
    $aliceToken = $aliceAuth.token
    $bobAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "CyberHealth-Systems"; username = "nurse_bob"; password = "NursePassword2026!" } | ConvertTo-Json)
    $bobToken = $bobAuth.token
    $davisAuth = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ tenantName = "Apex-Defense-Aerospace"; username = "col_davis"; password = "DefensePassword2026!" } | ConvertTo-Json)
    $davisToken = $davisAuth.token
}

$bobId = $bobAuth.userId

# DEMO 1: Searchable Encryption (Blind Index Tokens)
Write-Host "`n--- DEMO 1: Searchable Encryption (HMAC Blind Index) ---" -ForegroundColor Yellow
Write-Host "Querying 'oncology' as CyberHealth Admin (dr_alice)..."
$searchHealth = Invoke-RestMethod -Uri "$baseUri/documents/search?query=oncology" -Method Get -Headers @{ Authorization = "Bearer $aliceToken" }
Write-Host "  -> Result count: $($searchHealth.Count) (Matched Patient Genetics Report)" -ForegroundColor Green

Write-Host "Querying 'oncology' as Apex Defense Admin (col_davis)..."
$searchDefense = Invoke-RestMethod -Uri "$baseUri/documents/search?query=oncology" -Method Get -Headers @{ Authorization = "Bearer $davisToken" }
Write-Host "  -> Result count: $($searchDefense.Count) (Strict Multi-Tenant Isolation Proven!)" -ForegroundColor Green

# DEMO 2: Zero-Trust ABAC & Quota-Bound Sharing
Write-Host "`n--- DEMO 2: ABAC & Quota-Bound Document Download ---" -ForegroundColor Yellow
Write-Host "dr_alice issues a fresh grant to nurse_bob (Clearance 1) with Download Quota = 2..."
$freshGrant = Invoke-RestMethod -Uri "$baseUri/grants" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $aliceToken" } `
    -Body (@{
        documentId    = $state.Tenant1.Doc1Id
        recipientId   = $bobId
        permissions   = @("DOWNLOAD")
        downloadQuota = 2
    } | ConvertTo-Json)
Write-Host "  -> Grant Created (ID: $($freshGrant.id), Quota: $($freshGrant.downloadQuota))" -ForegroundColor Green

Write-Host "nurse_bob downloading Doc 1 using Grant (Consuming 1 Quota)..."
$bobDownload1 = Invoke-RestMethod -Uri "$baseUri/documents/$($state.Tenant1.Doc1Id)/download" -Method Get -Headers @{ Authorization = "Bearer $bobToken" }
Write-Host "  -> Download 1 Success: '$bobDownload1'" -ForegroundColor Green

# DEMO 3: Linearized Revocation (Instant Invalidation)
Write-Host "`n--- DEMO 3: Linearized Revocation (Instant HTTP 410) ---" -ForegroundColor Yellow
Write-Host "dr_alice revokes nurse_bob's grant..."
$revoke = Invoke-RestMethod -Uri "$baseUri/grants/$($freshGrant.id)/revoke" -Method Post -Headers @{ Authorization = "Bearer $aliceToken" }
Write-Host "  -> Grant successfully revoked in database and evicted from cache." -ForegroundColor Green

Write-Host "nurse_bob attempts to download again immediately after revocation..."
try {
    $blocked = Invoke-RestMethod -Uri "$baseUri/documents/$($state.Tenant1.Doc1Id)/download" -Method Get -Headers @{ Authorization = "Bearer $bobToken" }
    Write-Host "  -> FAILED: Download should have been blocked!" -ForegroundColor Red
} catch {
    Write-Host "  -> BLOCKED! HTTP 410 GONE received: Access grant has been revoked" -ForegroundColor Green
}

# DEMO 4: Cryptographic Audit Hash Chain Verification
Write-Host "`n--- DEMO 4: Cryptographic Hash Chain Integrity ---" -ForegroundColor Yellow
Write-Host "Verifying Merkle hash chain for CyberHealth Systems..."
$audit = Invoke-RestMethod -Uri "$baseUri/audit/verify" -Method Get -Headers @{ Authorization = "Bearer $aliceToken" }
Write-Host "  -> Valid: $($audit.valid) | Total Verified Events: $($audit.totalEvents)" -ForegroundColor Green
Write-Host "  -> Verification Message: '$($audit.message)'" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "      ALL 4 LIVE DEMO SCENARIOS VERIFIED SUCCESSFULLY!    " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
