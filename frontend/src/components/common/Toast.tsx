import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import { CheckCircle2, XCircle, Info, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'info';

interface Toast {
  id: string;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  showToast: (opts: { type: ToastType; message: string }) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

/**
 * In-app toast notification system — positioned fixed bottom-right.
 * Uses the QUANTUM-LOCK color palette only.
 * Auto-dismisses after 4 seconds.
 */
export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timerRef = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    clearTimeout(timerRef.current[id]);
    delete timerRef.current[id];
  }, []);

  const showToast = useCallback(({ type, message }: { type: ToastType; message: string }) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setToasts((prev) => [...prev.slice(-4), { id, type, message }]); // max 5 toasts

    timerRef.current[id] = setTimeout(() => {
      dismiss(id);
    }, 4000);
  }, [dismiss]);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}

      {/* Toast Container */}
      <div
        className="fixed bottom-6 right-6 z-[100] flex flex-col gap-2.5 pointer-events-none"
        aria-live="polite"
        aria-label="Notifications"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`
              pointer-events-auto flex items-start gap-3 px-4 py-3 rounded-xl border shadow-lg
              text-sm font-medium max-w-sm animate-in slide-in-from-right-5 fade-in duration-200
              ${toast.type === 'success'
                ? 'bg-cream border-olive/30 text-olive'
                : toast.type === 'error'
                ? 'bg-cream border-brick/30 text-brick'
                : 'bg-cream border-terracotta/30 text-terracotta'}
            `}
            role="alert"
          >
            <div className="shrink-0 mt-0.5">
              {toast.type === 'success' && <CheckCircle2 className="w-4 h-4 text-olive" />}
              {toast.type === 'error' && <XCircle className="w-4 h-4 text-brick" />}
              {toast.type === 'info' && <Info className="w-4 h-4 text-terracotta" />}
            </div>

            <span className="flex-1 leading-snug text-brown/90">{toast.message}</span>

            <button
              onClick={() => dismiss(toast.id)}
              className="shrink-0 text-brown/40 hover:text-brown/80 transition-colors ml-1"
              aria-label="Dismiss notification"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export function useToast(): ToastContextType {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    // Graceful fallback outside provider — no-op toast
    return { showToast: () => {} };
  }
  return ctx;
}

