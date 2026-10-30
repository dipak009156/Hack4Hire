import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiRequest, ApiError } from '../api/client';
import { DocumentResponse, DocumentVersionResponse, DocumentClassification, DownloadResponse } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { StateBadge, ClassificationBadge } from '../components/common/Badge';
import { Dialog } from '../components/common/Dialog';
import { Drawer } from '../components/common/Drawer';
import { Skeleton } from '../components/common/Skeleton';
import { EmptyState } from '../components/common/EmptyState';
import { generateIdempotencyKey } from '../utils/idempotency';
import {
  FileText,
  Upload,
  Download,
  Layers,
  Key,
  Shield,
  Clock,
  AlertCircle,
  FileCheck2,
} from 'lucide-react';

export const Documents: React.FC = () => {
  const queryClient = useQueryClient();

  // Selected document for Drawer inspection
  const [selectedDoc, setSelectedDoc] = useState<DocumentResponse | null>(null);

  // Upload modal state
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [isNewVersionOpen, setIsNewVersionOpen] = useState(false);

  // Upload form state
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState('');
  const [classification, setClassification] = useState<DocumentClassification>('INTERNAL');
  const [idempotencyKey, setIdempotencyKey] = useState(generateIdempotencyKey('upload-doc'));
  const [uploadError, setUploadError] = useState<string | null>(null);

  // Download state
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  // 1. Fetch documents
  const { data: documents, isLoading } = useQuery({
    queryKey: ['documents'],
    queryFn: () => apiRequest<DocumentResponse[]>('/documents'),
  });

  // 2. Fetch versions of selected document
  const { data: versions, isLoading: loadingVersions } = useQuery({
    queryKey: ['document-versions', selectedDoc?.id],
    queryFn: () => apiRequest<DocumentVersionResponse[]>(`/documents/${selectedDoc!.id}/versions`),
    enabled: !!selectedDoc,
  });

  // Helper: File to base64
  const fileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const res = reader.result as string;
        const base64 = res.split(',')[1] || res;
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  };

  // Upload Mutation
  const uploadMutation = useMutation({
    mutationFn: async ({ isNewVersion }: { isNewVersion: boolean }) => {
      if (!file) throw new Error('Please select a file to upload');

      const content = await fileToBase64(file);
      const docName = name.trim() || file.name;
      const contentType = file.type || 'application/octet-stream';

      if (isNewVersion && selectedDoc) {
        return apiRequest<DocumentResponse>(`/documents/${selectedDoc.id}/versions`, {
          method: 'POST',
          idempotencyKey,
          body: JSON.stringify({
            name: docName,
            classification,
            contentType,
            content,
          }),
        });
      } else {
        return apiRequest<DocumentResponse>('/documents', {
          method: 'POST',
          idempotencyKey,
          body: JSON.stringify({
            name: docName,
            classification,
            contentType,
            content,
          }),
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      if (selectedDoc) {
        queryClient.invalidateQueries({ queryKey: ['document-versions', selectedDoc.id] });
      }
      setIsUploadOpen(false);
      setIsNewVersionOpen(false);
      resetForm();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setUploadError(err.problem.detail || err.problem.title || 'Upload failed');
      } else if (err instanceof Error) {
        setUploadError(err.message);
      } else {
        setUploadError('Failed to upload document');
      }
    },
  });

  const resetForm = () => {
    setFile(null);
    setName('');
    setClassification('INTERNAL');
    setUploadError(null);
    setIdempotencyKey(generateIdempotencyKey('upload-doc'));
  };

  const handleFileChange = (selected: File | null) => {
    setFile(selected);
    if (selected && !name) {
      setName(selected.name);
    }
  };

  // Download Handler
  const handleDownload = async (docId: string, version?: number) => {
    setDownloadingId(docId);
    setDownloadError(null);
    try {
      const url = version 
        ? `/documents/${docId}/download?version=${version}`
        : `/documents/${docId}/download`;

      const res = await apiRequest<DownloadResponse>(url);

      // Trigger browser download from base64
      const byteCharacters = atob(res.contentBase64);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: res.contentType });

      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = res.name || 'document';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setDownloadError(err.problem.detail || 'Download access denied');
      } else {
        setDownloadError('Failed to download decrypted document');
      }
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
            Encrypted Documents
          </h1>
          <p className="text-sm text-brown/70 mt-1">
            Envelope-encrypted object storage backed by MinIO with AES-256-GCM.
          </p>
        </div>

        <Button
          onClick={() => {
            resetForm();
            setIsUploadOpen(true);
          }}
          variant="primary"
          size="md"
        >
          <Upload className="w-4 h-4 mr-2" />
          Upload Document
        </Button>
      </div>

      {downloadError && (
        <div className="p-4 rounded-xl bg-brick/10 border border-brick/30 text-brick text-sm flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <span>{downloadError}</span>
          </div>
          <button onClick={() => setDownloadError(null)} className="text-brick font-bold text-xs underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Documents Table */}
      <Card className="p-0 overflow-hidden">
        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : documents && documents.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand bg-sand/20 text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">Document Name</th>
                  <th className="py-3 px-4">Classification</th>
                  <th className="py-3 px-4">Current Version</th>
                  <th className="py-3 px-4">Uploaded</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand">
                {documents.map((doc) => (
                  <tr
                    key={doc.id}
                    className="hover:bg-sand/10 transition-colors cursor-pointer group"
                    onClick={() => setSelectedDoc(doc)}
                  >
                    <td className="py-3.5 px-4 font-medium text-brown flex items-center gap-2.5">
                      <div className="w-8 h-8 rounded-lg bg-terracotta/10 text-terracotta flex items-center justify-center shrink-0">
                        <FileText className="w-4 h-4" />
                      </div>
                      <span className="truncate max-w-xs">{doc.name}</span>
                    </td>
                    <td className="py-3.5 px-4">
                      <ClassificationBadge classification={doc.classification} />
                    </td>
                    <td className="py-3.5 px-4 font-mono font-medium text-brown">
                      v{doc.currentVersion}
                    </td>
                    <td className="py-3.5 px-4 text-brown/60 text-xs">
                      {new Date(doc.createdAt).toLocaleDateString()}
                    </td>
                    <td className="py-3.5 px-4 text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          onClick={() => handleDownload(doc.id)}
                          variant="ghost"
                          size="sm"
                          isLoading={downloadingId === doc.id}
                          title="Decrypt & Download"
                        >
                          <Download className="w-4 h-4" />
                        </Button>
                        <Button
                          onClick={() => setSelectedDoc(doc)}
                          variant="secondary"
                          size="sm"
                        >
                          <Layers className="w-3.5 h-3.5 mr-1" />
                          Versions
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={FileText}
            title="No Documents Yet"
            description="Upload your first envelope-encrypted document to this tenant."
            actionLabel="Upload Now"
            onAction={() => {
              resetForm();
              setIsUploadOpen(true);
            }}
          />
        )}
      </Card>

      {/* Upload Modal Dialog */}
      <Dialog
        isOpen={isUploadOpen || isNewVersionOpen}
        onClose={() => {
          setIsUploadOpen(false);
          setIsNewVersionOpen(false);
        }}
        title={isNewVersionOpen ? `Upload Version v${(selectedDoc?.currentVersion ?? 0) + 1}` : 'Upload New Document'}
        description="The document content is encrypted client-side via envelope AES-256-GCM before storage."
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            uploadMutation.mutate({ isNewVersion: isNewVersionOpen });
          }}
          className="space-y-4"
        >
          {uploadError && (
            <div className="p-3 rounded-lg bg-brick/10 border border-brick/30 text-brick text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{uploadError}</span>
            </div>
          )}

          {/* Drag & Drop File Zone */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Select File
            </label>
            <div
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                  handleFileChange(e.dataTransfer.files[0]);
                }
              }}
              className="border-2 border-dashed border-sand hover:border-terracotta rounded-xl p-6 text-center bg-white/50 cursor-pointer transition-colors"
              onClick={() => document.getElementById('file-upload-input')?.click()}
            >
              <input
                id="file-upload-input"
                type="file"
                className="hidden"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    handleFileChange(e.target.files[0]);
                  }
                }}
              />
              <Upload className="w-8 h-8 text-terracotta mx-auto mb-2" />
              {file ? (
                <div>
                  <div className="font-semibold text-sm text-brown">{file.name}</div>
                  <div className="text-xs text-brown/50">{(file.size / 1024).toFixed(1)} KB</div>
                </div>
              ) : (
                <div>
                  <div className="font-medium text-sm text-brown">Drag & drop or click to select</div>
                  <div className="text-xs text-brown/50 mt-1">Supports any binary or text file</div>
                </div>
              )}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Document Name
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. quarterly-financials.pdf"
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80 mb-1.5">
              Classification
            </label>
            <select
              value={classification}
              onChange={(e) => setClassification(e.target.value as DocumentClassification)}
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
            >
              <option value="INTERNAL">INTERNAL</option>
              <option value="PUBLIC">PUBLIC</option>
              <option value="CONFIDENTIAL">CONFIDENTIAL</option>
              <option value="RESTRICTED">RESTRICTED</option>
            </select>
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-semibold uppercase tracking-wider text-brown/80">
                Idempotency-Key (Auto-generated)
              </label>
              <button
                type="button"
                onClick={() => setIdempotencyKey(generateIdempotencyKey('upload-doc'))}
                className="text-[11px] text-terracotta hover:underline font-mono"
              >
                Refresh Key
              </button>
            </div>
            <input
              type="text"
              readOnly
              value={idempotencyKey}
              className="w-full px-3.5 py-2 rounded-xl border border-sand bg-sand/20 font-mono text-xs text-brown/70 select-all"
            />
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setIsUploadOpen(false);
                setIsNewVersionOpen(false);
              }}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={uploadMutation.isPending}
            >
              Confirm Upload
            </Button>
          </div>
        </form>
      </Dialog>

      {/* Document Detail Drawer */}
      <Drawer
        isOpen={!!selectedDoc}
        onClose={() => setSelectedDoc(null)}
        title={selectedDoc?.name || 'Document Details'}
        subtitle={`Tenant ID: ${selectedDoc?.tenantId}`}
      >
        {selectedDoc && (
          <div className="space-y-6">
            {/* Meta Card */}
            <Card className="p-4 space-y-3 bg-white/80">
              <div className="flex items-center justify-between pb-3 border-b border-sand">
                <span className="text-xs font-semibold text-brown/60 uppercase font-mono">Classification</span>
                <ClassificationBadge classification={selectedDoc.classification} />
              </div>
              <div className="flex items-center justify-between pb-3 border-b border-sand text-xs">
                <span className="font-semibold text-brown/60 uppercase font-mono">Current Version</span>
                <span className="font-mono font-bold text-brown">v{selectedDoc.currentVersion}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-brown/60 uppercase font-mono">Created</span>
                <span className="text-brown/80">{new Date(selectedDoc.createdAt).toLocaleString()}</span>
              </div>
            </Card>

            {/* Actions */}
            <div className="flex items-center gap-3">
              <Button
                onClick={() => {
                  resetForm();
                  setIsNewVersionOpen(true);
                }}
                variant="primary"
                size="sm"
                className="flex-1"
              >
                <Upload className="w-4 h-4 mr-1.5" />
                Upload New Version
              </Button>
              <Button
                onClick={() => handleDownload(selectedDoc.id, selectedDoc.currentVersion)}
                variant="secondary"
                size="sm"
                isLoading={downloadingId === selectedDoc.id}
              >
                <Download className="w-4 h-4 mr-1.5" />
                Download
              </Button>
            </div>

            {/* Versions List */}
            <div className="space-y-3">
              <h4 className="text-sm font-bold font-fraunces text-brown uppercase tracking-wider font-mono">
                Version History (AES-256-GCM)
              </h4>

              {loadingVersions ? (
                <div className="space-y-2">
                  <Skeleton className="h-16 w-full" />
                  <Skeleton className="h-16 w-full" />
                </div>
              ) : versions && versions.length > 0 ? (
                <div className="space-y-2.5">
                  {versions.map((ver) => (
                    <Card key={ver.id} className="p-3.5 space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-sm text-brown">v{ver.version}</span>
                          <StateBadge state={ver.state} />
                        </div>
                        <Button
                          onClick={() => handleDownload(selectedDoc.id, ver.version)}
                          variant="ghost"
                          size="sm"
                          className="h-7 text-xs"
                          isLoading={downloadingId === selectedDoc.id}
                        >
                          <Download className="w-3.5 h-3.5 mr-1" />
                          Download
                        </Button>
                      </div>

                      <div className="grid grid-cols-2 gap-2 text-xs text-brown/70 pt-1 font-mono">
                        <div className="flex items-center gap-1.5 truncate">
                          <Key className="w-3 h-3 text-terracotta shrink-0" />
                          <span>KEK v{ver.kekVersion}</span>
                        </div>
                        <div className="flex items-center gap-1.5 truncate">
                          <Shield className="w-3 h-3 text-olive shrink-0" />
                          <span>{ver.algorithm}</span>
                        </div>
                      </div>

                      <div className="text-[11px] font-mono text-brown/60 pt-1 border-t border-sand/50 truncate" title={ver.ciphertextDigest}>
                        <span className="font-semibold text-brown/40">SHA-256 Digest: </span>
                        {ver.ciphertextDigest}
                      </div>
                    </Card>
                  ))}
                </div>
              ) : (
                <div className="p-4 text-center text-xs text-brown/50">
                  No versions available.
                </div>
              )}
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

