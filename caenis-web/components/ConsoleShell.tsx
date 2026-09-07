'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { createContext, useContext, useState, useCallback } from 'react'
import { Activity, Map, Terminal, ScrollText, Settings, LogOut } from 'lucide-react'
import type { Account, CommandEvent } from '@/lib/types'
import {api, clearCsrf, message} from '@/lib/api'
import { useLive } from '@/lib/live'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Alert } from '@/components/ui/alert'

const SessionContext = createContext<Account | null>(null)
const LiveContext = createContext<{ revision: number; command: CommandEvent | null }>({ revision: 0, command: null })
export function useAccount() { const user = useContext(SessionContext); if (!user) throw new Error('Session missing'); return user }
export function useConsoleLive() { return useContext(LiveContext) }

export default function ConsoleShell({ account, children }: { account: Account; children: React.ReactNode }) {
  const path = usePathname()
  const [revision, setRevision] = useState(0)
  const [command, setCommand] = useState<CommandEvent | null>(null)
  const [error, setError] = useState('')
  const changed = useCallback(() => setRevision(value => value + 1), [])
  const online = useLive(changed, setCommand, account.role !== 'ANALYST')
  const links = [
    { href: '/fleet', label: 'Fleet', icon: Activity, visible: account.role !== 'ANALYST' },
    { href: '/tactical', label: 'Tactical', icon: Map, visible: true },
    { href: '/rcon', label: 'Console', icon: Terminal, visible: account.role !== 'ANALYST' },
    { href: '/audit', label: 'Audit log', icon: ScrollText, visible: account.role !== 'ANALYST' },
    { href: '/settings', label: 'Settings', icon: Settings, visible: account.role === 'SUPERADMIN' },
  ]
  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' })
      clearCsrf()
      window.location.assign('/login')
    } catch (error) {
      setError(message(error))
    }
  }
  return <SessionContext.Provider value={account}><LiveContext.Provider value={{ revision, command }}>
    <div className="console-shell">
      <a href="#workspace" className="skip-link">Skip to workspace</a>
      <aside className="console-nav">
        <Link className="console-brand" href="/">C<span>Æ</span>NIS<small>Fleet console</small></Link>
        <nav aria-label="Operations">{links.filter(link => link.visible).map(({ href, label, icon: Icon }) =>
          <Link key={href} href={href} aria-current={path.startsWith(href) ? 'page' : undefined}><Icon size={18} />{label}</Link>)}</nav>
        <div className="session-details"><span>{account.username}</span><small>{account.role.toLowerCase()}</small>
          <Button variant="ghost" onClick={logout}><LogOut data-icon="inline-start" />Sign out</Button></div>
      </aside>
      <div className="console-body"><header className="console-top"><span>Caenis Overseer</span><Badge data-state={online ? 'ONLINE' : 'UNREACHABLE'}>{online ? 'Live updates connected' : 'Live updates reconnecting · REST refresh active'}</Badge></header>
        <main id="workspace" className="workspace">{error && <Alert>{error}</Alert>}{children}</main>
      </div>
    </div>
  </LiveContext.Provider></SessionContext.Provider>
}
