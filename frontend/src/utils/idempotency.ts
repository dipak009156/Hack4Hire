/**
 * Generates and tracks Idempotency-Key headers (Invariant I4).
 * A fresh UUID is generated for each operation; an explicit retry can reuse the same key.
 */

// Simple in-memory map of endpoint -> last key used, to allow explicit retry
const lastKeys = new Map<string, string>();

export function generateIdempotencyKey(operationTag?: string): string {
  const key = crypto.randomUUID();
  if (operationTag) {
    lastKeys.set(operationTag, key);
  }
  return key;
}

export function getLastIdempotencyKey(operationTag: string): string {
  return lastKeys.get(operationTag) || generateIdempotencyKey(operationTag);
}

