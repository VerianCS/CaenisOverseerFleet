'use client';

import React, { useEffect, useState } from 'react';

interface DepthSoundingProps {
  ready: boolean;
  onDone?: () => void;
}

const GRID_COLS = 16;
const GRID_ROWS = 9;

// Original guardian sigil — a trident held within a sounding-ring.
// Not a reproduction of any existing character artwork.
const RuneGlyph: React.FC = () => (
  <svg viewBox="0 0 100 100" className="relative z-10 h-16 w-16">
    <g fill="none" stroke="var(--adamant)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path
        d="M50 12 L50 88 M32 12 L32 30 Q32 40 50 40 Q68 40 68 30 L68 12"
        style={{
          strokeDasharray: 210,
          strokeDashoffset: 210,
          animation: 'rune-draw 1.4s ease-out 0.35s forwards',
        }}
      />
      <circle
        cx="50"
        cy="60"
        r="15"
        style={{
          strokeDasharray: 95,
          strokeDashoffset: 95,
          animation: 'rune-draw 0.9s ease-out 1.15s forwards',
        }}
      />
    </g>
  </svg>
);

export const DepthSounding: React.FC<DepthSoundingProps> = ({ ready, onDone }) => {
  const [exiting, setExiting] = useState(false);
  const [mounted, setMounted] = useState(true);

  // Server renders no chunk cells at all; the client's first render matches
  // that (state starts null), then this effect fills them in post-hydration.
  // Generating them with Math.random() during render — even inside useMemo —
  // produces different values on the server pass vs. the client pass, which
  // is exactly what causes a hydration mismatch.
  const [cellDelays, setCellDelays] = useState<number[] | null>(null);

  useEffect(() => {
    setCellDelays(Array.from({ length: GRID_COLS * GRID_ROWS }, () => Math.random() * 1.6));
  }, []);

  useEffect(() => {
    if (!ready) return;
    const settle = setTimeout(() => setExiting(true), 500);
    return () => clearTimeout(settle);
  }, [ready]);

  useEffect(() => {
    if (!exiting) return;
    const unmount = setTimeout(() => {
      setMounted(false);
      onDone?.();
    }, 650);
    return () => clearTimeout(unmount);
  }, [exiting, onDone]);

  if (!mounted) return null;

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-abyss transition-all duration-700 ease-[cubic-bezier(0.22,1,0.36,1)] ${
        exiting ? 'pointer-events-none scale-105 opacity-0' : 'scale-100 opacity-100'
      }`}
      role="status"
      aria-live="polite"
      aria-label="Establishing tactical uplink"
    >
      {/* World-generation chunk grid (client-only, see note above) */}
      {cellDelays && (
        <div
          className="absolute inset-0 grid opacity-60"
          style={{
            gridTemplateColumns: `repeat(${GRID_COLS}, 1fr)`,
            gridTemplateRows: `repeat(${GRID_ROWS}, 1fr)`,
          }}
        >
          {cellDelays.map((delay, i) => (
            <div
              key={i}
              className="border border-transparent"
              style={{ animation: `chunk-resolve 2.8s ease-out ${delay}s infinite` }}
            />
          ))}
        </div>
      )}
      {/* Sounding rings + rune */}
      <div className="relative flex h-40 w-40 items-center justify-center">
        <span
          className="absolute inset-0 rounded-full border border-tidewake/40"
          style={{ animation: 'sonar-ping 2.4s ease-out infinite' }}
        />
        <span
          className="absolute inset-0 rounded-full border border-tidewake/40"
          style={{ animation: 'sonar-ping 2.4s ease-out 0.8s infinite' }}
        />
        <span
          className="absolute inset-0 rounded-full border border-tidewake/40"
          style={{ animation: 'sonar-ping 2.4s ease-out 1.6s infinite' }}
        />
        <RuneGlyph />
      </div>

      <p className="mt-8 font-data text-[11px] tracking-[0.2em] text-current animate-gentle-pulse">
        calibrating depth sounding
      </p>
    </div>
  );
};
