'use client';

import React from 'react';
import { Search, X } from 'lucide-react';

interface IncidentSearchProps {
  value: string;
  onChange: (value: string) => void;
  resultCount: number;
  totalCount: number;
}

export const IncidentSearch: React.FC<IncidentSearchProps> = ({
  value,
  onChange,
  resultCount,
  totalCount,
}) => {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2 rounded-sm border border-depth-line bg-depth px-3 py-2 transition-colors focus-within:border-tidewake-dim">
        <Search size={14} className="flex-shrink-0 text-current" />
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="search incidents — player, breach type, coords"
          className="w-full bg-transparent font-data text-xs text-foam outline-none placeholder:text-current"
        />
        {value && (
          <button
            onClick={() => onChange('')}
            className="flex-shrink-0 text-current transition-colors hover:text-fracture"
            title="Clear search"
          >
            <X size={13} />
          </button>
        )}
      </div>
      {value && (
        <span className="pl-1 font-data text-[10px] text-current">
          {resultCount} of {totalCount} record{totalCount === 1 ? '' : 's'} match
        </span>
      )}
    </div>
  );
};
