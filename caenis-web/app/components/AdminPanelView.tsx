'use client'

import React, { useState } from 'react'
import {
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  Command,
  Compass,
  Crown,
  Filter,
  Gavel,
  Lock,
  RefreshCw,
  Search,
  Send,
  Server,
  Shield,
  ShieldAlert,
  Terminal,
  UserCheck,
  UserMinus,
  UserX,
  Volume2,
  VolumeX,
  Zap,
} from 'lucide-react'

export interface AdminPlayer {
  id: string
  name: string
  uuid: string
  role: 'Owner' | 'Admin' | 'Moderator' | 'Member'
  color: string
  ping: number
  initials: string
  gamemode: 'Survival' | 'Creative' | 'Spectator'
  dimension: 'Overworld' | 'The Nether' | 'The End'
  coords: string
  isBanned: boolean
  isMuted: boolean
  health: number // 0-20
}

const INITIAL_PLAYERS: AdminPlayer[] = [
  {
    id: 'p1',
    name: 'Alex',
    uuid: 'c06f490c-3abb-4341',
    role: 'Owner',
    color: 'bg-emerald-500',
    ping: 42,
    initials: 'AL',
    gamemode: 'Survival',
    dimension: 'Overworld',
    coords: 'X: -142, Y: 68, Z: 320',
    isBanned: false,
    isMuted: false,
    health: 20,
  },
  {
    id: 'p2',
    name: 'Steve',
    uuid: '853c8090-2bb2-411a',
    role: 'Admin',
    color: 'bg-sky-500',
    ping: 67,
    initials: 'ST',
    gamemode: 'Creative',
    dimension: 'Overworld',
    coords: 'X: 12, Y: 72, Z: -55',
    isBanned: false,
    isMuted: false,
    health: 20,
  },
  {
    id: 'p3',
    name: 'Notchling',
    uuid: '11ae233d-55c9-4b82',
    role: 'Member',
    color: 'bg-amber-500',
    ping: 91,
    initials: 'NO',
    gamemode: 'Survival',
    dimension: 'The Nether',
    coords: 'X: 380, Y: 54, Z: 120',
    isBanned: false,
    isMuted: false,
    health: 16,
  },
  {
    id: 'p4',
    name: 'CreeperKid',
    uuid: '99bf4321-12ec-4991',
    role: 'Member',
    color: 'bg-lime-500',
    ping: 54,
    initials: 'CK',
    gamemode: 'Survival',
    dimension: 'Overworld',
    coords: 'X: -604, Y: 64, Z: 88',
    isBanned: false,
    isMuted: true,
    health: 12,
  },
  {
    id: 'p5',
    name: 'Redstone_Red',
    uuid: '74ad9932-bb88-4221',
    role: 'Moderator',
    color: 'bg-red-500',
    ping: 38,
    initials: 'RR',
    gamemode: 'Survival',
    dimension: 'Overworld',
    coords: 'X: 45, Y: 12, Z: -210',
    isBanned: false,
    isMuted: false,
    health: 20,
  },
  {
    id: 'p6',
    name: 'PixelPanda',
    uuid: '33ef6712-4aa8-4339',
    role: 'Member',
    color: 'bg-violet-500',
    ping: 73,
    initials: 'PP',
    gamemode: 'Survival',
    dimension: 'The End',
    coords: 'X: 100, Y: 49, Z: 0',
    isBanned: false,
    isMuted: false,
    health: 18,
  },
]

export default function AdminPanelView({ onBack }: { onBack: () => void }) {
  const [players, setPlayers] = useState<AdminPlayer[]>(INITIAL_PLAYERS)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<'all' | 'staff' | 'members'>('all')
  const [consoleInput, setConsoleInput] = useState('')
  const [actionLog, setActionLog] = useState<string[]>([
    '[13:28:40] [Server/INFO]: Caenis Overseer Panel initialized.',
    '[13:29:12] [Admin/INFO]: Loaded 6 player permission profiles.',
  ])

  const logAction = (text: string) => {
    const time = new Date().toTimeString().split(' ')[0]
    setActionLog((prev) => [`[${time}] [Overseer]: ${text}`, ...prev.slice(0, 30)])
  }

  // Admin Actions
  const handleKick = (player: AdminPlayer) => {
    setPlayers((prev) => prev.filter((p) => p.id !== player.id))
    logAction(`Player "${player.name}" was kicked by administrator.`)
  }

  const handleToggleBan = (player: AdminPlayer) => {
    setPlayers((prev) =>
      prev.map((p) =>
        p.id === player.id ? { ...p, isBanned: !p.isBanned } : p
      )
    )
    logAction(
      `Player "${player.name}" was ${player.isBanned ? 'unbanned' : 'banned'}.`
    )
  }

  const handleToggleMute = (player: AdminPlayer) => {
    setPlayers((prev) =>
      prev.map((p) =>
        p.id === player.id ? { ...p, isMuted: !p.isMuted } : p
      )
    )
    logAction(
      `Player "${player.name}" was ${player.isMuted ? 'unmuted' : 'muted'}.`
    )
  }

  const handleCycleGamemode = (player: AdminPlayer) => {
    const modes: AdminPlayer['gamemode'][] = ['Survival', 'Creative', 'Spectator']
    const nextMode = modes[(modes.indexOf(player.gamemode) + 1) % modes.length]
    setPlayers((prev) =>
      prev.map((p) => (p.id === player.id ? { ...p, gamemode: nextMode } : p))
    )
    logAction(`Updated gamemode for "${player.name}" to ${nextMode}.`)
  }

  const handleRunCommand = (e: React.FormEvent) => {
    e.preventDefault()
    if (!consoleInput.trim()) return
    logAction(`Executed console command: /${consoleInput.replace(/^\//, '')}`)
    setConsoleInput('')
  }

  const filteredPlayers = players.filter((player) => {
    const matchesSearch =
      player.name.toLowerCase().includes(search.toLowerCase()) ||
      player.uuid.includes(search)
    if (!matchesSearch) return false
    if (roleFilter === 'staff') return ['Owner', 'Admin', 'Moderator'].includes(player.role)
    if (roleFilter === 'members') return player.role === 'Member'
    return true
  })

  return (
    <main className="admin-page-shell relative z-20 min-h-screen bg-[#0d131d] text-slate-100 p-4 md:p-8">
      {/* Top Bar */}
      <div className="max-w-7xl mx-auto mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="flex items-center gap-2 px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-md text-sm font-semibold transition"
          >
            <ArrowLeft size={16} /> Back to Dashboard
          </button>
          <div>
            <h1 className="text-xl font-bold tracking-wide flex items-center gap-2">
              <Shield className="text-blue-400" size={20} />
              OVERSEER ADMINISTRATION
            </h1>
            <p className="text-xs text-slate-400">Server Player & Permission Operations</p>
          </div>
        </div>

        {/* Server State Pills */}
        <div className="flex items-center gap-2 text-xs">
          <span className="px-2.5 py-1 bg-emerald-950 border border-emerald-700 text-emerald-300 rounded font-mono">
            TPS: 20.0
          </span>
          <span className="px-2.5 py-1 bg-slate-800 border border-slate-700 text-slate-300 rounded font-mono">
            RAM: 3.2 / 8.0 GB
          </span>
          <span className="px-2.5 py-1 bg-blue-950 border border-blue-700 text-blue-300 rounded font-mono">
            ONLINE: {players.filter((p) => !p.isBanned).length}
          </span>
        </div>
      </div>

      <div className="max-w-7xl mx-auto grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Columns: Player Roster & Management */}
        <div className="lg:col-span-2 space-y-4">
          {/* Controls Bar */}
          <div className="bg-[#161f2e] border border-slate-800 rounded-lg p-3 flex flex-wrap items-center justify-between gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="absolute left-3 top-2.5 text-slate-400" size={16} />
              <input
                type="text"
                placeholder="Search by player name or UUID..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-[#0d1420] border border-slate-700 rounded pl-9 pr-3 py-1.5 text-sm focus:outline-none focus:border-blue-500"
              />
            </div>

            <div className="flex items-center gap-1 bg-[#0d1420] p-1 rounded border border-slate-700 text-xs">
              <button
                onClick={() => setRoleFilter('all')}
                className={`px-3 py-1 rounded font-medium ${
                  roleFilter === 'all' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                All ({players.length})
              </button>
              <button
                onClick={() => setRoleFilter('staff')}
                className={`px-3 py-1 rounded font-medium ${
                  roleFilter === 'staff' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                Staff
              </button>
              <button
                onClick={() => setRoleFilter('members')}
                className={`px-3 py-1 rounded font-medium ${
                  roleFilter === 'members' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                }`}
              >
                Members
              </button>
            </div>
          </div>

          {/* Players Table */}
          <div className="bg-[#161f2e] border border-slate-800 rounded-lg overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-[#101722] text-xs text-slate-400 uppercase border-b border-slate-800">
                  <tr>
                    <th className="px-4 py-3">Player</th>
                    <th className="px-4 py-3">Role</th>
                    <th className="px-4 py-3">Gamemode</th>
                    <th className="px-4 py-3">Location</th>
                    <th className="px-4 py-3 text-right">Admin Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {filteredPlayers.map((player) => (
                    <tr
                      key={player.id}
                      className={`hover:bg-[#1a2536] transition ${
                        player.isBanned ? 'opacity-40 bg-red-950/20' : ''
                      }`}
                    >
                      {/* Name & Initials */}
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <span
                            className={`w-8 h-8 flex items-center justify-center font-bold text-xs rounded ${player.color} text-white shadow`}
                          >
                            {player.initials}
                          </span>
                          <div>
                            <div className="font-semibold flex items-center gap-1.5">
                              {player.name}
                              {player.role === 'Owner' && <Crown size={13} className="text-amber-400" />}
                              {player.isMuted && <VolumeX size={13} className="text-orange-400" />}
                            </div>
                            <p className="text-[11px] font-mono text-slate-400">{player.uuid}</p>
                          </div>
                        </div>
                      </td>

                      {/* Role Badge */}
                      <td className="px-4 py-3">
                        <span
                          className={`text-[11px] px-2 py-0.5 rounded font-medium border ${
                            player.role === 'Owner'
                              ? 'bg-amber-950 border-amber-600 text-amber-300'
                              : player.role === 'Admin'
                              ? 'bg-red-950 border-red-600 text-red-300'
                              : player.role === 'Moderator'
                              ? 'bg-blue-950 border-blue-600 text-blue-300'
                              : 'bg-slate-800 border-slate-700 text-slate-300'
                          }`}
                        >
                          {player.role}
                        </span>
                      </td>

                      {/* Gamemode Switcher */}
                      <td className="px-4 py-3">
                        <button
                          onClick={() => handleCycleGamemode(player)}
                          title="Click to cycle gamemode"
                          className="px-2 py-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-xs flex items-center gap-1 transition"
                        >
                          <Zap size={12} className="text-yellow-400" />
                          {player.gamemode}
                        </button>
                      </td>

                      {/* Dimension & Coords */}
                      <td className="px-4 py-3 text-xs">
                        <span className="text-slate-300 font-medium">{player.dimension}</span>
                        <p className="font-mono text-[11px] text-slate-500">{player.coords}</p>
                      </td>

                      {/* Fast Action Buttons */}
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Mute */}
                          <button
                            onClick={() => handleToggleMute(player)}
                            title={player.isMuted ? 'Unmute Player' : 'Mute Player'}
                            className={`p-1.5 rounded border text-xs ${
                              player.isMuted
                                ? 'bg-orange-950 border-orange-700 text-orange-300'
                                : 'bg-slate-800 border-slate-700 hover:bg-slate-700 text-slate-300'
                            }`}
                          >
                            {player.isMuted ? <VolumeX size={14} /> : <Volume2 size={14} />}
                          </button>

                          {/* Kick */}
                          <button
                            onClick={() => handleKick(player)}
                            title="Kick player from server"
                            className="p-1.5 bg-slate-800 hover:bg-amber-900 border border-slate-700 hover:border-amber-700 text-amber-300 rounded text-xs transition"
                          >
                            <UserMinus size={14} />
                          </button>

                          {/* Ban */}
                          <button
                            onClick={() => handleToggleBan(player)}
                            title={player.isBanned ? 'Unban Player' : 'Ban Player'}
                            className={`p-1.5 rounded border text-xs transition ${
                              player.isBanned
                                ? 'bg-red-900 border-red-600 text-red-200'
                                : 'bg-slate-800 hover:bg-red-950 border-slate-700 hover:border-red-700 text-red-400'
                            }`}
                          >
                            <Gavel size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}

                  {filteredPlayers.length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-center py-8 text-slate-500 text-sm">
                        No players found matching your search.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Right 1 Column: Live Console & Fast Operations */}
        <div className="space-y-4">
          {/* Fast Server Tools */}
          <div className="bg-[#161f2e] border border-slate-800 rounded-lg p-4">
            <h3 className="text-xs uppercase font-bold text-slate-400 tracking-wider mb-3 flex items-center gap-1.5">
              <Command size={14} className="text-blue-400" />
              Quick Overseer Tasks
            </h3>
            <div className="grid grid-cols-2 gap-2 text-xs font-medium">
              <button
                onClick={() => logAction('Broadcasting server restart notice to all realms.')}
                className="p-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-left transition"
              >
                Broadcast Notice
              </button>
              <button
                onClick={() => logAction('Force saved world chunks to disk (level.dat).')}
                className="p-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-left transition"
              >
                Save World
              </button>
              <button
                onClick={() => logAction('Cleared drop item entities from spawn area (54 removed).')}
                className="p-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-left transition"
              >
                Kill Dropped Items
              </button>
              <button
                onClick={() => logAction('Synced player whitelist from permissions db.')}
                className="p-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-left transition"
              >
                Reload Whitelist
              </button>
            </div>
          </div>

          {/* Interactive Console */}
          <div className="bg-[#161f2e] border border-slate-800 rounded-lg p-4 flex flex-col h-[400px]">
            <h3 className="text-xs uppercase font-bold text-slate-400 tracking-wider mb-2 flex items-center gap-1.5">
              <Terminal size={14} className="text-emerald-400" />
              Live Server Audit Console
            </h3>

            {/* Terminal Feed */}
            <div className="flex-1 bg-[#090d14] border border-slate-800 rounded p-3 font-mono text-[11px] text-slate-300 overflow-y-auto space-y-1 select-text">
              {actionLog.map((log, index) => (
                <div
                  key={index}
                  className={
                    log.includes('banned') || log.includes('kicked')
                      ? 'text-red-400'
                      : log.includes('gamemode')
                      ? 'text-yellow-300'
                      : 'text-slate-300'
                  }
                >
                  {log}
                </div>
              ))}
            </div>

            {/* Command Input Bar */}
            <form onSubmit={handleRunCommand} className="mt-3 flex items-center gap-2">
              <input
                type="text"
                placeholder="Type command (e.g. op, kick, say, whitelist)..."
                value={consoleInput}
                onChange={(e) => setConsoleInput(e.target.value)}
                className="flex-1 bg-[#090d14] border border-slate-700 rounded px-3 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-emerald-500"
              />
              <button
                type="submit"
                className="px-3 py-1.5 bg-emerald-700 hover:bg-emerald-600 text-white rounded text-xs font-semibold flex items-center gap-1 transition"
              >
                <Send size={12} />
              </button>
            </form>
          </div>
        </div>
      </div>
    </main>
  )
}