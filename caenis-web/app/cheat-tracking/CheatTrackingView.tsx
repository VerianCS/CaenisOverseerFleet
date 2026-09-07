'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { TacticalMap } from './components/TacticalMap';
import { AlertFeed } from './components/AlertFeed';
import { StatsHeader } from './components/StatsHeader';
import { DepthSounding } from './components/DepthSounding';
import { IncidentSearch } from './components/IncidentSearch';
import { MapMarker, ThreatAlert, OverviewStats } from './types/deck';
import { fetchOverviewStats, fetchMapEvents, fetchThreatAlerts } from './services/api';
import { Radio } from 'lucide-react';

export default function CheatTrackingView({ onBack }: { onBack: () => void }) {
  const [stats, setStats] = useState<OverviewStats | null>(null);
  const [markers, setMarkers] = useState<MapMarker[]>([]);
  const [alerts, setAlerts] = useState<ThreatAlert[]>([]);
  const [isLive, setIsLive] = useState<boolean>(true);
  const [ready, setReady] = useState(false);
  const [revealed, setRevealed] = useState(false);
  const [query, setQuery] = useState('');

  const refreshTelemetry = async () => {
    try {
      const [s, m, a] = await Promise.all([
        fetchOverviewStats(),
        fetchMapEvents('world', -2000, 2000, -2000, 2000, 1440),
        fetchThreatAlerts()
      ]);
      setStats(s);
      setMarkers(m);
      setAlerts(a);
    } catch (err) {
      console.error('Tactical link telemetry fetch error:', err);
    } finally {
      setReady(true);
    }
  };

  useEffect(() => {
    refreshTelemetry();
    if (!isLive) return;

    // Real-time polling interval: 2.5 seconds
    const interval = setInterval(refreshTelemetry, 2500);
    return () => clearInterval(interval);
  }, [isLive]);

  const filteredAlerts = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return alerts;
    return alerts.filter((alert) => {
      const breachLabel =
        alert.alertType === 'FAST_MINING_TEMPORAL_BREACH'
          ? 'temporal fracture fast break'
          : 'occlusion breach x-ray';
      return (
        alert.playerName.toLowerCase().includes(q) ||
        alert.severity.toLowerCase().includes(q) ||
        breachLabel.includes(q) ||
        alert.diagnosticData.toLowerCase().includes(q) ||
        alert.world.toLowerCase().includes(q) ||
        `${alert.x} ${alert.y} ${alert.z}`.includes(q)
      );
    });
  }, [alerts, query]);

  return (
    <>
      <DepthSounding ready={ready} onDone={() => setRevealed(true)} />

      <main
        className={`flex h-screen w-screen flex-col gap-4 overflow-hidden bg-abyss p-4 text-foam ${
          revealed ? 'animate-rise-fade' : 'opacity-0'
        }`}
      >
        {/* Top Banner */}
        <header className="flex items-center justify-between border-b border-depth-line pb-3">
          <div className="flex items-center gap-3">
            <button type="button" onClick={onBack} className="minecraft-button secondary !px-3 !py-2 !text-[9px]" aria-label="Return to CraftCtrl dashboard">← dashboard</button>
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-gentle-pulse rounded-full bg-tidewake" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-tidewake" />
            </span>
            <h1 className="font-display text-lg text-foam">
              Caenis Overseer
              <span className="ml-3 font-data text-sm font-normal tracking-normal text-current">
                tactical observation deck
              </span>
            </h1>
          </div>

          <button
            onClick={() => setIsLive(!isLive)}
            className={`flex items-center gap-1.5 rounded-sm border px-3 py-1.5 font-data text-xs transition-colors ${
              isLive
                ? 'border-tidewake-dim bg-tidewake-dim/30 text-tidewake'
                : 'border-depth-line bg-depth-raised text-current'
            }`}
          >
            <Radio size={12} className={isLive ? 'animate-gentle-pulse' : ''} />
            {isLive ? 'uplink active' : 'paused'}
          </button>
        </header>

        {/* Main Workspace: Sidebar (search + counters + feed) + Spatial Canvas */}
        <div className="grid min-h-0 flex-1 grid-cols-[380px_1fr] gap-4">
          <aside className="flex min-h-0 flex-col gap-3">
            <IncidentSearch
              value={query}
              onChange={setQuery}
              resultCount={filteredAlerts.length}
              totalCount={alerts.length}
            />
            <StatsHeader stats={stats} />
            <div className="min-h-0 flex-1">
              <AlertFeed
                alerts={filteredAlerts}
                emptyMessage={
                  query.trim()
                    ? 'no incidents match this search'
                    : 'no diagnostic anomalies logged — operational state nominal'
                }
              />
            </div>
          </aside>

          <div className="h-full min-h-0">
            <TacticalMap markers={markers} />
          </div>
        </div>
      </main>
    </>
  );
}
