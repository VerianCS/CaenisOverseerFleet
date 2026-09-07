export type Role = 'SUPERADMIN' | 'MODERATOR' | 'ANALYST'
export interface Account { id: string; username: string; role: Role }
export interface Player { id: string; name: string; ping: number; world: string }
export interface AgentConfiguration { enabled: boolean; debugSampling: boolean; batchSize: number; flushIntervalMs: number; jitterMs: number }
export interface Health {
  tps: number[]; mspt: number; heapUsed: number; heapCommitted: number; heapMax: number; loadedChunks: number; entities: number
  players: Player[]; worlds: string[]; queueDepth: number; droppedEvents: number; spoolBytes: number; sampledAt: number; version: string
}
export interface Instance {
  id: string; name: string; clusterGroup: string; region: string; enabled: boolean
  status: 'ONLINE' | 'UNREACHABLE' | 'AWAITING_AGENT' | 'DISABLED'; lastSeen: string | null
  health: Health | null; rconConfigured: boolean; lifecycleConfigured: boolean
  configuration: AgentConfiguration; automationEnabled: boolean; automationAction: string
  rconHost: string | null; rconPort: number; vaultPath: string | null; managerUrl: string | null
}
export interface Sample { time: string; tps: number; mspt: number; heapUsed: number; heapMax: number; players: number }
export interface CommandEvent { id: string; status: string; output: string }
export interface AuditEvent { id: number; correlationId: string; actor: string; instanceId: string | null; action: string; command: string; outcome: string; detail: string; createdAt: string }
export interface Scope { id: string; name: string; worlds: string[] }
