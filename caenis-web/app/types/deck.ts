export type AlertType = 'FAST_MINING_TEMPORAL_BREACH' | 'TOPOLOGICAL_OCCLUSION_XRAY';
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface MapMarker {
  id: number;
  playerName: String;
  blockType: string;
  x: number;
  y: number;
  z: number;
  isExposed: boolean;
  timestamp: string;
}

export interface ThreatAlert {
  id: number;
  playerId: string;
  playerName: string;
  alertType: AlertType;
  severity: Severity;
  diagnosticData: string;
  world: string;
  x: number;
  y: number;
  z: number;
  createdAt: string;
}

export interface OverviewStats {
  totalEventsLogged: number;
  activeThreatsCount: number;
  criticalThreatsCount: number;
}