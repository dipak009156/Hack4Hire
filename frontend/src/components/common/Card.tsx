import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  hoverLift?: boolean;
}

export const Card: React.FC<CardProps> = ({
  children,
  hoverLift = false,
  className = '',
  ...props
}) => {
  return (
    <div
      className={`bg-white/90 backdrop-blur-sm border border-sand rounded-xl p-5 shadow-warm transition-all duration-200 ${
        hoverLift ? 'hover:-translate-y-0.5 hover:shadow-warm-lg' : ''
      } ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};

