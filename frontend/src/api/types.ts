/**
 * TypeScript types matching QUANTUM-LOCK backend entities & OpenAPI spec.
 */

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  retryAfterSeconds?: number;
  invalidParams?: Record<string, string>;
  [key: string]: unknown;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  tenantId?: string;
  tenant?: string;
  roles: string[];
}

export type DocumentClassification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';

export type DocumentState = 'PENDING' | 'OBJECT_STORED' | 'RECOVERING' | 'COMMITTED' | 'FAILED';

export interface DocumentResponse {
  id: string;
  tenantId: string;
  ownerId: string;
  name: string;
  classification: DocumentClassification;
  currentVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentVersionResponse {
  id: string;
  documentId: string;
  version: number;
  algorithm: string;
  state: DocumentState;
  sizeBytes: number;
  contentType: string;
  ciphertextDigest: string;
  kekVersion: number;
  createdAt: string;
}

export interface DownloadResponse {
  documentId: string;
  version: number;
  name: string;
  contentType: string;
  contentBase64: string;
  sizeBytes: number;
}

export interface EncryptedDownloadResponse {
  documentId: string;
  version: number;
  name: string;
  contentType: string;
  ciphertextBase64: string;
  nonce: string;
  wrappedDek: string;
  kekVersion: number;
  ciphertextDigest: string;
  sizeBytes: number;
}

export interface AccessGrantResponse {
  id: string;
  documentId: string;
  grantorId: string;
  granteeId: string;
  permissions: string[];
  purpose?: string;
  expiresAt?: string;
  maxDownloads?: number;
  remainingDownloads?: number;
  active: boolean;
  revokedAt?: string;
  createdAt: string;
}

export interface ShareDocumentRequest {
  granteeId: string;
  permissions?: string[];
  purpose?: string;
  expiresAt?: string;
  maxDownloads?: number;
}

export interface KeyRotationResponse {
  id: string;
  tenantId: string;
  sourceKekVersion: number;
  targetKekVersion: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  totalDocuments: number;
  processedDocuments: number;
  errorMessage?: string;
}

export interface PolicyDto {
  id: string;
  policyId: string;
  tenantId: string;
  name: string;
  expression: string;
  version: number;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyRequest {
  name: string;
  expression: string;
  version?: number;
}

export interface AuditEvent {
  id: string;
  sequence?: number;
  chainPosition: number;
  tenantId: string;
  actorId?: string;
  actorName?: string;
  actor?: string;
  resourceType?: string;
  resourceId?: string;
  action: string;
  operationId?: string;
  policyVersion?: number;
  outcome: string;
  detail?: string;
  previousHash?: string;
  eventHash: string;
  createdAt: string;
}

export interface AuditVerificationResult {
  valid: boolean;
  totalEvents: number;
  firstInconsistentEvent?: {
    chainPosition: number;
    eventId: string;
    action: string;
    expectedHash?: string;
    actualHash?: string;
    expectedPreviousHash?: string;
    actualPreviousHash?: string;
    reason: string;
  };
  message: string;
}

export interface ReconcileResult {
  staleRecordsFound: number;
  recovered: number;
  markedFailed: number;
  objectsDeleted: number;
}

export interface CipherstormCheck {
  id: string;
  name: string;
  category: string;
  status: 'PASS' | 'FAIL';
  evidence: string;
  count?: number;
}

export interface CipherstormReport {
  timestamp: string;
  target: string;
  totalChecks: number;
  passed: number;
  failed: number;
  checks: CipherstormCheck[];
}

export interface SecurityProofItem {
  id: string;
  step: number;
  title: string;
  description: string;
  actionLabel: string;
  status: 'IDLE' | 'RUNNING' | 'SUCCESS' | 'ERROR';
  result?: string;
}

