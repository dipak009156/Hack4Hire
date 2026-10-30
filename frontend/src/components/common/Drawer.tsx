import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface DrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const Drawer: React.FC<DrawerProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div
        className="fixed inset-0 bg-espresso/40 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      <div className="relative w-full max-w-xl bg-cream border-l border-sand shadow-warm-lg h-full z-10 flex flex-col animate-in slide-in-from-right duration-200">
        <div className="flex items-center justify-between p-6 border-b border-sand bg-white/50">
          <div>
            <h3 className="text-xl font-bold font-fraunces text-brown">{title}</h3>
            {subtitle && <p className="text-sm text-brown/70 mt-0.5">{subtitle}</p>}
          </div>
          <button
            onClick={onClose}
            className="text-brown/50 hover:text-brown p-1.5 rounded-lg hover:bg-sand/30 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-6 space-y-6">{children}</div>
      </div>
    </div>
  );
};

