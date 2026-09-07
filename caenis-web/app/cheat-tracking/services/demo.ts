import { MapMarker, ThreatAlert, OverviewStats } from '../types/deck';

// Fallback telemetry used when the tactical link backend is unreachable or
// has no recent mining events, so the spatial canvas always has nodes to render.
export const DEMO_MARKERS: MapMarker[] = [
  {
    id: 9001,
    playerName: 'Alex',
    blockType: 'DIAMOND_ORE',
    x: 14,
    y: 12,
    z: -22,
    isExposed: true,
    timestamp: '2026-09-04T02:12:00Z',
  },
  {
    id: 9002,
    playerName: 'Steve',
    blockType: 'DEEPSLATE_DIAMOND_ORE',
    x: -36,
    y: -8,
    z: 18,
    isExposed: false,
    timestamp: '2026-09-04T02:10:00Z',
  },
  {
    id: 9003,
    playerName: 'Notchling',
    blockType: 'STONE',
    x: 57,
    y: 4,
    z: 43,
    isExposed: true,
    timestamp: '2026-09-04T02:05:00Z',
  },
  {
    id: 9004,
    playerName: 'CreeperKid',
    blockType: 'NETHERITE_DEBRIS',
    x: -61,
    y: 18,
    z: -19,
    isExposed: false,
    timestamp: '2026-09-04T02:01:00Z',
  },
  {
    id: 9005,
    playerName: 'Redstone_Red',
    blockType: 'ANCIENT_DEBRIS',
    x: 25,
    y: -14,
    z: 60,
    isExposed: false,
    timestamp: '2026-09-04T01:58:00Z',
  },
  {
    id: 9006,
    playerName: 'PixelPanda',
    blockType: 'COAL_ORE',
    x: -12,
    y: 6,
    z: -41,
    isExposed: true,
    timestamp: '2026-09-04T01:55:00Z',
  },
  {
    id: 9007,
    playerName: 'Alex',
    blockType: 'GOLD_ORE',
    x: 78,
    y: 2,
    z: -64,
    isExposed: true,
    timestamp: '2026-09-04T01:50:00Z',
  },
  {
    id: 9008,
    playerName: 'Steve',
    blockType: 'STONE',
    x: -84,
    y: 9,
    z: 72,
    isExposed: true,
    timestamp: '2026-09-04T01:44:00Z',
  },
];

export const DEMO_ALERTS: ThreatAlert[] = [];

export const DEMO_STATS: OverviewStats = {
  totalEventsLogged: 1280,
  activeThreatsCount: 2,
  criticalThreatsCount: 1,
};