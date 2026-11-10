# QUANTUM-LOCK Examiner Demo Data Seeder
$baseUri = "http://localhost:8080/api/v1"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "            QUANTUM-LOCK DEMONSTRATION                    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Onboard Tenant 1: CyberHealth Systems (Hospital Vault)
Write-Host "`n[1/5] Onboarding Tenant 1: CyberHealth Systems..." -ForegroundColor Yellow
$tenant1Admin = Invoke-RestMethod -Uri "$baseUri/auth/register-tenant" -Method Post -ContentType "application/json" -Body (@{
    tenantName    = "CyberHealth-Systems"
    adminUsername = "dr_alice"
    adminPassword = "HospitalPassword2026!"
} | ConvertTo-Json)
$t1Token = $tenant1Admin.token
$t1Id = $tenant1Admin.tenantId
$aliceId = $tenant1Admin.userId

# Create Member User in Tenant 1 (Nurse Bob, Clearance 1)
$nurseBob = Invoke-RestMethod -Uri "$baseUri/auth/users" -Method Post -ContentType "application/json" -Headers @{ Authorization = "Bearer $t1Token" } -Body (@{
    username       = "nurse_bob"
    password       = "NursePassword2026!"
    roles          = @("MEMBER")
    clearanceLevel = 1
} | ConvertTo-Json)
$bobToken = $nurseBob.token
$bobId = $nurseBob.userId
Write-Host "  -> Tenant: CyberHealth Systems ($t1Id)" -ForegroundColor Green
Write-Host "  -> Admin: dr_alice (Clearance 3) | Member: nurse_bob (Clearance 1)" -ForegroundColor Green

# 2. Onboard Tenant 2: Apex Defense Aerospace (Defense Vault)
Write-Host "`n[2/5] Onboarding Tenant 2: Apex Defense Aerospace..." -ForegroundColor Yellow
$tenant2Admin = Invoke-RestMethod -Uri "$baseUri/auth/register-tenant" -Method Post -ContentType "application/json" -Body (@{
    tenantName    = "Apex-Defense-Aerospace"
    adminUsername = "col_davis"
    adminPassword = "DefensePassword2026!"
} | ConvertTo-Json)
$t2Token = $tenant2Admin.token
$t2Id = $tenant2Admin.tenantId
Write-Host "  -> Tenant: Apex Defense ($t2Id) | Admin: col_davis" -ForegroundColor Green

# 3. Upload Sample Encrypted Documents
Write-Host "`n[3/5] Uploading Envelope-Encrypted Documents..." -ForegroundColor Yellow

# Doc 1: Genetic Sequencing (Confidential, Patient Data)
$doc1Content = "CYBERHEALTH CONFIDENTIAL: Patient #8841-A Whole Genome Sequencing shows BRCA1 exon 11 variant. Strict medical privacy enforced."
$b64Doc1 = [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($doc1Content))
$doc1 = Invoke-RestMethod -Uri "$baseUri/documents" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $t1Token"; "Idempotency-Key" = "tx-health-doc1" } `
    -Body (@{
        base64Content  = $b64Doc1
        classification = "CONFIDENTIAL"
        keywords       = @("patient", "genetics", "oncology", "dna")
    } | ConvertTo-Json)
Write-Host "  -> Doc 1 (CyberHealth): Patient Genetics Report [CONFIDENTIAL] - ID: $($doc1.documentId)" -ForegroundColor Green

# Doc 2: Visitor Policy (Internal, CyberHealth)
$doc2Content = "CyberHealth Visitor Policy: Visiting hours are 10:00 AM to 8:00 PM daily. ID badges must be visible at all times."
$b64Doc2 = [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($doc2Content))
$doc2 = Invoke-RestMethod -Uri "$baseUri/documents" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $t1Token"; "Idempotency-Key" = "tx-health-doc2" } `
    -Body (@{
        base64Content  = $b64Doc2
        classification = "INTERNAL"
        keywords       = @("hospital", "visitor", "policy")
    } | ConvertTo-Json)
Write-Host "  -> Doc 2 (CyberHealth): Visitor Policy [INTERNAL] - ID: $($doc2.documentId)" -ForegroundColor Green

# Doc 3: Defense Telemetry (Secret, Apex Defense)
$doc3Content = "APEX DEFENSE TOP SECRET: Quantum Propulsion Telemetry Vector at Mach 12. Angular velocity stable, antimatter containment 99.98%."
$b64Doc3 = [System.Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($doc3Content))
$doc3 = Invoke-RestMethod -Uri "$baseUri/documents" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $t2Token"; "Idempotency-Key" = "tx-defense-doc3" } `
    -Body (@{
        base64Content  = $b64Doc3
        classification = "SECRET"
        keywords       = @("quantum", "propulsion", "telemetry", "aerospace")
    } | ConvertTo-Json)
Write-Host "  -> Doc 3 (Apex Defense): Quantum Propulsion Telemetry [SECRET] - ID: $($doc3.documentId)" -ForegroundColor Green

# 4. Create Access Grant: dr_alice grants nurse_bob access to Doc 1 with quota of 2
Write-Host "`n[4/5] Creating ABAC Access Grant with Download Quota..." -ForegroundColor Yellow
$grant = Invoke-RestMethod -Uri "$baseUri/grants" -Method Post -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $t1Token" } `
    -Body (@{
        documentId    = $doc1.documentId
        recipientId   = $bobId
        permissions   = @("DOWNLOAD")
        downloadQuota = 2
    } | ConvertTo-Json)
Write-Host "  -> Grant created for nurse_bob (Quota: 2 downloads) - Grant ID: $($grant.id)" -ForegroundColor Green

# 5. Rotate KMS Key for CyberHealth (v1 -> v2)
Write-Host "`n[5/5] Performing Zero-Downtime KMS Key Rotation (v1 -> v2)..." -ForegroundColor Yellow
$rot = Invoke-RestMethod -Uri "$baseUri/keys/rotate" -Method Post -Headers @{ Authorization = "Bearer $t1Token" }
Write-Host "  -> KEK rotated successfully: $($rot.sourceKek) -> $($rot.targetKek) (Status: $($rot.status))" -ForegroundColor Green

# Save Demo State to JSON for easy reference
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$statePath = Join-Path $scriptDir "demo_state.json"
$demoState = @{
    Tenant1 = @{
        Name = "CyberHealth Systems"
        Id = $t1Id
        AdminToken = $t1Token
        MemberToken = $bobToken
        Doc1Id = $doc1.documentId
        Doc2Id = $doc2.documentId
        GrantId = $grant.id
    }
    Tenant2 = @{
        Name = "Apex Defense Aerospace"
        Id = $t2Id
        AdminToken = $t2Token
        Doc3Id = $doc3.documentId
    }
}
$demoState | ConvertTo-Json -Depth 5 | Set-Content $statePath -Encoding UTF8

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "   DEMO DATA SEEDED SUCCESSFULLY! (Saved to demo_state.json)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
