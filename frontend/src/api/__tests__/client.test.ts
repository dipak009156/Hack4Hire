import { describe, it, expect, beforeEach, vi } from 'vitest';
import { apiRequest, ApiError, setAuthToken, getLastCorrelationId } from '../client';
import { generateIdempotencyKey } from '../../utils/idempotency';

describe('API Client & Security Invariants', () => {
  beforeEach(() => {
    setAuthToken(null);
    vi.restoreAllMocks();
  });

  it('never sends tenant headers in API requests (Invariant I2)', async () => {
    let capturedHeaders: Headers | undefined;

    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
      capturedHeaders = new Headers(init?.headers);
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ 'X-Correlation-ID': 'test-corr-123' }),
        json: () => Promise.resolve({ success: true }),
      });
    }));

    await apiRequest('/documents');

    expect(capturedHeaders?.has('X-Tenant-ID')).toBe(false);
    expect(capturedHeaders?.has('X-Tenant')).toBe(false);
  });

  it('attaches in-memory Bearer token to requests', async () => {
    setAuthToken('sample-jwt-token');
    let authHeader: string | null = null;

    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
      authHeader = new Headers(init?.headers).get('Authorization');
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        json: () => Promise.resolve([]),
      });
    }));

    await apiRequest('/documents');

    expect(authHeader).toBe('Bearer sample-jwt-token');
  });

  it('includes Idempotency-Key header when provided (Invariant I4)', async () => {
    let idempotencyHeader: string | null = null;
    const testKey = generateIdempotencyKey('test-op');

    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
      idempotencyHeader = new Headers(init?.headers).get('Idempotency-Key');
      return Promise.resolve({
        ok: true,
        status: 201,
        headers: new Headers(),
        json: () => Promise.resolve({ id: 'doc-123' }),
      });
    }));

    await apiRequest('/documents', {
      method: 'POST',
      idempotencyKey: testKey,
    });

    expect(idempotencyHeader).toBe(testKey);
  });

  it('maps 404 responses uniformly to "Not found" (Side-channel leak prevention)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => {
      return Promise.resolve({
        ok: false,
        status: 404,
        headers: new Headers({ 'X-Correlation-ID': 'corr-404' }),
        json: () => Promise.resolve({
          title: 'Not Found',
          detail: 'Document 12345 in tenant ABC does not exist',
        }),
      });
    }));

    await expect(apiRequest('/documents/12345')).rejects.toThrow(ApiError);

    try {
      await apiRequest('/documents/12345');
    } catch (err) {
      const apiErr = err as ApiError;
      expect(apiErr.status).toBe(404);
      expect(apiErr.problem.detail).toBe('The requested resource was not found');
      expect(apiErr.correlationId).toBe('corr-404');
    }
  });

  it('tracks X-Correlation-ID from response headers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => {
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ 'X-Correlation-ID': 'uuid-correlation-999' }),
        json: () => Promise.resolve({ status: 'OK' }),
      });
    }));

    await apiRequest('/health');
    expect(getLastCorrelationId()).toBe('uuid-correlation-999');
  });
});
