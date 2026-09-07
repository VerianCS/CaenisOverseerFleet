'use client'
import Link from 'next/link'
import { useState } from 'react'
import { useResource } from '@/lib/poll'
import { api, message } from '@/lib/api'
import type { Instance, Sample } from '@/lib/types'
import { useAccount } from './ConsoleShell'
import { AgentKey, Status, bytes } from './FleetView'
import TerminalView from './TerminalView'
import { Button } from './ui/button'
import { Alert } from './ui/alert'
import { FieldGroup, Field, FieldLabel } from './ui/field'

function History({ samples }: { samples: Sample[] }) {
  if (!samples.length) return <p className="muted">History appears after the first heartbeat.</p>
  const width = 800, height = 170
  const points = samples.map((sample, index) => ((index / Math.max(1, samples.length - 1)) * width) + ',' + (height - Math.max(0, Math.min(20, sample.tps)) / 20 * (height - 12))).join(' ')
  const first = samples[0], last = samples[samples.length - 1]
  return <><svg className="history-chart" viewBox={'0 0 ' + width + ' ' + (height + 12)} role="img" aria-label="TPS history from 0 to 20 ticks per second">
    {[0, 10, 20].map(tps => <g key={tps}><line x1="0" x2={width} y1={height - tps / 20 * (height - 12)} y2={height - tps / 20 * (height - 12)} stroke="var(--border)" /><text x="4" y={height - tps / 20 * (height - 12) - 3} fill="var(--muted-foreground)" fontSize="10">{tps}</text></g>)}
    <polyline points={points} fill="none" stroke="var(--tidewake)" strokeWidth="2" /></svg><div className="history-range"><span>{new Date(first.time).toLocaleString()}</span><span>{new Date(last.time).toLocaleString()}</span></div>
    <details><summary>Read recent samples</summary><div className="table-wrap"><table><thead><tr><th>Time</th><th>TPS</th><th>MSPT</th><th>Heap</th><th>Players</th></tr></thead><tbody>{samples.slice(-20).reverse().map(sample => <tr key={sample.time}><td>{new Date(sample.time).toLocaleTimeString()}</td><td>{sample.tps.toFixed(2)}</td><td>{sample.mspt.toFixed(1)}</td><td>{bytes(sample.heapUsed)}</td><td>{sample.players}</td></tr>)}</tbody></table></div></details></>
}

export default function NodeView({ id }: { id: string }) {
  const user = useAccount()
  const { data: node, error, loading, refresh } = useResource<Instance>('/fleet/' + id)
  const [hours, setHours] = useState('24')
  const { data: history, error: historyError } = useResource<Sample[]>('/fleet/' + id + '/history?hours=' + hours, 30000)
  const [notice, setNotice] = useState('')
  const [failure, setFailure] = useState('')
  const [busy, setBusy] = useState(false)
  const [key, setKey] = useState('')
  const [lifecycle, setLifecycle] = useState('')
  const [confirmRotate, setConfirmRotate] = useState(false)
  async function action(action: string) {
    setBusy(true); setFailure(''); setNotice('')
    try {
      const result = await api<{ id: string }>('/fleet/' + id + '/lifecycle', { method: 'POST', body: JSON.stringify({ action }) })
      setNotice('Lifecycle command queued. Follow its result in the audit log. Reference: ' + result.id)
      setLifecycle(''); refresh()
    } catch (error) { setFailure(message(error)) } finally { setBusy(false) }
  }
  async function connections(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setFailure('')
    const data = Object.fromEntries(new FormData(event.currentTarget))
    const input = { ...data, rconPort: Number(data.rconPort), rconPassword: data.rconPassword || null, vaultPath: data.vaultPath || null, rconHost: data.rconHost || null, managerUrl: data.managerUrl || null, managerKey: data.managerKey || null }
    try { await api('/fleet/' + id + '/connections', { method: 'PUT', body: JSON.stringify(input) }); setNotice('Connection settings saved.'); refresh() }
    catch (error) { setFailure(message(error)) } finally { setBusy(false) }
  }
  async function settings(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!node) return
    const data = new FormData(event.currentTarget); setBusy(true); setFailure('')
    try {
      await api('/fleet/' + id + '/settings', { method: 'PUT', body: JSON.stringify({
        enabled: data.has('enabled'), automationEnabled: data.has('automationEnabled'), automationAction: data.get('automationAction'),
        configuration: { enabled: data.has('sampling'), debugSampling: data.has('debug'), batchSize: Number(data.get('batchSize')), flushIntervalMs: Number(data.get('flushIntervalMs')), jitterMs: Number(data.get('jitterMs')) },
      }) }); setNotice('Instance settings saved. The agent receives sampling updates on its next heartbeat.'); refresh()
    } catch (error) { setFailure(message(error)) } finally { setBusy(false) }
  }
  async function rotate() {
    setBusy(true); setFailure('')
    try { const result = await api<{ agentSecret: string }>('/fleet/' + id + '/rotate-key', { method: 'POST' }); setKey(result.agentSecret); setConfirmRotate(false) }
    catch (error) { setFailure(message(error)) } finally { setBusy(false) }
  }
  if (loading) return <p role="status">Loading instance…</p>
  if (!node) return <Alert>{error || 'Instance not found.'}<Link href="/fleet">Return to fleet</Link></Alert>
  return <>
    <Link className="back-link" href="/fleet">← Fleet</Link><header className="page-heading"><div><p className="section-label">{node.clusterGroup} / {node.region}</p><h1>{node.name}</h1><p className="muted">{node.id}</p></div><Status value={node.status} /></header>
    {error && <Alert>{error} Displaying the last received snapshot.</Alert>}{failure && <Alert>{failure}</Alert>}{notice && <p className="notice" role="status">{notice} <Link href="/audit">Open audit log</Link></p>}
    {node.status === 'UNREACHABLE' && <Alert>Three heartbeat cycles were missed. These metrics are the last known sample; inspect the agent and its connection.</Alert>}
    {historyError && <Alert>Performance history: {historyError}</Alert>}
    <dl className="metrics-strip"><div><dt>TPS · 1m / 5m / 15m</dt><dd>{node.health?.tps.map(value => value.toFixed(1)).join(' / ') ?? '—'}</dd></div><div><dt>Mean tick time</dt><dd>{node.health ? node.health.mspt.toFixed(1) + ' ms' : '—'}</dd></div><div><dt>Heap usage</dt><dd>{node.health ? bytes(node.health.heapUsed) + ' / ' + bytes(node.health.heapMax) : '—'}</dd></div><div><dt>Chunks / entities</dt><dd>{node.health ? node.health.loadedChunks + ' / ' + node.health.entities : '—'}</dd></div></dl>
    <section className="surface"><div className="section-heading"><h2>Performance history</h2><label>Time range <select value={hours} onChange={event => setHours(event.target.value)}><option value="1">1 hour</option><option value="24">24 hours</option><option value="72">72 hours</option></select></label></div><History samples={history ?? []} /></section>
    {user.role === 'SUPERADMIN' && <section className="surface"><h2>Server lifecycle</h2><p className="muted">The host manager controls the Paper process. Stopping or restarting disconnects its players.</p><div className="action-row">{['start', 'stop', 'restart'].map(action => <Button key={action} variant={action === 'start' ? 'default' : 'outline'} disabled={busy || !node.lifecycleConfigured} onClick={() => setLifecycle(action)}>{action[0].toUpperCase() + action.slice(1)}</Button>)}</div>
      {!node.lifecycleConfigured && <p className="muted">Configure this instance in a host manager and add its connection below.</p>}
      {lifecycle && <Alert>Apply “{lifecycle}” to {node.name}? <div className="action-row"><Button disabled={busy} onClick={() => action(lifecycle)}>Confirm {lifecycle}</Button><Button variant="outline" onClick={() => setLifecycle('')}>Cancel</Button></div></Alert>}
    </section>}
    <section className="surface"><h2>Players <span className="muted">({node.health?.players.length ?? 0})</span></h2><div className="table-wrap"><table><thead><tr><th>Player</th><th>World</th><th>Latency</th></tr></thead><tbody>{node.health?.players.map(player => <tr key={player.id}><td>{player.name}<small className="data">{player.id}</small></td><td>{player.world}</td><td>{player.ping} ms</td></tr>)}</tbody></table></div>{!node.health?.players.length && <p className="muted">No players reported.</p>}</section>
    <TerminalView fixedInstance={node} />
    <section className="surface"><h2>Telemetry delivery</h2><dl className="metrics-strip"><div><dt>Queued events</dt><dd>{node.health?.queueDepth ?? '—'}</dd></div><div><dt>Disk spool</dt><dd>{node.health ? (node.health.spoolBytes / 1048576).toFixed(1) + ' MiB' : '—'}</dd></div><div><dt>Dropped events</dt><dd>{node.health?.droppedEvents ?? '—'}</dd></div></dl></section>
    {user.role === 'SUPERADMIN' && <>
      <details className="surface"><summary>RCON and host manager connections</summary><form onSubmit={connections}><FieldGroup>
        <div className="field-columns"><Field><FieldLabel htmlFor="rconHost">RCON host</FieldLabel><input id="rconHost" name="rconHost" defaultValue={node.rconHost ?? ''} placeholder="10.0.0.20" /></Field><Field><FieldLabel htmlFor="rconPort">RCON port</FieldLabel><input id="rconPort" name="rconPort" type="number" min="1" max="65535" defaultValue={node.rconPort} required /></Field></div>
        <Field><FieldLabel htmlFor="rconPassword">RCON password</FieldLabel><input id="rconPassword" name="rconPassword" type="password" autoComplete="new-password" placeholder="Leave blank to keep the saved password" /></Field>
        <Field><FieldLabel htmlFor="vaultPath">Vault KV v2 API path (optional)</FieldLabel><input id="vaultPath" name="vaultPath" defaultValue={node.vaultPath ?? ''} placeholder="secret/data/caenis/survival" /></Field>
        <Field><FieldLabel htmlFor="managerUrl">Host manager URL</FieldLabel><input id="managerUrl" name="managerUrl" type="url" defaultValue={node.managerUrl ?? ''} placeholder="http://10.0.0.20:8090" /></Field>
        <Field><FieldLabel htmlFor="managerKey">Host manager key</FieldLabel><input id="managerKey" name="managerKey" type="password" autoComplete="new-password" placeholder="Leave blank to keep the saved key" /></Field><Button type="submit" disabled={busy}>Save connections</Button>
      </FieldGroup></form></details>
      <details className="surface"><summary>Agent and detector settings</summary><form onSubmit={settings}><FieldGroup>
        <label className="check"><input type="checkbox" name="enabled" defaultChecked={node.enabled} />Instance enabled</label><label className="check"><input type="checkbox" name="sampling" defaultChecked={node.configuration.enabled} />Capture mining events</label><label className="check"><input type="checkbox" name="debug" defaultChecked={node.configuration.debugSampling} />Debug sampling</label>
        <div className="field-columns"><Field><FieldLabel htmlFor="batchSize">Batch size</FieldLabel><input id="batchSize" name="batchSize" type="number" min="1" max="500" required defaultValue={node.configuration.batchSize} /></Field><Field><FieldLabel htmlFor="flushIntervalMs">Dispatch interval (ms)</FieldLabel><input id="flushIntervalMs" name="flushIntervalMs" type="number" min="500" max="10000" required defaultValue={node.configuration.flushIntervalMs} /></Field><Field><FieldLabel htmlFor="jitterMs">Mining tolerance (ms)</FieldLabel><input id="jitterMs" name="jitterMs" type="number" min="0" max="2000" required defaultValue={node.configuration.jitterMs} /></Field></div>
        <label className="check"><input type="checkbox" name="automationEnabled" defaultChecked={node.automationEnabled} />Enable automatic containment for repeated critical fast-mining alerts</label><Field><FieldLabel htmlFor="automationAction">Containment action</FieldLabel><select id="automationAction" name="automationAction" defaultValue={node.automationAction}><option value="kick">Kick</option><option value="freeze">Freeze for 5 minutes</option></select></Field><p className="muted">Requires three critical events in one minute. Limited to one action per player per five minutes and five players per instance per minute. Occlusion alerts always require human review.</p><Button type="submit" disabled={busy}>Save settings</Button>
      </FieldGroup></form></details>
      <details className="surface"><summary>Rotate agent key</summary><p>Rotating immediately revokes the current key and agent session. Download and install the replacement configuration to reconnect.</p><Button variant="destructive" onClick={() => setConfirmRotate(true)} disabled={busy}>Rotate key</Button>{confirmRotate && <Alert>Revoke the existing agent credentials?<div className="action-row"><Button onClick={rotate} disabled={busy}>Revoke and replace</Button><Button variant="outline" onClick={() => setConfirmRotate(false)}>Cancel</Button></div></Alert>}{key && <AgentKey id={id} secret={key} />}</details>
    </>}
  </>
}
