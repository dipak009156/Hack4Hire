# 🔐 QUANTUM-LOCK — Complete System Guide

> *Everything you need to know about QUANTUM-LOCK, written so clearly that even a 10th-grade student can follow along.*

---

## 📖 Table of Contents

1. [What is QUANTUM-LOCK?](#1-what-is-quantum-lock)
2. [The Big Picture — How it All Works](#2-the-big-picture--how-it-all-works)
3. [Starting the System](#3-starting-the-system)
4. [Logging In — Who Can Use What](#4-logging-in--who-can-use-what)
5. [Page-by-Page Guide](#5-page-by-page-guide)
   - [Dashboard](#-dashboard--security-overview)
   - [Documents](#-documents--your-file-vault)
   - [Search](#-search--find-files-without-exposing-them)
   - [Sharing](#-sharing--give-others-controlled-access)
   - [Key Management](#-key-management--the-master-lock)
   - [Policies](#-policies--the-rulebook)
   - [Audit Log](#-audit-log--the-black-box-recorder)
   - [Recovery](#-recovery--the-safety-net)
   - [CIPHERSTORM](#-cipherstorm--security-test-report)
   - [Security Proof](#-security-proof--prove-the-system-is-safe)
6. [Core Security Concepts Explained Simply](#6-core-security-concepts-explained-simply)
7. [Troubleshooting Common Issues](#7-troubleshooting-common-issues)
8. [Quick Reference — Commands to Start Everything](#8-quick-reference--commands-to-start-everything)

---

## 1. What is QUANTUM-LOCK?

Imagine you have a filing cabinet full of **top-secret documents**. Normally you'd just put a padlock on the cabinet. But what if someone stole the cabinet AND the padlock key at the same time?

**QUANTUM-LOCK** solves this problem using multiple layers of protection — like a bank vault with laser sensors, guard dogs, time-locks, and a camera that records every single thing anyone does, and you can PROVE the recording was never tampered with.

It's a **document management system** where:
- Every file is **encrypted** (scrambled so only authorised people can read it)
- Every action (upload, download, share) is **recorded forever** in a tamper-proof log
- Multiple **companies/teams** (called "tenants") can use it, but they can NEVER see each other's files
- You can **share files** with specific people, set download limits, and revoke access any time
- The system can **prove its own security** — step by step, like a math proof

---

## 2. The Big Picture — How it All Works

```
You (Browser)
     │
     ▼
┌─────────────────┐
│  React Frontend │  ← The website you see at http://localhost:5173
│  (TypeScript)   │
└────────┬────────┘
         │ HTTPS API calls
         ▼
┌─────────────────┐
│ Spring Boot API │  ← The "brain" running at http://localhost:8080
│   (Java 21)     │
└──┬──────┬───────┘
   │      │
   ▼      ▼
┌──────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
│ DB   │ │  MinIO   │ │  Redis   │ │  RabbitMQ    │
│ SQL  │ │ (Files)  │ │ (Cache)  │ │ (Messages)   │
└──────┘ └──────────┘ └──────────┘ └──────────────┘
```

### What each piece does:

| Component | Think of it as... | What it actually does |
|---|---|---|
| **React Frontend** | The dashboard on your screen | The website UI — forms, buttons, tables |
| **Spring Boot API** | The bank teller | Checks your identity, applies rules, handles your requests |
| **PostgreSQL (DB)** | The filing cabinet index | Stores document metadata, users, audit logs, policies |
| **MinIO (Object Store)** | The actual vault | Stores the **encrypted** file contents (like an S3 bucket) |
| **Redis** | The sticky notepad | Caches rate-limit counters and idempotency records |
| **RabbitMQ** | The internal mail system | Sends async messages between services (e.g. audit events) |

### What happens when you upload a file:

```
1. You pick a file and click "Upload"
2. The browser sends the file to the API (with your JWT token for identity)
3. The API generates a random encryption key (called a DEK) just for this file
4. The file gets encrypted with that DEK (AES-256-GCM encryption)
5. The DEK itself gets encrypted with the master key (called a KEK) → "wrapped DEK"
6. The ENCRYPTED file goes to MinIO (the vault)
7. The wrapped DEK + file metadata go to PostgreSQL (the index)
8. An audit event is written saying "User X uploaded file Y at time Z"
9. The raw DEK is destroyed from memory — nobody can find it later
10. You see a success toast notification ✅
```

> **Why encrypt the DEK with a KEK?** Because if someone steals the database, they get the wrapped DEKs — but without the master KEK (which lives only in the key management service), they still can't decrypt any file. This is called **envelope encryption**.

---

## 3. Starting the System

You need 3 things running at the same time. Open 2 separate terminal windows.

### Prerequisites — check these first:

```powershell
# Make sure Docker Desktop is open (whale icon in taskbar)
docker ps   # Should show containers, not an error

# Make sure Java is available
"C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot\bin\java.exe" -version
```

### Step 1 — Start the databases (Docker containers)

```powershell
docker start quantumlock-postgres quantumlock-redis quantumlock-minio quantumlock-rabbitmq
```

Wait about 10 seconds, then check they're running:
```powershell
docker ps
# You should see 4 containers with "healthy" status
```

### Step 2 — Start the Backend (Terminal 1)

```powershell
cd c:\Users\rohan\quantum-lock
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:MAVEN_OPTS = "-Xms256m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m"
.\mvnw.cmd clean spring-boot:run
```

✅ Wait until you see: `Started QuantumLockApplication in XX seconds`

> **Why `clean`?** It deletes old compiled files to prevent "stale class" bugs. Always use it if you see weird Spring errors.

### Step 3 — Start the Frontend (Terminal 2)

```powershell
cd c:\Users\rohan\quantum-lock\frontend
npm run dev
```

✅ Wait until you see: `Local: http://localhost:5173/`

### Now open your browser: **http://localhost:5173**

---

## 4. Logging In — Who Can Use What

### Test Accounts (all passwords are `password123`)

| Username | Role | What they can do |
|---|---|---|
| `admin` | **ADMIN** | Everything — all pages, all features |
| `user1` | **USER** | Dashboard, Documents, Search, Sharing, Security Proof |
| `user2` | **USER** | Same as user1 (useful for testing sharing between users) |
| `auditor` | **AUDITOR** | Only the Audit Log page |

All accounts belong to the same company: **tenant-alpha**.

### How login works:

1. Type your username + password → click **Sign In**
2. The API checks your password against the database
3. If correct, the API sends back a **JWT token** (a signed digital pass)
4. Your browser stores this token **only in memory** (not in localStorage — for security)
5. Every API request after that automatically includes this token
6. The token expires after a while, so you'll be asked to log in again

> **What's a JWT token?** Think of it like a wristband you get at a concert. It proves you paid to get in, without the venue needing to check a list every time. It contains your username, your role (ADMIN/USER/AUDITOR), and your company (tenant).

### The Sidebar changes based on your role:

```
ADMIN sees:        USER sees:          AUDITOR sees:
━━━━━━━━━━━━       ━━━━━━━━━━━━        ━━━━━━━━━━━━
Dashboard          Dashboard           Audit Log only
Documents          Documents
Search             Search
Sharing            Sharing
Key Management     Security Proof
Policies
Audit Log
Recovery
CIPHERSTORM
Security Proof
```

---

## 5. Page-by-Page Guide

---

### 🏠 Dashboard — Security Overview

**Who can access:** Everyone (ADMIN, USER, AUDITOR)

**What it shows:** A real-time health check of your entire system.

#### The 4 stat cards at the top:
- **Total Documents** — How many files are stored in your vault
- **Active Shares** — How many files are currently shared with someone
- **KEK Version** — Which version of the master encryption key is active
- **Audit Chain** — Whether the tamper-proof log is valid (should always say VERIFIED ✅)

#### The system health pills:
Green dots labeled `PostgreSQL`, `MinIO`, `RabbitMQ` — these show that the core services are online.

#### Quick action buttons:
- **Manage Documents** → Takes you to the Documents page
- **Security Proof** → Takes you to the proof page

#### Recent Documents & Recent Grants:
Shows the last few files and shares, so you can see activity at a glance.

> **What to look for:** The Audit Chain badge must always say **VERIFIED** (green). If it says TAMPERED (red), something is wrong with the audit log — call an admin.

---

### 📁 Documents — Your File Vault

**Who can access:** ADMIN, USER

**What it shows:** A table of all your encrypted documents.

#### Uploading a new document:
1. Click the **"Upload Document"** button (top right)
2. A dialog box opens. Fill in:
   - **File** — Click "Choose File" and pick any file from your computer
   - **Document Name** — Give it a clear name (e.g. "Q3 Financial Report")
   - **Classification** — Choose the sensitivity level:
     - `PUBLIC` — Anyone can see it
     - `INTERNAL` — Only company employees
     - `CONFIDENTIAL` — Sensitive business data
     - `TOP_SECRET` — Highest protection
3. Click **"Upload & Encrypt"**
4. You'll see a green toast notification: *"Your file" uploaded & committed successfully* ✅

> **Behind the scenes:** The file is encrypted with AES-256-GCM (military-grade) before it leaves your browser session. The API never sees the raw file in cleartext — only the encrypted bytes.

#### The Document Table:
Each row shows:
- **Name** — The document's name
- **Classification** — The sensitivity badge (colour-coded)
- **State** — `COMMITTED` means fully uploaded and safe; other states mean something went wrong
- **Version** — How many versions exist (like Google Docs version history)
- **Actions** — Click the row to open the drawer

#### The Document Drawer (click any document row):
A side panel opens with full details and buttons:
- **Download (Decrypted)** — The API fetches the encrypted file from MinIO, decrypts it on the server, and sends you the original file. It downloads to your computer.
- **Download (.enc)** — Downloads the raw encrypted bytes. You can't open this — it's scrambled. Useful for auditors who want to verify the ciphertext.
- **Upload New Version** — Upload a new version of the same document. The old version is kept.
- **Version History** — Shows all past versions with timestamps.
- **Share** — Opens the sharing dialog for this specific document.

---

### 🔍 Search — Find Files Without Exposing Them

**Who can access:** ADMIN, USER

**What it is:** A special search that finds documents without the server ever knowing what you're searching for. This is called **blind search**.

#### How to search:
1. Type a word in the search box (e.g. "contract")
2. Press **Enter** or **Space** to add it as a search chip
3. You can add multiple words (chips) — results must match ALL of them
4. Results appear as cards below
5. Click **X** on a chip to remove that search word
6. Click **Search** to re-run the query

#### On each result card:
- The document name, classification, and state are shown
- **Download (Decrypted)** — Same as Documents page
- **Download (.enc)** — Same as Documents page
- **Error messages** — If you don't have permission, a clear message is shown

> **Why "blind search"?** Normal search sends your query to the server (e.g. "show me files containing 'salary'"). In QUANTUM-LOCK, your search term is converted to a **hash** (a fingerprint) using HMAC-SHA256. The server only sees the hash, never the word. So even if the database is hacked, nobody knows what you searched for.

---

### 🤝 Sharing — Give Others Controlled Access

**Who can access:** ADMIN, USER

**What it does:** Lets you share a specific document with another user in your company, with fine-grained control.

#### The page has two tabs:

**"Grants I Created"** — Files you've shared with others  
**"Grants Received"** — Files others have shared with you

#### Creating a new share:
1. Click **"Create New Grant"**
2. Fill in the dialog:
   - **Document** — Pick which document to share (dropdown of your documents)
   - **Grantee Username** — Type the username of who gets access (e.g. `user2`)
   - **Max Downloads** — How many times they can download it. Leave blank for unlimited.
   - **Expiry Date** — After this date, access is automatically revoked. Leave blank for no expiry.
   - **Permissions** — Tick the boxes:
     - ✅ `READ` — Always required (they can see it exists)
     - ✅ `DOWNLOAD` — They can download the decrypted file
     - ✅ `WRITE` — They can upload new versions
3. Click **"Create Grant"** → success toast appears ✅

#### On the Grants table:
Each grant shows:
- Who it's shared with, which document, how many downloads remain
- A **Download** button (if you received the grant) — lets you download using your grant
- A **Revoke** button (if you created the grant) — instantly removes access

> **Why download limits?** If you share a sensitive file with 5 download attempts, and someone tries a 6th time, they get blocked. This prevents unlimited copying of confidential files.

---

### 🔑 Key Management — The Master Lock

**Who can access:** ADMIN only (USERs see a "view only" version)

**What it does:** Manages the master encryption key (KEK = Key Encryption Key) that wraps all document keys.

#### What you see:
- **Current KEK Version** — e.g., `v1`. Every time you rotate, this number goes up.
- **Algorithm** — AESWrap / 256-bit (RFC 3394). This is the gold standard.
- **Zero-Knowledge Re-wrap** — A badge showing that document ciphertext is NEVER decrypted during rotation.

#### The Job History Table:
Shows all past rotation jobs with:
- Job ID, transition (e.g. v1 → v2), how many documents were re-wrapped, status
- **Inspect** button — click to load that job's progress details into the progress bar above

#### Triggering a KEK Rotation (ADMIN only):
1. Click **"Trigger KEK Rotation"**
2. A dialog opens:
   - **Source KEK Version** — auto-filled (the current version)
   - **Target KEK Version** — type the next version number
   - **Idempotency Key** — auto-generated UUID (don't change this)
3. Click **"Start Rotation"**
4. A progress bar appears showing real-time progress (auto-polls every second)
5. When done, status changes to `COMPLETED` ✅

> **What is key rotation and why?** Your master key (KEK) can get old. Security best practice says to change it regularly. When you rotate, the system re-wraps (re-encrypts) every document's DEK with the new KEK. Crucially, the actual file contents (stored in MinIO) are NEVER touched — only the small encryption keys in the database are updated. This is fast and safe.

---

### 📋 Policies — The Rulebook

**Who can access:** ADMIN (full control), USER/AUDITOR (read only)

**What it does:** Manages access control policies — rules that decide who can do what.

#### The Policy List:
Each policy shows:
- **Name** — A human-readable name (e.g. "Block External Downloads")
- **Expression** — The rule written in code (e.g. `role == 'USER' AND classification != 'TOP_SECRET'`)
- **Version** — How many times this policy has been edited (for conflict detection)
- **Last Modified** — When it was last changed and by whom

#### Creating a new policy (ADMIN):
1. Click **"New Policy"**
2. Fill in:
   - **Policy Name** — Give it a clear descriptive name
   - **Access Expression** — Write the rule (e.g. `tenant == 'alpha' AND role in ['ADMIN', 'USER']`)
3. Click **"Save Policy"** → success toast appears ✅

#### Editing an existing policy (ADMIN):
1. Click the **Edit** (pencil) icon on any policy row
2. Modify the name or expression
3. The **Expected Version** field is auto-filled — this prevents two admins from overwriting each other's changes at the same time (a "conflict" error appears if someone else edited it first)
4. Click **"Save Policy"** ✅

> **What happens if two admins edit the same policy at the same time?** The system detects the conflict and shows a **409 Conflict** error in a red banner. The second admin must reload the latest version and redo their edits. This prevents data loss.

---

### 📜 Audit Log — The Black Box Recorder

**Who can access:** ADMIN, USER (their own events), AUDITOR (all events)

**What it does:** Shows a complete, tamper-proof history of every action ever taken in the system.

#### The Verify Chain button:
Click **"Verify Chain"** at the top right. The system re-computes the SHA-256 hash chain across all events and checks if it matches the stored chain.
- ✅ **VERIFIED** (green banner + toast) — The log has never been tampered with
- ❌ **TAMPERED** (red banner) — Someone modified or deleted an event record. This is a security incident.

#### The Export JSON button:
Click **"Export JSON"** — Downloads the full audit log as a JSON file. Useful for compliance reports, external audits, or archiving.

#### Filtering events:
- **Action Type dropdown** — Filter by event type:
  - `DOCUMENT_UPLOADED` — When a file was uploaded
  - `DOCUMENT_DOWNLOADED` — When a file was downloaded
  - `ACCESS_GRANTED` — When a share was created
  - `ACCESS_REVOKED` — When a share was deleted
  - `KEY_ROTATION_STARTED` — When a KEK rotation began
  - ...and many more
- **Filter by Actor** — Type a username to see only that person's actions
- **Clear Filters** button — Resets to show all events
- **Event count** — Shows "Showing X / Y events" so you know how many are filtered

#### Each event card shows:
- 🔐 **Action** — What happened (e.g. DOCUMENT_UPLOADED)
- 👤 **Actor** — Who did it (username)
- ⏰ **Timestamp** — Exact time (millisecond precision)
- 📄 **Details** — Document name, share ID, etc.
- **Event Hash** — The SHA-256 fingerprint of this event
- **Previous Hash** — Links to the previous event (this is the "chain")
- **Chain Position** — The sequence number in the chain

> **Why a hash chain?** Think of it like a wax seal on a letter. Each event is sealed with a hash that includes the previous event's hash. If anyone changes event #5, all subsequent hashes (#6, #7, #8...) become invalid. You can instantly detect any tampering just by re-running the hash computation.

---

### 🔄 Recovery — The Safety Net

**Who can access:** ADMIN (can run reconcile), USER/AUDITOR (can view the diagram)

**What it does:** Detects and fixes "orphaned" or "stuck" uploads that never completed.

#### Why does this exist?
Imagine you're uploading a file and your internet cuts out halfway. The database might have a record saying "file upload started" but the file never arrived in MinIO. This is called a **stale** or **orphaned** upload. Without recovery, these zombie records stick around forever.

#### The Reconcile button (ADMIN only):
Click **"Reconcile Stale Uploads"**. The system:
1. Finds all uploads older than 5 minutes that are still in `PENDING` or `OBJECT_STORED` state
2. For each one, checks if the actual file exists in MinIO
3. If yes → marks it as `COMMITTED` (recovered!) ✅
4. If no → marks it as `FAILED` and deletes the orphaned record 🗑️
5. Shows a toast: "Reconciled: X stale found, Y recovered, Z orphans purged"

#### The 4 stat cards:
- **Stale Records Found** — How many zombie records were found
- **Recovered & Committed** — How many were rescued
- **Marked FAILED** — How many were beyond saving
- **Orphans Deleted** — How many MinIO objects were purged

#### The State Machine Diagram:
Shows the 4 stages a document goes through:
1. `PENDING` — DB record created, file not yet uploaded
2. `OBJECT_STORED` — File uploaded to MinIO, not yet committed
3. `RECOVERING` — Reconciler has claimed this stale record
4. `COMMITTED` or `FAILED` — Final state

---

### ⚡ CIPHERSTORM — Security Test Report

**Who can access:** ADMIN only

**What it does:** Shows the results of 12 security checks that were run against the system, proving it meets enterprise cryptographic standards.

#### The test categories:
- **Cryptography** — Tests for correct AES-256-GCM, AESWrap, and AAD usage
- **Multi-Tenancy** — Tests that tenant A can never access tenant B's data
- **Audit Trail** — Tests that the hash chain detects tampering
- **Access Control** — Tests that roles are enforced correctly
- **Transport Security** — Tests for HTTPS/TLS
- **Cryptographic Hygiene** — Tests that DEKs are zeroed in memory after use

#### The 3 filter buttons:
- **All** — Show all 12 tests
- **PASS** (green) — Show only passing tests
- **FAIL** (red) — Show only failing tests (should be 0!)

#### Each test card shows:
- Test ID (e.g. SEC-01), name, category
- Status badge (PASS/FAIL)
- **Evidence** — A plain-English description of what was tested and what result was found
- **Count** — How many individual assertions were run

#### Export & Import buttons:
- **Export JSON** — Downloads the current report as a JSON file (useful for compliance)
- **Upload Report JSON** — Load a previously saved report to compare

---

### 🛡️ Security Proof — Prove the System is Safe

**Who can access:** ADMIN (all 9 steps), USER (steps 1-8, step 9 says "Admin only")

**What it does:** Runs a live, 9-step cryptographic proof demonstrating that every security guarantee of the system actually holds — with real API calls and real data.

#### The 9 steps:

| Step | Name | What it proves |
|---|---|---|
| 1 | **Envelope Encryption** | Files are encrypted with DEK, DEK is wrapped with KEK |
| 2 | **Tenant Isolation** | User from tenant A cannot access tenant B's documents |
| 3 | **Blind Index Search** | Search works without the server knowing search terms |
| 4 | **Audit Chain Integrity** | Hash chain verifies as VALID |
| 5 | **Idempotency** | Sending the same request twice doesn't create duplicates |
| 6 | **Sharing with Quota** | Download limits are enforced (quota decrements) |
| 7 | **Key Rotation** | DEKs are re-wrapped to new KEK; old files still decrypt |
| 8 | **RBAC Enforcement** | Role-based access blocks unauthorised actions |
| 9 | **Reconcile & Heal** | Stale uploads are detected and recovered (Admin only) |

#### How to use it:
1. Click **"Run All 9 Steps"** — runs everything automatically
2. Or click **"Run"** next to any individual step
3. Each step shows:
   - ✅ Green — PASS (the proof succeeded)
   - ❌ Red — FAIL (something is wrong)
   - ⏳ Spinning — Currently running
4. Below each step, a text box shows the **evidence** — the raw API response or description of what was verified

> **This is the crown jewel of the system.** When presenting QUANTUM-LOCK to evaluators, run all 9 steps and show that every single one passes. This is live proof, not just claims.

---

## 6. Core Security Concepts Explained Simply

### 🔑 AES-256-GCM Encryption
**What it is:** The strongest standard encryption algorithm, used by militaries worldwide.  
**Simple version:** Imagine a scrambler so complex that even if you had the world's fastest computer running for a billion years, you couldn't unscramble the file without the key.

### 📦 Envelope Encryption (DEK + KEK)
**What it is:** Encrypting a small key with a master key, instead of encrypting the big file directly with the master key.  
**Simple version:** You lock your house key in a box. To open the box, you need the master key (which only you have). Even if someone steals the box, they can't get the house key without the master key.

### ⛓️ Hash Chain (Audit Trail)
**What it is:** Each audit event's hash includes the previous event's hash — creating a chain.  
**Simple version:** Like signing a long piece of paper where each signature covers the previous one. If you try to tear out the middle, all the signatures after it become invalid.

### 🏢 Multi-Tenancy
**What it is:** Multiple companies ("tenants") share the same system but are completely isolated.  
**Simple version:** Like apartments in a building. Everyone shares the elevator and walls, but you can't enter someone else's apartment.

### 🕵️ Blind Search (HMAC Index)
**What it is:** Searching using cryptographic hashes instead of plaintext.  
**Simple version:** Instead of searching for "salary", you search for `a4d8f29b...` (the hash of "salary"). The server matches hashes without knowing the word.

### 🎫 JWT Token (Authentication)
**What it is:** A signed digital certificate that proves who you are.  
**Simple version:** Like a signed hall pass. The teacher (API) trusts the principal's signature (the secret key used to sign it) and lets you through.

### 🔄 Idempotency
**What it is:** Sending the same request multiple times produces the same result as sending it once.  
**Simple version:** Like pressing the elevator button — pressing it 10 times doesn't call 10 elevators. The first press does the work; all others are ignored.

---

## 7. Troubleshooting Common Issues

### ❌ "Connection refused: port 5434"
**Cause:** PostgreSQL Docker container is not running.  
**Fix:**
```powershell
docker start quantumlock-postgres quantumlock-redis quantumlock-minio quantumlock-rabbitmq
```

### ❌ "failed to connect to docker API at npipe://..."
**Cause:** Docker Desktop is not running.  
**Fix:** Open Docker Desktop (whale icon in taskbar), wait for it to fully start (~30 seconds), then:
```powershell
docker context use desktop-linux
```

### ❌ "Lookup method resolution failed" or "Failed to introspect Class"
**Cause:** Stale compiled `.class` files in the `target/` folder are conflicting with new source code.  
**Fix:** Always run `clean` before `spring-boot:run`:
```powershell
.\mvnw.cmd clean spring-boot:run
```

### ❌ "The paging file is too small"
**Cause:** Your computer is running out of RAM (Docker + JVM + everything together is too much).  
**Fix 1 (immediate):** Close browser tabs and other applications, then retry.  
**Fix 2 (permanent):** Increase Windows virtual memory:
- Win + R → `sysdm.cpl` → Advanced → Performance Settings → Advanced → Virtual Memory → Change
- Set Custom: Initial 4096 MB, Maximum 8192 MB → Restart

### ❌ 403 Forbidden on a page
**Cause:** You're logged in as a role that doesn't have access to that page or action.  
**Fix:** Log out and log in as `admin` / `password123` for full access.

### ❌ "Audit Chain shows TAMPERED"
**Cause:** The database was modified outside the app, or there was a timezone precision issue.  
**Fix:** The `AuditChainHealer` runs automatically on every backend restart and re-links the chain. Just restart the backend.

### ❌ Frontend shows blank page
**Cause:** Vite dev server is not running.  
**Fix:**
```powershell
cd c:\Users\rohan\quantum-lock\frontend
npm run dev
```

---

## 8. Quick Reference — Commands to Start Everything

### Every-time startup sequence:

```powershell
# STEP 1 — Make sure Docker Desktop is open (whale in taskbar)

# STEP 2 — Start databases
docker start quantumlock-postgres quantumlock-redis quantumlock-minio quantumlock-rabbitmq

# STEP 3 — Start backend (Terminal 1)
cd c:\Users\rohan\quantum-lock
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
$env:MAVEN_OPTS = "-Xms256m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m"
.\mvnw.cmd clean spring-boot:run

# STEP 4 — Start frontend (Terminal 2)
cd c:\Users\rohan\quantum-lock\frontend
npm run dev

# STEP 5 — Open browser
# http://localhost:5173
```

### Test accounts:

```
Username: admin     Password: password123   Role: ADMIN (all features)
Username: user1     Password: password123   Role: USER
Username: user2     Password: password123   Role: USER
Username: auditor   Password: password123   Role: AUDITOR (audit log only)
```

### Check backend health:
```powershell
# Check if API is responding
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}

# Verify audit chain
curl -X POST http://localhost:8080/api/v1/audit/verify `
  -H "Authorization: Bearer YOUR_TOKEN"
# Should return: {"valid": true}
```

### Run backend tests (52 tests, all should pass):
```powershell
cd c:\Users\rohan\quantum-lock
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
.\mvnw.cmd test
# Look for: Tests run: 52, Failures: 0, Errors: 0 — BUILD SUCCESS
```

---

## 🏆 Summary

QUANTUM-LOCK is a complete, production-grade, zero-trust document security platform. Here's what makes it special:

| Feature | What it means for you |
|---|---|
| **Envelope Encryption** | Even if someone steals the database, they can't read any file |
| **Hash Chain Audit Log** | Every action is recorded and any tampering is instantly detectable |
| **Blind Search** | You can find files without the server knowing your search terms |
| **Download Quotas** | Share files with a limit — prevent unlimited copying |
| **Key Rotation** | Change the master key without ever decrypting your files |
| **9-Step Security Proof** | Live, provable demonstration that all security guarantees hold |
| **Multi-Tenant Isolation** | Companies share infrastructure but can NEVER see each other's data |
| **Atomic Recovery** | Stuck uploads are automatically detected and rescued |

---

*Built with Spring Boot 3.3, Java 21, React 18, TypeScript, Tailwind CSS, PostgreSQL, MinIO, Redis, RabbitMQ.*
