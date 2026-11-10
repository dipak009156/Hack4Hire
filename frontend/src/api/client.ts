import { ProblemDetail } from './types';

// In-memory token storage (NEVER localStorage / sessionStorage)
let inMemoryToken: string | null = null;
let lastCorrelationId: string | null = null;
const correlationListeners = new Set<(id: string) => void>();

export function setAuthToken(token: string | null) {
  inMemoryToken = token;
}

export function getAuthToken(): string | null {
  return inMemoryToken;
}

export function getLastCorrelationId(): string | null {
  return lastCorrelationId;
}

export function onCorrelationIdChange(listener: (id: string) => void) {
  correlationListeners.add(listener);
  return () => correlationListeners.delete(listener);
}

function notifyCorrelationId(id: string) {
  lastCorrelationId = id;
  correlationListeners.forEach(fn => fn(id));
}

export class ApiError extends Error {
  public problem: ProblemDetail;
  public status: number;
  public correlationId?: string;

  constructor(status: number, problem: ProblemDetail, correlationId?: string) {
    super(problem.detail || problem.title || `API Error ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.correlationId = correlationId;
  }
}

export interface RequestOptions extends RequestInit {
  idempotencyKey?: string;
  skipAuth?: boolean;
}

export async function apiRequest<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers || {});

  // Add in-memory Bearer token
  if (!options.skipAuth && inMemoryToken) {
    headers.set('Authorization', `Bearer ${inMemoryToken}`);
  }

  // Idempotency-Key header if provided
  if (options.idempotencyKey) {
    headers.set('Idempotency-Key', options.idempotencyKey);
  }

  // Content-Type default
  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Enforce security rule: The UI NEVER passes tenant header or query param
  headers.delete('X-Tenant-ID');
  headers.delete('X-Tenant');

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
  const url = endpoint.startsWith('http')
    ? endpoint
    : endpoint.startsWith('/api')
      ? `${apiBase}${endpoint}`
      : `${apiBase}/api/v1${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Track correlation ID from response header
  const corrId = response.headers.get('x-correlation-id') || response.headers.get('X-Correlation-ID');
  if (corrId) {
    notifyCorrelationId(corrId);
  }

  // 204 No Content
  if (response.status === 204) {
    return {} as T;
  }

  if (!response.ok) {
    let problem: ProblemDetail;
    try {
      problem = await response.json();
    } catch {
      problem = {
        status: response.status,
        title: response.statusText,
        detail: response.status === 404 ? 'The requested resource was not found' : 'An error occurred processing the request'
      };
    }

    // Security rule: Treat 404 uniformly as "Not found"
    if (response.status === 404) {
      problem.detail = 'The requested resource was not found';
    }

    throw new ApiError(response.status, problem, corrId || undefined);
  }

  return response.json();
}

