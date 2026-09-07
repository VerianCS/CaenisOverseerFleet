'use client';

import React, { useEffect, useRef, useState } from 'react';
import { OverviewStats } from '../types/deck';
import { Waves, ShieldAlert, Gauge } from 'lucide-react';

type Tone = 'tidewake' | 'adamant' | 'fracture';

const toneText: Record<Tone, string> = {
  tidewake: 'text-tidewake',
  adamant: 'text-adamant',
  fracture: 'text-fracture',
};

const Readout: React.FC<{
  label: string;
  value: number | null;
  tone: Tone;
  icon: React.ReactNode;
}> = ({ label, value, tone, icon }) => {
  const [tick, setTick] = useState(0);
  const prev = useRef<number | null>(value);

  useEffect(() => {
    if (value !== null && value !== prev.current) {
      setTick((t) => t + 1);
      prev.current = value;
    }
  }, [value]);

  return (
    <div className="flex items-center justify-between rounded-sm border border-depth-line bg-depth p-3.5">
      <div>
        <div className="font-data text-[10px] uppercase tracking-[0.15em] text-current">{label}</div>
        <div key={tick} className={`font-data text-2xl font-semibold animate-tick-in ${toneText[tone]}`}>
          {value !== null ? value.toLocaleString() : '—'}
        </div>
      </div>
      <div className={`${toneText[tone]} opacity-70`}>{icon}</div>
    </div>
  );
};

export const StatsHeader: React.FC<{ stats: OverviewStats | null }> = ({ stats }) => {
  return (
    <div className="grid grid-cols-1 gap-2">
      <Readout
        label="telemetry ingested"
        value={stats ? stats.totalEventsLogged : null}
        tone="tidewake"
        icon={<Gauge size={22} />}
      />
      <Readout
        label="heuristic infractions"
        value={stats ? stats.activeThreatsCount : null}
        tone="adamant"
        icon={<Waves size={22} />}
      />
      <Readout
        label="critical interventions"
        value={stats ? stats.criticalThreatsCount : null}
        tone="fracture"
        icon={<ShieldAlert size={22} />}
      />
    </div>
  );
};
