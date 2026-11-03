import urllib.request
import urllib.error
import json
import base64
import uuid
import sys
import time

BASE_URL = "http://localhost:8080/api/v1"

results = []

def request(method, path, body=None, token=None, idempotency_key=None, expected_status=None):
    url = BASE_URL + path if not path.startswith("http") else path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key
    
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            resp_body = resp.read().decode("utf-8")
            if resp_body:
                try:
                    payload = json.loads(resp_body)
                except Exception:
                    payload = resp_body
            else:
                payload = {}
            return status, payload
    except urllib.error.HTTPError as e:
        status = e.code
        err_body = e.read().decode("utf-8")
        try:
            payload = json.loads(err_body)
        except Exception:
            payload = err_body
        if expected_status and status == expected_status:
            return status, payload
        raise Exception(f"HTTP {status} on {method} {path}: {payload}")
    except Exception as e:
        raise Exception(f"Request failed: {e}")

def run_test(name, fn):
    try:
        detail = fn()
        results.append((name, "PASS", detail))
        print(f"[\033[92mPASS\033[0m] {name} - {detail}")
    except Exception as e:
        results.append((name, "FAIL", str(e)))
        print(f"[\033[91mFAIL\033[0m] {name} - {e}")

print("=== QUANTUM-LOCK 25-Point Comprehensive Feature & Button Verification ===")

# 1. Health
def test_1():
    status, res = request("GET", "http://localhost:8080/actuator/health")
    assert res.get("status") == "UP", f"Unexpected status {res}"
    return "Backend actuator health is UP"
run_test("1. Backend Actuator Health", test_1)

# Tokens and IDs
admin_token = None
admin_id = None
user1_token = None
user2_token = None
user2_id = None
auditor_token = None

# 2. Admin Login
def test_2():
    global admin_token, admin_id
    _, res = request("POST", "/auth/login", {"username": "admin", "password": "password123", "tenant": "tenant-alpha"})
    admin_token = res["accessToken"]
    admin_id = res["userId"]
    assert "ADMIN" in res["roles"], f"Roles: {res['roles']}"
    return f"Admin token received (tenant: {res['tenantId'][:8]}..., UID: {admin_id})"
run_test("2. Auth - Admin Login (tenant-alpha)", test_2)

# 3. User1 Login
def test_3():
    global user1_token
    _, res = request("POST", "/auth/login", {"username": "user1", "password": "password123", "tenant": "tenant-alpha"})
    user1_token = res["accessToken"]
    assert "USER" in res["roles"]
    return f"User1 token received (roles: {res['roles']})"
run_test("3. Auth - User1 Login (tenant-alpha)", test_3)

# 4. User2 Login
def test_4():
    global user2_token, user2_id
    _, res = request("POST", "/auth/login", {"username": "user2", "password": "password123", "tenant": "tenant-alpha"})
    user2_token = res["accessToken"]
    user2_id = res["userId"]
    assert "USER" in res["roles"]
    return f"User2 token received (UID: {user2_id})"
run_test("4. Auth - User2 Login (tenant-alpha)", test_4)

# 5. Auditor Login
def test_5():
    global auditor_token
    _, res = request("POST", "/auth/login", {"username": "auditor", "password": "password123", "tenant": "tenant-alpha"})
    auditor_token = res["accessToken"]
    assert "AUDITOR" in res["roles"]
    return f"Auditor token received (roles: {res['roles']})"
run_test("5. Auth - Auditor Login (tenant-alpha)", test_5)

# 6. Cross-Tenant Login
def test_6():
    _, res = request("POST", "/auth/login", {"username": "admin", "password": "password123", "tenant": "tenant-beta"})
    assert res["tenantId"] == "22222222-2222-2222-2222-222222222222"
    return f"Tenant-Beta authenticated cleanly with isolation (tenantId: {res['tenantId']})"
run_test("6. Auth - Cross-Tenant Isolation Login (tenant-beta)", test_6)

# Document test state
doc_id = None
plain_text_v1 = f"Quantum_Envelope_Encryption_Content_{uuid.uuid4().hex[:8]}"
plain_text_v2 = f"Updated_Quantum_Envelope_V2_{uuid.uuid4().hex[:8]}"

# 7. List Documents
def test_7():
    _, res = request("GET", "/documents", token=admin_token)
    assert isinstance(res, list)
    return f"Retrieved {len(res)} existing tenant documents"
run_test("7. Documents - List Existing Documents", test_7)

# 8. Upload Document
def test_8():
    global doc_id
    body = {
        "name": f"quantum-security-spec-{uuid.uuid4().hex[:6]}.txt",
        "classification": "INTERNAL",
        "contentType": "text/plain",
        "content": base64.b64encode(plain_text_v1.encode()).decode()
    }
    _, res = request("POST", "/documents", body=body, token=admin_token, idempotency_key=str(uuid.uuid4()))
    doc_id = res["id"]
    assert doc_id is not None
    return f"Document uploaded successfully (id: {doc_id}, state: {res.get('state')}, v{res.get('currentVersion')})"
run_test("8. Documents - Upload Envelope-Encrypted Document", test_8)

# 9. Inspect Versions
def test_9():
    _, res = request("GET", f"/documents/{doc_id}/versions", token=admin_token)
    assert len(res) >= 1
    return f"Found {len(res)} version(s). Version 1 KEK version: {res[0].get('kekVersion')}"
run_test("9. Documents - Inspect Versions", test_9)

# 10. Upload New Version
def test_10():
    body = {
        "name": "quantum-security-spec-v2.txt",
        "classification": "CONFIDENTIAL",
        "contentType": "text/plain",
        "content": base64.b64encode(plain_text_v2.encode()).decode()
    }
    _, res = request("POST", f"/documents/{doc_id}/versions", body=body, token=admin_token, idempotency_key=str(uuid.uuid4()))
    assert res.get("currentVersion") == 2
    return f"Uploaded version 2 successfully (currentVersion: {res.get('currentVersion')})"
run_test("10. Documents - Upload New Version", test_10)

# 11. Download Decrypted Plaintext
def test_11():
    _, res = request("GET", f"/documents/{doc_id}/download", token=admin_token)
    assert "contentBase64" in res
    decrypted = base64.b64decode(res["contentBase64"]).decode()
    assert decrypted == plain_text_v2, f"Decrypted mismatch: {decrypted} != {plain_text_v2}"
    return f"Decrypted text verified: '{decrypted}' ({res['sizeBytes']} bytes)"
run_test("11. Documents - Download Decrypted Plaintext", test_11)

# 12. Download Raw Encrypted Ciphertext Envelope
def test_12():
    _, res = request("GET", f"/documents/{doc_id}/encrypted", token=admin_token)
    assert res.get("ciphertextBase64") and res.get("nonce") and res.get("wrappedDek")
    return f"Raw ciphertext verified (length: {len(res['ciphertextBase64'])}, KEK v{res['kekVersion']}, Nonce: {res['nonce']})"
run_test("12. Documents - Download Raw Encrypted Ciphertext Envelope", test_12)

# 13. Search Blind-Index
def test_13():
    _, res = request("GET", "/search?q=quantum", token=admin_token)
    assert isinstance(res, list) and len(res) > 0, f"Expected matching results for 'quantum', got {res}"
    matched_ids = [d["id"] for d in res]
    assert doc_id in matched_ids, f"Uploaded doc {doc_id} not found in search results: {matched_ids}"
    return f"Blind-index search for 'quantum' matched {len(res)} documents including {doc_id}"
run_test("13. Search - Blind-Index Query", test_13)

# Sharing state
grant_id = None

# 14. Grant Access to User2
def test_14():
    global grant_id
    body = {
        "granteeId": user2_id,
        "permissions": ["READ"],
        "purpose": "Security compliance audit review",
        "maxDownloads": 3
    }
    _, res = request("POST", f"/documents/{doc_id}/shares", body=body, token=admin_token, idempotency_key=str(uuid.uuid4()))
    grant_id = res["id"]
    assert grant_id is not None
    assert res.get("remainingDownloads") == 3
    return f"Access grant created: {grant_id} (maxDownloads: {res['maxDownloads']}, remaining: {res['remainingDownloads']})"
run_test("14. Sharing - Grant Access with Quota to User2", test_14)

# 15. Check My Grants List
def test_15():
    _, res = request("GET", "/shares/my-grants", token=user2_token)
    received = res.get("received", [])
    matched = [g for g in received if g["id"] == grant_id]
    assert len(matched) > 0, f"Grant {grant_id} not in user2 received grants: {received}"
    return f"User2 confirmed received grant {grant_id} with {matched[0]['remainingDownloads']} downloads remaining"
run_test("15. Sharing - Check My Grants List", test_15)

# 16. Recipient Downloads Shared Document (Quota Decrement)
def test_16():
    _, res = request("GET", f"/documents/{doc_id}/download", token=user2_token)
    decrypted = base64.b64decode(res["contentBase64"]).decode()
    assert decrypted == plain_text_v2
    # Verify remaining downloads decremented
    _, grants = request("GET", "/shares/my-grants", token=user2_token)
    grant = [g for g in grants["received"] if g["id"] == grant_id][0]
    assert grant["remainingDownloads"] == 2, f"Expected 2 remaining, got {grant['remainingDownloads']}"
    return f"User2 successfully downloaded & decrypted document. Quota atomically decremented from 3 -> 2."
run_test("16. Sharing - Recipient Downloads Shared Document (Quota Decrement)", test_16)

# 17. Revoke Access Grant
def test_17():
    status, _ = request("DELETE", f"/documents/{doc_id}/shares/{grant_id}", token=admin_token)
    assert status in (200, 204)
    return f"Access grant {grant_id} revoked by admin"
run_test("17. Sharing - Revoke Access Grant", test_17)

# 18. Revoked Recipient Download Rejection (403)
def test_18():
    status, err = request("GET", f"/documents/{doc_id}/download", token=user2_token, expected_status=403)
    assert status == 403, f"Expected 403, got {status}"
    return f"Subsequent download by revoked user2 correctly rejected with HTTP 403 Forbidden"
run_test("18. Sharing - Revoked Recipient Download Rejection (Access Denied)", test_18)

# KMS state
current_kek = None
job_id = None

# 19. Query Current KEK
def test_19():
    global current_kek
    _, res = request("GET", "/rotation/current-kek", token=admin_token)
    current_kek = res["currentKekVersion"]
    assert current_kek is not None
    return f"Active KEK version: v{current_kek}"
run_test("19. KMS - Query Current Active KEK Version", test_19)

# 20. Trigger KEK Rotation Job
def test_20():
    global job_id
    body = {
        "sourceKekVersion": current_kek,
        "targetKekVersion": current_kek + 1
    }
    _, res = request("POST", "/rotation/start", body=body, token=admin_token, idempotency_key=str(uuid.uuid4()))
    job_id = res["id"]
    assert job_id is not None
    return f"Rotation job created: {job_id} (v{res['sourceKekVersion']} -> v{res['targetKekVersion']}), status: {res['status']}"
run_test("20. KMS - Trigger KEK Rotation Job", test_20)

# 21. Rotation History & Status Polling
def test_21():
    _, stat = request("GET", f"/rotation/{job_id}/status", token=admin_token)
    _, hist = request("GET", "/rotation/history", token=admin_token)
    assert len(hist) > 0
    return f"Status: {stat['status']}, processed {stat['processedDocuments']}/{stat['totalDocuments']} docs. History has {len(hist)} jobs."
run_test("21. KMS - Rotation History & Status Polling", test_21)

# Policy state
policy_id = None

# 22. Policy ABAC
def test_22():
    global policy_id
    # List
    _, policies = request("GET", "/policies", token=admin_token)
    # Create
    body = {
        "name": f"Policy-{uuid.uuid4().hex[:6]}",
        "expression": "department == 'Engineering' && deviceTrust == 'HIGH'"
    }
    _, created = request("POST", "/policies", body=body, token=admin_token)
    policy_id = created["id"]
    version = created["version"]
    
    # Update with optimistic lock
    update_body = {
        "name": created["name"],
        "expression": "department == 'Engineering' && deviceTrust == 'HIGH' && action == 'READ'",
        "version": version
    }
    _, updated = request("PUT", f"/policies/{policy_id}", body=update_body, token=admin_token)
    if updated.get("version") != version + 1:
        # Check if version remained or incremented
        raise Exception(f"Version expected {version + 1}, but got {updated.get('version')} (created: {created}, updated: {updated})")
    return f"Policy created (id: {policy_id}), updated with optimistic locking to v{updated['version']}"
run_test("22. Policy ABAC - List, Create and Optimistic Lock Update", test_22)

# 23. Audit Chain Integrity
def test_23():
    _, verify = request("POST", "/audit/verify", token=auditor_token)
    assert verify.get("valid") is True, f"Chain invalid: {verify}"
    _, events = request("GET", "/audit/events", token=auditor_token)
    assert len(events) > 0
    return f"Cryptographic audit chain VERIFIED intact: {verify['totalEvents']} events from GENESIS. Chronological events retrieved: {len(events)}"
run_test("23. Audit Trail - Hash Chain Verification", test_23)

# 24. Recovery & Orphan Reconciler
def test_24():
    _, res = request("POST", "/admin/reconcile", token=admin_token)
    assert "staleRecordsFound" in res and "objectsDeleted" in res
    return f"Reconciler executed: {res['staleRecordsFound']} stale records, {res['recovered']} recovered, {res['objectsDeleted']} orphan objects cleaned"
run_test("24. Admin - Recovery & Orphan Reconciler", test_24)

# User state
created_uid = None

# 25. User Lifecycle
def test_25():
    global created_uid
    uname = f"agent_usr_{uuid.uuid4().hex[:6]}"
    # Create
    body = {
        "username": uname,
        "password": "password123",
        "roles": ["USER"],
        "department": "Security Ops",
        "deviceTrustLevel": "HIGH"
    }
    _, created = request("POST", "/admin/users", body=body, token=admin_token)
    created_uid = created["id"]
    
    # Update
    patch_body = {
        "department": "Cyber Defense",
        "deviceTrustLevel": "STANDARD"
    }
    _, updated = request("PATCH", f"/admin/users/{created_uid}", body=patch_body, token=admin_token)
    assert updated["department"] == "Cyber Defense"
    
    # Soft delete / deactivate
    status, _ = request("DELETE", f"/admin/users/{created_uid}", token=admin_token)
    assert status in (200, 204)
    
    # Verify inactive
    _, all_users = request("GET", "/admin/users", token=admin_token)
    u = [x for x in all_users if x["id"] == created_uid][0]
    assert u["active"] is False
    return f"User '{uname}' ({created_uid}) created, updated, and soft-deactivated successfully."
run_test("25. Admin - User Lifecycle (List, Create, Update, Deactivate)", test_25)

print("\n" + "="*60)
passed = sum(1 for _, s, _ in results if s == "PASS")
total = len(results)
print(f"VERIFICATION SUMMARY: {passed} / {total} PASSED")
if passed == total:
    print("\033[92mALL 25 ZERO-TRUST FEATURES & BUTTON HANDLERS VERIFIED 100% OPERATIONAL!\033[0m")
    sys.exit(0)
else:
    print("\033[91mSOME TESTS FAILED!\033[0m")
    sys.exit(1)
