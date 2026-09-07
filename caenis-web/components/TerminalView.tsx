'use client'
import { useEffect, useRef, useState } from 'react'
import { api, message } from '@/lib/api'
import { useResource } from '@/lib/poll'
import type { Instance } from '@/lib/types'
import { useAccount, useConsoleLive } from './ConsoleShell'
import { Button } from './ui/button'
import { FieldGroup, Field, FieldLabel } from './ui/field'
import { Alert } from './ui/alert'
import { Empty } from './ui/empty'

export default function TerminalView({ fixedInstance }: { fixedInstance?: Instance }) {
  const account = useAccount()
  const { data: fleet, error: fleetError, loading: fleetLoading, refresh: refreshFleet } = useResource<Instance[]>(fixedInstance ? null : '/fleet')
  const [selected, setSelected] = useState('')
  const node = fixedInstance ?? fleet?.find(value => value.id === selected) ?? fleet?.[0]
  const [command, setCommand] = useState('')
  const [action, setAction] = useState('kick')
  const [player, setPlayer] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [output, setOutput] = useState('Choose a connected instance. Command responses will appear here.\n')
  const [error, setError] = useState('')
  const [history, setHistory] = useState<string[]>([])
  const historyPosition = useRef(0)
  const { command: incoming } = useConsoleLive()
  const terminal = useRef<HTMLPreElement>(null)
  const { data: status } = useResource<{ outcome: string; detail: string }[]>(pending ? '/commands/' + pending : null, 1500)
  function append(value: string) { setOutput(previous => (previous + value).slice(-262144)) }
  useEffect(() => {
    if (!incoming || incoming.id !== pending) return
    if (incoming.status === 'OUTPUT') setOutput(previous => (previous + incoming.output).slice(-262144))
  }, [incoming, pending])
  useEffect(() => {
    const last = status?.at(-1)
    if (!last || !['SUCCEEDED', 'UNCERTAIN', 'REJECTED'].includes(last.outcome)) return
    setOutput(previous => (previous + '\n[' + last.outcome.toLowerCase() + ']\n' + last.detail + '\n').slice(-262144))
    setPending(null); setBusy(false)
  }, [status])
  useEffect(() => {
    const element = terminal.current
    if (element && element.scrollHeight - element.scrollTop - element.clientHeight < 400) element.scrollTop = element.scrollHeight
  }, [output])
  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!node || busy) return
    setError(''); setBusy(true)
    const raw = command.trim()
    const input = account.role === 'SUPERADMIN'
      ? { instanceId: node.id, command: raw }
      : { instanceId: node.id, action, playerId: player || node.health?.players[0]?.id }
    append('\n> [' + node.name + '] ' + (account.role === 'SUPERADMIN' ? raw : action + ' ' + (player || node.health?.players[0]?.id || '')) + '\n')
    try {
      const result = await api<{ id: string }>('/commands', { method: 'POST', body: JSON.stringify(input) })
      setPending(result.id)
      if (raw) { setHistory(previous => [...previous, raw].slice(-50)); historyPosition.current = 0; setCommand('') }
    } catch (error) { setError(message(error)); setBusy(false) }
  }
  if (!fixedInstance && fleetLoading) return <p role="status">Loading instances…</p>
  if (fleetError) return <Alert>{fleetError}<Button variant="outline" onClick={refreshFleet}>Retry</Button></Alert>
  if (!node) return <Empty title="No instance selected">Register and connect a server from the Fleet page before issuing commands.</Empty>
  return <section className="terminal-section">
    {!fixedInstance && <FieldGroup><Field><FieldLabel htmlFor="console-instance">Target instance</FieldLabel><select id="console-instance" disabled={busy} value={node.id} onChange={event => { setSelected(event.target.value); setPlayer('') }}>{fleet?.map(node => <option value={node.id} key={node.id}>{node.name} · {node.status.toLowerCase()}</option>)}</select></Field></FieldGroup>}
    <div className="terminal-title"><span>{node.name} / remote console</span><Button variant="ghost" onClick={() => setOutput('')} disabled={busy}>Clear display</Button></div>
    <pre className="terminal-output" ref={terminal} tabIndex={0} aria-label="Command response output">{output}</pre>
    {error && <Alert>{error}</Alert>}
    <form onSubmit={submit}><FieldGroup>
      {account.role === 'SUPERADMIN' ? <Field><FieldLabel htmlFor={'command-' + node.id}>RCON command</FieldLabel>
        <input id={'command-' + node.id} value={command} onChange={event => setCommand(event.target.value)} maxLength={1446} required autoComplete="off" spellCheck={false} placeholder="list, tps, save-all…"
          onKeyDown={event => {
            if (!['ArrowUp', 'ArrowDown'].includes(event.key) || !history.length) return
            event.preventDefault()
            historyPosition.current = Math.max(0, Math.min(history.length, historyPosition.current + (event.key === 'ArrowUp' ? 1 : -1)))
            setCommand(historyPosition.current ? history[history.length - historyPosition.current] : '')
          }} /></Field> :
        <div className="field-columns"><Field><FieldLabel htmlFor="moderation-action">Action</FieldLabel><select id="moderation-action" value={action} onChange={event => setAction(event.target.value)}><option value="kick">Kick</option><option value="freeze">Freeze for 5 minutes</option><option value="unfreeze">Unfreeze</option><option value="spectate">Move to spectator mode</option></select></Field>
          <Field><FieldLabel htmlFor="moderation-player">Player</FieldLabel><select required id="moderation-player" value={player || node.health?.players[0]?.id || ''} onChange={event => setPlayer(event.target.value)}>{node.health?.players.map(player => <option value={player.id} key={player.id}>{player.name}</option>)}</select></Field></div>}
      <div className="action-row"><Button type="submit" disabled={busy || node.status !== 'ONLINE' || !node.rconConfigured}>{busy ? 'Awaiting response…' : 'Send command'}</Button><span className="muted text-sm">{!node.rconConfigured ? 'Configure RCON in instance settings.' : 'Commands and outcomes are recorded in the audit log.'}</span></div>
    </FieldGroup></form>
    {busy && <p role="status" className="muted">Command in progress. If the connection drops, the audit log retains its last confirmed state.</p>}
  </section>
}
