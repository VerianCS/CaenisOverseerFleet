import { MapMarker, ThreatAlert, OverviewStats } from "../types/deck";

const BACKEND_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1/deck';

export const fetchOverviewStats = async (): Promise<OverviewStats> => {
  const res = await fetch(`${BACKEND_URL}/stats`, { cache: 'no-store' });
  if (!res.ok) throw new Error('Failed to fetch tactical overview stats');
  return res.json();
};

export const fetchMapEvents = async (
  world = 'world',
  minX = -1000,
  maxX = 1000,
  minZ = -1000,
  maxZ = 1000,
  minutesBack = 120
): Promise<MapMarker[]> => {
  const params = new URLSearchParams({
    world,
    minX: minX.toString(),
    maxX: maxX.toString(),
    minZ: minZ.toString(),
    maxZ: maxZ.toString(),
    minutesBack: minutesBack.toString(),
    limit: '2000'
  });
  const res = await fetch(`${BACKEND_URL}/map?${params}`, { cache: 'no-store' });
  if (!res.ok) throw new Error('Failed to fetch spatial telemetry');
  return res.json();
};

export const fetchThreatAlerts = async (): Promise<ThreatAlert[]> => {
  const res = await fetch(`${BACKEND_URL}/alerts?limit=50`, { cache: 'no-store' });
  if (!res.ok) throw new Error('Failed to fetch threat alerts');
  return res.json();
};