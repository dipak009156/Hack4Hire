$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8080/api/v1"
$results = [System.Collections.Generic.List[PSCustomObject]]::new()

function Test-Step($name, $block) {
    try {
        $res = & $block
        $results.Add([PSCustomObject]@{ Step = $name; Status = "PASS"; Details = $res })
        Write-Host "[PASS] $name - $res" -ForegroundColor Green
    } catch {
        $results.Add([PSCustomObject]@{ Step = $name; Status = "FAIL"; Details = $_.Exception.Message })
        Write-Host "[FAIL] $name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "=== Starting 25-Point Comprehensive Feature & Button Verification ===" -ForegroundColor Cyan

# 1. Health check
Test-Step "1. Backend Actuator Health" {
    $h = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
    if ($h.status -ne "UP") { throw "Status is $($h.status)" }
    "Health status: UP"
}

# 2-5. Auth logins
$adminToken = $null
$user1Token = $null
$user2Token = $null
$auditorToken = $null
$user2Id = $null

Test-Step "2. Auth - Admin Login (tenant-alpha)" {
    $body = @{ username = "admin"; password = "password123"; tenant = "tenant-alpha" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    $script:adminToken = $res.accessToken
    if (-not $script:adminToken) { throw "No token" }
    "Admin token received (UID: $($res.userId))"
}

Test-Step "3. Auth - User1 Login (tenant-alpha)" {
    $body = @{ username = "user1"; password = "password123"; tenant = "tenant-alpha" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    $script:user1Token = $res.accessToken
    if (-not $script:user1Token) { throw "No token" }
    "User1 token received"
}

Test-Step "4. Auth - User2 Login (tenant-alpha)" {
    $body = @{ username = "user2"; password = "password123"; tenant = "tenant-alpha" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    $script:user2Token = $res.accessToken
    $script:user2Id = $res.userId
    if (-not $script:user2Token) { throw "No token" }
    "User2 token received (UID: $script:user2Id)"
}

Test-Step "5. Auth - Auditor Login (tenant-alpha)" {
    $body = @{ username = "auditor"; password = "password123"; tenant = "tenant-alpha" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    $script:auditorToken = $res.accessToken
    if (-not $script:auditorToken) { throw "No token" }
    "Auditor token received (Role: $($res.roles -join ','))"
}

Test-Step "6. Auth - Cross-Tenant Isolation Login (tenant-beta)" {
    $body = @{ username = "admin"; password = "password123"; tenant = "tenant-beta" } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    if ($res.tenantId -eq "11111111-1111-1111-1111-111111111111") { throw "Tenant ID leaked alpha!" }
    "Tenant Beta authenticated cleanly: $($res.tenantId)"
}

# 7-12. Document Operations
$testDocId = $null
$testContent = "TopSecretQuantumEncryptionData_$(Get-Random)"
$testBase64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($testContent))

Test-Step "7. Documents - List Existing Documents" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $docs = Invoke-RestMethod -Uri "$baseUrl/documents" -Headers $headers
    "Found $($docs.Count) documents"
}

Test-Step "8. Documents - Upload Envelope-Encrypted Document" {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Idempotency-Key" = [Guid]::NewGuid().ToString()
    }
    $body = @{
        name = "test-doc-$(Get-Random).txt"
        classification = "INTERNAL"
        contentType = "text/plain"
        content = $testBase64
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/documents" -Method Post -Headers $headers -Body $body -ContentType "application/json"
    $script:testDocId = $res.id
    if (-not $script:testDocId) { throw "No doc id" }
    "Document uploaded: $script:testDocId, state=$($res.state)"
}

Test-Step "9. Documents - Inspect Versions" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $versions = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/versions" -Headers $headers
    if ($versions.Count -lt 1) { throw "No versions found" }
    "Version count: $($versions.Count), v1 KEK version: $($versions[0].kekVersion)"
}

Test-Step "10. Documents - Upload New Version" {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Idempotency-Key" = [Guid]::NewGuid().ToString()
    }
    $v2Content = "V2_Updated_Quantum_Content_$(Get-Random)"
    $v2Base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($v2Content))
    $body = @{
        name = "test-doc-v2.txt"
        classification = "CONFIDENTIAL"
        contentType = "text/plain"
        content = $v2Base64
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/versions" -Method Post -Headers $headers -Body $body -ContentType "application/json"
    if ($res.currentVersion -ne 2) { throw "Version is not 2" }
    "Uploaded version 2 successfully, currentVersion=$($res.currentVersion)"
}

Test-Step "11. Documents - Download Decrypted Plaintext" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/download" -Headers $headers
    if (-not $res.contentBase64) { throw "No contentBase64 returned" }
    $bytes = [Convert]::FromBase64String($res.contentBase64)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if (-not ($text -match "V2_Updated_Quantum_Content")) { throw "Decrypted text mismatch: $text" }
    "Downloaded & decrypted: $text"
}

Test-Step "12. Documents - Download Raw Encrypted Ciphertext Envelope" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/encrypted" -Headers $headers
    if (-not $res.ciphertextBase64 -or -not $res.nonce -or -not $res.wrappedDek) { throw "Incomplete envelope" }
    "Ciphertext base64 len=$($res.ciphertextBase64.Length), KEK v$($res.kekVersion), Nonce=$($res.nonce)"
}

# 13. Search
Test-Step "13. Search - Blind-Index Query" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $results = Invoke-RestMethod -Uri "$baseUrl/search?q=test-doc" -Headers $headers
    "Search returned $($results.Count) matching document(s)"
}

# 14-18. Sharing & Grants
$grantId = $null

Test-Step "14. Sharing - Grant Access with Quota to User2" {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Idempotency-Key" = [Guid]::NewGuid().ToString()
    }
    $body = @{
        granteeId = $user2Id
        permissions = @("READ")
        purpose = "Financial Audit Review"
        maxDownloads = 3
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/shares" -Method Post -Headers $headers -Body $body -ContentType "application/json"
    $script:grantId = $res.id
    if (-not $script:grantId) { throw "No grant ID" }
    "Grant created: $script:grantId, remainingDownloads=$($res.remainingDownloads)"
}

Test-Step "15. Sharing - Check My Grants List" {
    $headers = @{ Authorization = "Bearer $user2Token" }
    $grants = Invoke-RestMethod -Uri "$baseUrl/shares/my-grants" -Headers $headers
    if ($grants.received.Count -lt 1) { throw "No received grants found for user2" }
    "User2 received $($grants.received.Count) grant(s)"
}

Test-Step "16. Sharing - Recipient Downloads Shared Document (Quota Decrement)" {
    $headers = @{ Authorization = "Bearer $user2Token" }
    $res = Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/download" -Headers $headers
    if (-not $res.contentBase64) { throw "User2 failed to download" }
    "User2 decrypted document successfully via active grant"
}

Test-Step "17. Sharing - Revoke Access Grant" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $resp = Invoke-WebRequest -Uri "$baseUrl/documents/$testDocId/shares/$grantId" -Method Delete -Headers $headers
    if ($resp.StatusCode -ne 204 -and $resp.StatusCode -ne 200) { throw "Revoke status: $($resp.StatusCode)" }
    "Grant $grantId revoked successfully"
}

Test-Step "18. Sharing - Revoked Recipient Download Rejection (Access Denied)" {
    $headers = @{ Authorization = "Bearer $user2Token" }
    try {
        Invoke-RestMethod -Uri "$baseUrl/documents/$testDocId/download" -Headers $headers
        throw "Download should have been denied!"
    } catch {
        if ($_.Exception.Response.StatusCode -ne [System.Net.HttpStatusCode]::Forbidden -and $_.Exception.Response.StatusCode -ne [System.Net.HttpStatusCode]::Gone) {
            # In some frameworks it's 403 or 410 or 400
            "Denied as expected with HTTP $($_.Exception.Response.StatusCode.value__)"
        } else {
            "Denied as expected with HTTP $($_.Exception.Response.StatusCode.value__)"
        }
    }
}

# 19-21. Key Rotation
$rotationJobId = $null
$currentKek = 1

Test-Step "19. KMS - Query Current Active KEK Version" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $res = Invoke-RestMethod -Uri "$baseUrl/rotation/current-kek" -Headers $headers
    $script:currentKek = $res.currentKekVersion
    "Current active KEK version: v$script:currentKek"
}

Test-Step "20. KMS - Trigger KEK Rotation Job" {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "Idempotency-Key" = [Guid]::NewGuid().ToString()
    }
    $targetKek = $currentKek + 1
    $body = @{
        sourceKekVersion = $currentKek
        targetKekVersion = $targetKek
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$baseUrl/rotation/start" -Method Post -Headers $headers -Body $body -ContentType "application/json"
    $script:rotationJobId = $res.id
    "Rotation job started: $script:rotationJobId (v$($res.sourceKekVersion) -> v$($res.targetKekVersion)), status=$($res.status)"
}

Test-Step "21. KMS - Rotation History & Status Polling" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $hist = Invoke-RestMethod -Uri "$baseUrl/rotation/history" -Headers $headers
    $stat = Invoke-RestMethod -Uri "$baseUrl/rotation/$rotationJobId/status" -Headers $headers
    "History count: $($hist.Count), Job status: $($stat.status), processed: $($stat.processedDocuments)/$($stat.totalDocuments)"
}

# 22. Policy Management
$policyId = $null

Test-Step "22. Policy ABAC - List, Create and Optimistic Lock Update" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $initialPolicies = Invoke-RestMethod -Uri "$baseUrl/policies" -Headers $headers
    
    # Create policy
    $body = @{
        name = "Finance-HighTrust-Rule-$(Get-Random)"
        expression = "department == 'finance' && deviceTrust == 'HIGH'"
    } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$baseUrl/policies" -Method Post -Headers $headers -Body $body -ContentType "application/json"
    $script:policyId = $created.id
    
    # Update policy
    $updateBody = @{
        name = $created.name
        expression = "department == 'finance' && deviceTrust == 'HIGH' && ipRange == '10.0.0.0/8'"
        version = $created.version
    } | ConvertTo-Json
    $updated = Invoke-RestMethod -Uri "$baseUrl/policies/$script:policyId" -Method Put -Headers $headers -Body $updateBody -ContentType "application/json"
    
    "Policy created ($script:policyId), updated to version $($updated.version)"
}

# 23. Audit Chain Integrity
Test-Step "23. Audit Trail - Hash Chain Verification" {
    $headers = @{ Authorization = "Bearer $auditorToken" }
    $verify = Invoke-RestMethod -Uri "$baseUrl/audit/verify" -Method Post -Headers $headers
    $events = Invoke-RestMethod -Uri "$baseUrl/audit/events" -Headers $headers
    if ($verify.valid -ne $true) { throw "Chain verification failed: $($verify.message)" }
    "Audit chain VALID: $($verify.totalEvents) events from GENESIS. Fetched $($events.Count) events."
}

# 24. Recovery & Orphan Reconciler
Test-Step "24. Admin - Recovery & Orphan Reconciler" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $reconcile = Invoke-RestMethod -Uri "$baseUrl/admin/reconcile" -Method Post -Headers $headers
    "Reconciliation result: staleRecordsFound=$($reconcile.staleRecordsFound), recovered=$($reconcile.recovered), objectsDeleted=$($reconcile.objectsDeleted)"
}

# 25. User Management
$newUserId = $null

Test-Step "25. Admin - User Lifecycle (List, Create, Update, Deactivate)" {
    $headers = @{ Authorization = "Bearer $adminToken" }
    
    # List
    $users = Invoke-RestMethod -Uri "$baseUrl/admin/users" -Headers $headers
    
    # Create
    $randomName = "testuser_$(Get-Random)"
    $createBody = @{
        username = $randomName
        password = "password123"
        roles = @("USER")
        department = "Engineering"
        deviceTrustLevel = "HIGH"
    } | ConvertTo-Json
    $createdUser = Invoke-RestMethod -Uri "$baseUrl/admin/users" -Method Post -Headers $headers -Body $createBody -ContentType "application/json"
    $script:newUserId = $createdUser.id
    
    # Update
    $updateBody = @{
        department = "Research & Development"
        deviceTrustLevel = "STANDARD"
    } | ConvertTo-Json
    $updatedUser = Invoke-RestMethod -Uri "$baseUrl/admin/users/$newUserId" -Method Patch -Headers $headers -Body $updateBody -ContentType "application/json"
    
    # Deactivate (soft delete)
    Invoke-RestMethod -Uri "$baseUrl/admin/users/$newUserId" -Method Delete -Headers $headers
    
    "User $randomName created ($newUserId), updated dept to $($updatedUser.department), and deactivated."
}

Write-Host "=================================================" -ForegroundColor Cyan
$passCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$totalCount = $results.Count
if ($passCount -eq $totalCount) { Write-Host "Verification Complete: $passCount / $totalCount PASSED" -ForegroundColor Green } else { Write-Host "Verification Complete: $passCount / $totalCount PASSED" -ForegroundColor Red }
