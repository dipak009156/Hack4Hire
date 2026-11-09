import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '4xl' | '5xl';
}

export const Dialog: React.FC<DialogProps> = ({
  isOpen,
  onClose,
  title,
  description,
  children,
  maxWidth = 'md',
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

  const maxWidthStyles = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-2xl',
    '2xl': 'max-w-2xl',
    '4xl': 'max-w-4xl',
    '5xl': 'max-w-5xl',
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div 
        className="fixed inset-0 bg-espresso/50 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      <div className={`relative bg-cream border border-sand rounded-xl shadow-warm-lg w-full ${maxWidthStyles[maxWidth]} p-6 z-10 animate-in fade-in zoom-in-95 duration-150`}>
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-xl font-bold font-fraunces text-brown">{title}</h3>
            {description && (
              <p className="text-sm text-brown/70 mt-1">{description}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-brown/50 hover:text-brown p-1 rounded-lg hover:bg-sand/30 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="mt-2">{children}</div>
      </div>
    </div>
  );
};

