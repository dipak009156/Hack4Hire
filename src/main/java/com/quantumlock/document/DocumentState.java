package com.quantumlock.document;

/**
 * State machine for a document version upload (ADR-004).
 *
 * <pre>
 * PENDING ──────────────────────────► FAILED
 *    │                                  ▲
 *    ▼ (object stored in MinIO)         │ (unrecoverable / missing object)
 * OBJECT_STORED ──► RECOVERING ─────────┘
 *    │                  │
 *    ▼ (DB commit)      ▼ (verified & committed by reconciler)
 * COMMITTED ◄───────────┘
 * </pre>
 *
 * Stale {@code PENDING} or {@code OBJECT_STORED} records older than 5 minutes
 * are transitioned to {@code RECOVERING} by the admin recovery endpoint, then
 * completed or removed.
 */
public enum DocumentState {
    PENDING,
    OBJECT_STORED,
    RECOVERING,
    COMMITTED,
    FAILED
}
