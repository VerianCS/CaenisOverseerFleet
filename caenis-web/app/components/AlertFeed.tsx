'use client';

import React, { useEffect, useRef, useState } from 'react';
import { ThreatAlert } from '../types/deck';
import { AlertTriangle, ShieldAlert, Clock, Zap } from 'lucide-react';

interface AlertFeedProps {
  alerts: ThreatAlert[];
  emptyMessage?: string;
}

const severityStyle = (severity: string) => {
  switch (severity) {
    case 'CRITICAL':
      return 'bg-fracture/10 text-fracture border-fracture/40';
    case 'HIGH':
      return 'bg-adamant/10 text-adamant border-adamant/40';
    case 'MEDIUM':
      return 'bg-adamant/5 text-adamant/70 border-adamant/20';
    default:
      return 'bg-depth-raised text-current border-depth-line';
  }
};

export const AlertFeed: React.FC<AlertFeedProps> = ({
  alerts,
  emptyMessage = 'no diagnostic anomalies logged — operational state nominal',
}) => {
  const seenIds = useRef<Set<number>>(new Set());
  const [freshId, setFreshId] = useState<number | null>(null);

  useEffect(() => {
    const newest = alerts[0];
    if (!newest) return;
    const isFirstLoad = seenIds.current.size === 0;
    if (!seenIds.current.has(newest.id)) {
      if (!isFirstLoad) setFreshId(newest.id);
      alerts.forEach((a) => seenIds.current.add(a.id));
    }
  }, [alerts]);

  return (
    <div className="flex h-full flex-col overflow-hidden rounded-sm border border-depth-line bg-depth">
      <div className="flex items-center justify-between border-b border-depth-line bg-depth-raised/60 p-3">
        <div className="flex items-center gap-2">
          <ShieldAlert size={15} className="text-fracture" />
          <h2 className="font-display text-sm text-foam">Threat Surveillance</h2>
        </div>
        <span className="font-data text-[11px] text-current">{alerts.length} records</span>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto p-2">
        {alerts.length === 0 ? (
          <div className="py-12 text-center font-data text-xs text-current">{emptyMessage}</div>
        ) : (
          alerts.map((alert) => (
            <div
              key={alert.id}
              className={`flex flex-col gap-1.5 rounded-sm border border-depth-line bg-depth-raised/40 p-2.5 transition-colors hover:bg-depth-raised ${
                alert.id === freshId ? 'animate-rise-fade' : ''
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-data text-xs font-semibold text-foam">{alert.playerName}</span>
                <span className={`rounded-sm border px-1.5 py-0.5 font-data text-[10px] ${severityStyle(alert.severity)}`}>
                  {alert.severity.toLowerCase()}
                </span>
              </div>

              <div className="flex items-center gap-1.5 text-xs text-foam/80">
                {alert.alertType === 'FAST_MINING_TEMPORAL_BREACH' ? (
                  <Zap size={13} className="flex-shrink-0 text-adamant" />
                ) : (
                  <AlertTriangle size={13} className="flex-shrink-0 text-fracture" />
                )}
                <span>
                  {alert.alertType === 'FAST_MINING_TEMPORAL_BREACH'
                    ? 'temporal fracture — fast break'
                    : 'occlusion breach — x-ray'}
                </span>
              </div>

              <div className="rounded-sm border border-depth-line/60 bg-abyss/60 p-1.5 font-data text-[11px] text-current">
                {alert.diagnosticData}
              </div>

              <div className="flex items-center justify-between pt-1 font-data text-[10px] text-current">
                <span>
                  [{alert.x}, {alert.y}, {alert.z}]
                </span>
                <span className="flex items-center gap-1">
                  <Clock size={10} />
                  {new Date(alert.createdAt).toLocaleTimeString()}
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
