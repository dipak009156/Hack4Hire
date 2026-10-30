import React from 'react';
import { DocumentClassification, DocumentState } from '../../api/types';

interface StateBadgeProps {
  state: DocumentState | string;
  className?: string;
}

export const StateBadge: React.FC<StateBadgeProps> = ({ state, className = '' }) => {
  switch (state) {
    case 'COMMITTED':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#6B8F4E]/15 text-[#6B8F4E] border border-[#6B8F4E]/30 ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-[#6B8F4E] mr-1.5"></span>
          COMMITTED
        </span>
      );
    case 'PENDING':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#E9A23B]/15 text-[#E9A23B] border border-[#E9A23B]/30 ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-[#E9A23B] mr-1.5 animate-pulse"></span>
          PENDING
        </span>
      );
    case 'OBJECT_STORED':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#E6D5BF]/40 text-[#3B2A20] border border-[#E6D5BF] ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-sand mr-1.5"></span>
          OBJECT_STORED
        </span>
      );
    case 'RECOVERING':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border-2 border-[#E9A23B] text-[#E9A23B] bg-transparent ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-[#E9A23B] mr-1.5"></span>
          RECOVERING
        </span>
      );
    case 'FAILED':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#A63D2F]/15 text-[#A63D2F] border border-[#A63D2F]/30 ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-[#A63D2F] mr-1.5"></span>
          FAILED
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-sand/30 text-brown border border-sand ${className}`}>
          {state}
        </span>
      );
  }
};

interface ClassificationBadgeProps {
  classification: DocumentClassification | string;
  className?: string;
}

export const ClassificationBadge: React.FC<ClassificationBadgeProps> = ({ classification, className = '' }) => {
  switch (classification) {
    case 'RESTRICTED':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-brick/10 text-brick border border-brick/20 ${className}`}>
          RESTRICTED
        </span>
      );
    case 'CONFIDENTIAL':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber/15 text-brown border border-amber/30 ${className}`}>
          CONFIDENTIAL
        </span>
      );
    case 'INTERNAL':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-terracotta/10 text-terracotta border border-terracotta/20 ${className}`}>
          INTERNAL
        </span>
      );
    case 'PUBLIC':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-olive/15 text-olive border border-olive/30 ${className}`}>
          PUBLIC
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-sand/30 text-brown ${className}`}>
          {classification}
        </span>
      );
  }
};

