export type AlertType = 'FAST_MINING_TEMPORAL_BREACH' | 'TOPOLOGICAL_OCCLUSION_XRAY'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export interface MapMarker {
  id: number; playerName: string; playerId?: string; blockType: string; x: number; y: number; z: number
  isExposed: boolean; timestamp: string; count?: number
}
export interface ThreatAlert {
  id: number; instanceId?: string; playerId: string; playerName: string; alertType: AlertType; severity: Severity
  diagnosticData: string; world: string; x: number; y: number; z: number; createdAt: string
}
export interface OverviewStats {
  totalEventsLogged: number; activeThreatsCount: number; criticalThreatsCount: number
  eventsPerSecond?: number; automatedInterventions?: number
}
