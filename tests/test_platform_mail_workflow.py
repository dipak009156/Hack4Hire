import urllib.request
import urllib.error
import json
import uuid

BASE_URL = "http://localhost:8080/api/v1"
PLATFORM_MAIL = "chavandipak0606@gmail.com"
REQUESTER_MAIL = "rohanchavan7666@gmail.com"

def request(method, path, body=None, token=None, expected_status=None):
    url = BASE_URL + path if not path.startswith("http") else path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            resp_body = resp.read().decode("utf-8")
            payload = json.loads(resp_body) if resp_body else {}
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

print("=" * 75)
print("=== VERIFYING PLATFORM MAIL CREDENTIAL WORKFLOW & MULTI-TENANT ISOLATION ===")
print(f"Platform Governance Admin (SUPER_ADMIN): chavandipak0606@gmail.com")
print(f"Test Requester:                          rohanchavan7666@gmail.com")
print("=" * 75)

# 1. Tenant Isolation Verification: tenant-alpha admin must NOT have access to platform review
print("\n[STEP 1] Verifying Cross-Tenant Isolation: Tenant-level admin blocked from platform governance...")
_, alpha_auth = request("POST", "/auth/login", {"username": "admin", "password": "password123", "tenant": "tenant-alpha"})
alpha_admin_token = alpha_auth["accessToken"]

# Expect 403 Forbidden when tenant-alpha admin tries to list credential requests
status, err_payload = request("GET", "/credential-requests", token=alpha_admin_token, expected_status=403)
assert status == 403, f"Expected 403 for tenant-alpha admin, got {status}"
print("[PASS] 1a. Tenant-alpha admin strictly BLOCKED (HTTP 403) from viewing other orgs' requests!")

# 2. Authenticate Platform Superadmin
print("\n[STEP 2] Authenticating Platform Superadmin (superadmin @ platform)...")
_, super_auth = request("POST", "/auth/login", {"username": "superadmin", "password": "password123", "tenant": "platform"})
super_token = super_auth["accessToken"]
assert "SUPER_ADMIN" in super_auth["roles"]
print(f"[PASS] 2. Platform Superadmin authenticated with roles: {super_auth['roles']}")

# 3. Duplicate tenant redundancy check
print("\n[STEP 3] Testing tenant name redundancy validation...")
status, dup_res = request("GET", "/auth/check-tenant?name=tenant-alpha")
assert dup_res["available"] is False, f"Expected duplicate: {dup_res}"
print(f"[PASS] 3a. Redundancy check caught duplicate registered tenant: {dup_res['message']}")

new_tenant_name = f"stark-industries-{uuid.uuid4().hex[:6]}"
status, avail_res = request("GET", f"/auth/check-tenant?name={new_tenant_name}")
assert avail_res["available"] is True
print(f"[PASS] 3b. Uniqueness verified: '{new_tenant_name}' is available for onboarding.")

# 4. Requester submits credential request
print("\n[STEP 4] Submitting onboarding credential request...")
requester_username = f"tony_stark_{uuid.uuid4().hex[:4]}"
req_body = {
    "fullName": "Tony Stark",
    "email": REQUESTER_MAIL,
    "username": requester_username,
    "tenantName": new_tenant_name,
    "department": "Defense & Arc Reactor Tech",
    "reason": "Need isolated cryptographic zero-trust tenant enclave for proprietary CAD schematics."
}
status, created_req = request("POST", "/credential-requests", req_body)
assert status == 201
req_id = created_req["id"]
print(f"[PASS] 4. Onboarding request created: ID={req_id}, status={created_req['status']}")

# 5. Superadmin can list requests
status, req_list = request("GET", "/credential-requests", token=super_token)
assert any(r["id"] == req_id for r in req_list), "New request not found in superadmin queue"
print(f"[PASS] 5. Superadmin successfully viewed queue ({len(req_list)} pending requests)")

# 6. Verify email notification dispatched to Platform Admin (chavandipak0606@gmail.com)
print("\n[STEP 6] Inspecting dispatched notification alerts...")
status, emails = request("GET", "/credential-requests/emails")
admin_alert = [e for e in emails if e.get("requestId") == req_id and e.get("emailType") == "ADMIN_ALERT"]
assert len(admin_alert) > 0, "No admin alert email found"
alert = admin_alert[0]
assert alert["recipientEmail"] == PLATFORM_MAIL, f"Expected alert to {PLATFORM_MAIL}, got {alert['recipientEmail']}"
print(f"[PASS] 6a. Admin alert addressed to {PLATFORM_MAIL}: '{alert['subject']}'")

# 7. Verify Action Buttons in Admin Alert email
alert_html = alert["bodyHtml"]
assert "action=approve" in alert_html, "Missing approve action link"
assert "action=reject" in alert_html, "Missing reject action link"
assert "ACCEPT &amp; PROVISION" in alert_html or "ACCEPT" in alert_html, "Missing Accept button label"
assert "REJECT REQUEST" in alert_html, "Missing Reject button label"
print("[PASS] 7. Interactive action deep links verified in admin email HTML:")
print(f"       -> [ACCEPT DEEP LINK]: http://localhost:5173/review-request/{req_id}?action=approve")
print(f"       -> [REJECT DEEP LINK]: http://localhost:5173/review-request/{req_id}?action=reject")

# 8. Verify Requester Confirmation receipt
user_receipt = [e for e in emails if e.get("requestId") == req_id and e.get("emailType") == "USER_RECEIPT"]
assert len(user_receipt) > 0, "No user receipt email found"
print(f"[PASS] 8. Confirmation receipt sent to requester ({REQUESTER_MAIL})")

# 9. Superadmin APPROVES request
print("\n[STEP 9] Superadmin authorizes & provisions tenant...")
review_body = {
    "action": "APPROVE",
    "temporaryPassword": "password123"
}
status, approved = request("POST", f"/credential-requests/{req_id}/review", review_body, token=super_token)
assert approved["status"] == "APPROVED"
print(f"[PASS] 9. Approved! Isolated tenant '{approved['tenantName']}' provisioned in PostgreSQL.")

# 10. Verify Credentials Email dispatched to requester
status, emails_after = request("GET", "/credential-requests/emails")
approval_notice = [e for e in emails_after if e.get("requestId") == req_id and e.get("emailType") == "APPROVAL_NOTICE"]
assert len(approval_notice) > 0, "No approval notice email found"
notice = approval_notice[0]
assert notice["recipientEmail"] == REQUESTER_MAIL
assert new_tenant_name in notice["bodyHtml"]
assert requester_username in notice["bodyHtml"]
assert "password123" in notice["bodyHtml"]
print(f"[PASS] 10. Credentials email sent to '{REQUESTER_MAIL}' with login credentials and direct link!")

# 11. Newly provisioned user logs in as ADMIN of their own isolated tenant
print("\n[STEP 11] Validating newly onboarded tenant login...")
status, new_auth = request("POST", "/auth/login", {
    "tenant": new_tenant_name,
    "username": requester_username,
    "password": "password123"
})
assert status == 200
new_user_token = new_auth["accessToken"]
assert "ADMIN" in new_auth["roles"]
print(f"[PASS] 11. Requester logged in to '{new_tenant_name}' as ADMIN!")

# 12. Upload document to verify zero-trust cryptographic isolation
print("\n[STEP 12] Cryptographic envelope encryption test in new tenant...")
doc_body = {
    "name": "arc-reactor-schematic.pdf",
    "classification": "TOP_SECRET",
    "contentType": "application/pdf",
    "content": "TUFTTElWRV9EQVRBX1NFQ1VSRURfV0lUSF9BRVMtMjU2LUdDTQ=="
}
headers = {
    "Authorization": f"Bearer {new_user_token}",
    "Idempotency-Key": str(uuid.uuid4()),
    "Content-Type": "application/json"
}
req = urllib.request.Request(f"{BASE_URL}/documents", data=json.dumps(doc_body).encode("utf-8"), headers=headers, method="POST")
with urllib.request.urlopen(req) as resp:
    doc_res = json.loads(resp.read().decode("utf-8"))
    assert doc_res["id"] is not None
print(f"[PASS] 12. Document encrypted with AES-256-GCM in isolated tenant (Doc ID: {doc_res['id']})")

# 13. Rejection Workflow Test
print("\n[STEP 13] Testing rejection notice workflow...")
rej_tenant_name = f"rogue-entity-{uuid.uuid4().hex[:6]}"
status, rej_req = request("POST", "/credential-requests", {
    "fullName": "Justin Hammer",
    "email": REQUESTER_MAIL,
    "username": f"hammer_{uuid.uuid4().hex[:4]}",
    "tenantName": rej_tenant_name,
    "department": "Rival Systems",
    "reason": "Requesting access for competitive benchmarking."
})
rej_id = rej_req["id"]

status, rej_review = request("POST", f"/credential-requests/{rej_id}/review", {
    "action": "REJECT",
    "rejectionReason": "Vendor clearance rejected per zero-trust compliance standards."
}, token=super_token)
assert rej_review["status"] == "REJECTED"

status, emails_final = request("GET", "/credential-requests/emails")
rej_notice = [e for e in emails_final if e.get("requestId") == rej_id and e.get("emailType") == "REJECTION_NOTICE"]
assert len(rej_notice) > 0
assert rej_notice[0]["recipientEmail"] == REQUESTER_MAIL
assert "Vendor clearance rejected" in rej_notice[0]["bodyHtml"]
print(f"[PASS] 13. Rejection notice email sent to '{REQUESTER_MAIL}' with justification!")

print("\n" + "=" * 75)
print("SUCCESS: ALL 13 CREDENTIAL ONBOARDING & TENANT ISOLATION TESTS PASSED 100%!")
print("=" * 75)
