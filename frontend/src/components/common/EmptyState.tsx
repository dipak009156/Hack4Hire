import React from 'react';
import { LucideIcon } from 'lucide-react';
import { Button } from './Button';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
}) => {
  return (
    <div className="flex flex-col items-center justify-center p-12 text-center border-2 border-dashed border-sand rounded-xl bg-white/40 my-4">
      <div className="w-14 h-14 rounded-full bg-sand/30 flex items-center justify-center text-terracotta mb-4">
        <Icon className="w-7 h-7" />
      </div>
      <h4 className="text-lg font-bold font-fraunces text-brown">{title}</h4>
      <p className="text-sm text-brown/70 max-w-sm mt-1 mb-6">{description}</p>
      {actionLabel && onAction && (
        <Button onClick={onAction} variant="primary" size="sm">
          {actionLabel}
        </Button>
      )}
    </div>
  );
};

