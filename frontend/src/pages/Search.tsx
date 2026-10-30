import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiRequest } from '../api/client';
import { DocumentResponse } from '../api/types';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { ClassificationBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import { EmptyState } from '../components/common/EmptyState';
import { Search as SearchIcon, X, Info, FileText, Download } from 'lucide-react';

export const Search: React.FC = () => {
  const [tokenInput, setTokenInput] = useState('');
  const [tokens, setTokens] = useState<string[]>(['financial', 'report']);
  const [queryTrigger, setQueryTrigger] = useState<string[]>(['financial', 'report']);

  const queryString = queryTrigger.join(' ');

  const { data: results, isLoading, isFetching } = useQuery({
    queryKey: ['blind-search', queryString],
    queryFn: () => {
      if (!queryString) return [];
      return apiRequest<DocumentResponse[]>(`/search?q=${encodeURIComponent(queryString)}`);
    },
    enabled: queryTrigger.length > 0,
  });

  const handleAddToken = (e: React.KeyboardEvent | React.FormEvent) => {
    e.preventDefault();
    const clean = tokenInput.trim().toLowerCase();
    if (clean && !tokens.includes(clean)) {
      const updated = [...tokens, clean];
      setTokens(updated);
      setQueryTrigger(updated);
      setTokenInput('');
    }
  };

  const handleRemoveToken = (tokenToRemove: string) => {
    const updated = tokens.filter(t => t !== tokenToRemove);
    setTokens(updated);
    setQueryTrigger(updated);
  };

  const handleExecuteSearch = () => {
    if (tokenInput.trim()) {
      const clean = tokenInput.trim().toLowerCase();
      if (!tokens.includes(clean)) {
        const updated = [...tokens, clean];
        setTokens(updated);
        setQueryTrigger(updated);
        setTokenInput('');
        return;
      }
    }
    setQueryTrigger([...tokens]);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-extrabold font-fraunces text-brown tracking-tight">
          Blind-Index Search
        </h1>
        <p className="text-sm text-brown/70 mt-1">
          Cryptographically searches documents via HMAC tokens without revealing keywords to the database.
        </p>
      </div>

      {/* Security Note Banner */}
      <div className="p-4 rounded-xl bg-sand/30 border border-sand flex items-start gap-3 text-xs text-brown/80 leading-relaxed">
        <Info className="w-5 h-5 text-terracotta shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold text-brown">Zero-Knowledge Query Semantics: </span>
          Only <strong className="text-terracotta">exact keyword matches</strong> are supported (AND intersection across all active chips).
          Plaintext search terms are converted to HMAC-SHA256 digests in memory before querying the blind index.
        </div>
      </div>

      {/* Search Input Box & Keyword Chips */}
      <Card className="space-y-4">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <SearchIcon className="w-4 h-4 text-brown/40 absolute left-3.5 top-3" />
            <input
              type="text"
              value={tokenInput}
              onChange={(e) => setTokenInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleAddToken(e);
              }}
              placeholder="Type keyword and press Enter or click Add..."
              className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-sand bg-white/90 text-sm focus:outline-none focus:ring-2 focus:ring-terracotta"
            />
          </div>

          <div className="flex gap-2">
            <Button
              type="button"
              onClick={handleAddToken}
              variant="secondary"
              size="md"
              disabled={!tokenInput.trim()}
            >
              Add Token
            </Button>
            <Button
              type="button"
              onClick={handleExecuteSearch}
              variant="primary"
              size="md"
              isLoading={isFetching}
            >
              <SearchIcon className="w-4 h-4 mr-1.5" />
              Search
            </Button>
          </div>
        </div>

        {/* Active Token Chips */}
        <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-sand/50">
          <span className="text-xs font-semibold text-brown/60 uppercase font-mono mr-1">
            AND Intersection:
          </span>
          {tokens.map((token) => (
            <span
              key={token}
              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-terracotta/10 text-terracotta border border-terracotta/30 shadow-sm"
            >
              <span>{token}</span>
              <button
                type="button"
                onClick={() => handleRemoveToken(token)}
                className="hover:text-brick transition-colors"
                title="Remove token"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          ))}
          {tokens.length === 0 && (
            <span className="text-xs text-brown/50 italic">
              No active keyword chips. Add one above to filter.
            </span>
          )}
        </div>
      </Card>

      {/* Results Table */}
      <Card className="p-0 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-sand bg-sand/20 flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-brown font-mono">
            Search Results ({results?.length ?? 0})
          </span>
          <span className="text-xs text-brown/50 font-mono">
            Tenant Isolated
          </span>
        </div>

        {isLoading ? (
          <div className="p-6 space-y-4">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : results && results.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-sand text-xs font-semibold uppercase tracking-wider text-brown/70 font-mono">
                  <th className="py-3 px-4">Document Name</th>
                  <th className="py-3 px-4">Classification</th>
                  <th className="py-3 px-4">Version</th>
                  <th className="py-3 px-4">Created At</th>
                  <th className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-sand">
                {results.map((doc) => (
                  <tr key={doc.id} className="hover:bg-sand/10 transition-colors">
                    <td className="py-3.5 px-4 font-medium text-brown flex items-center gap-2.5">
                      <div className="w-8 h-8 rounded-lg bg-terracotta/10 text-terracotta flex items-center justify-center shrink-0">
                        <FileText className="w-4 h-4" />
                      </div>
                      <span className="truncate max-w-sm">{doc.name}</span>
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
                    <td className="py-3.5 px-4 text-right">
                      <Button
                        onClick={async () => {
                          const res = await apiRequest<{ contentBase64: string; name: string; contentType: string }>(
                            `/documents/${doc.id}/download`
                          );
                          const blob = new Blob([Uint8Array.from(atob(res.contentBase64), c => c.charCodeAt(0))], { type: res.contentType });
                          const link = document.createElement('a');
                          link.href = URL.createObjectURL(blob);
                          link.download = res.name;
                          link.click();
                        }}
                        variant="ghost"
                        size="sm"
                      >
                        <Download className="w-4 h-4 mr-1.5" />
                        Download
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={SearchIcon}
            title="No Matching Documents"
            description="No documents matched all specified keyword tokens in this tenant."
          />
        )}
      </Card>
    </div>
  );
};

