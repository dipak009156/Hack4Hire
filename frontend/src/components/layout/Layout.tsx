import React, { useState, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { getLastCorrelationId, onCorrelationIdChange } from '../../api/client';
import { Hash } from 'lucide-react';

export const Layout: React.FC = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [correlationId, setCorrelationId] = useState<string | null>(getLastCorrelationId());

  useEffect(() => {
    const unsubscribe = onCorrelationIdChange((id) => {
      setCorrelationId(id);
    });
    return () => {
      unsubscribe();
    };
  }, []);

  return (
    <div className="flex h-screen overflow-hidden bg-cream text-brown">
      {/* Desktop Sidebar */}
      <div className="hidden lg:block shrink-0">
        <Sidebar />
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="fixed inset-0 z-50 lg:hidden flex">
          <div
            className="fixed inset-0 bg-espresso/60 backdrop-blur-sm transition-opacity"
            onClick={() => setMobileMenuOpen(false)}
          />
          <div className="relative z-10 animate-in slide-in-from-left duration-200">
            <Sidebar onCloseMobile={() => setMobileMenuOpen(false)} />
          </div>
        </div>
      )}

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <TopBar onOpenMobileMenu={() => setMobileMenuOpen(true)} />

        <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          <div className="max-w-7xl mx-auto space-y-6">
            <Outlet />
          </div>
        </main>

        {/* Footer with Correlation ID chip */}
        <footer className="h-9 border-t border-sand bg-cream/70 backdrop-blur-sm px-6 flex items-center justify-between text-xs text-brown/60 select-none">
          <div className="flex items-center gap-1.5 font-mono">
            <span>QUANTUM-LOCK v1.0.0</span>
          </div>

          {correlationId ? (
            <div 
              className="flex items-center gap-1 bg-white/80 border border-sand px-2 py-0.5 rounded-full font-mono text-[11px] text-brown shadow-sm"
              title="Correlation ID of the most recent API request"
            >
              <Hash className="w-3 h-3 text-terracotta" />
              <span className="text-brown/50">Corr-ID:</span>
              <span className="font-semibold">{correlationId}</span>
            </div>
          ) : (
            <span className="text-[11px] text-brown/40">Ready</span>
          )}
        </footer>
      </div>
    </div>
  );
};
