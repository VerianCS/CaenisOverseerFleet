'use client'
import { useEffect, useState } from 'react'
import { useResource } from '@/lib/poll'
import type { Scope } from '@/lib/types'
import type { MapMarker, ThreatAlert, OverviewStats } from '@/app/cheat-tracking/types/deck'
import TacticalCanvas, { type Viewport } from './TacticalCanvas'
import { Alert } from './ui/alert'
import { Button } from './ui/button'
import { Badge } from './ui/badge'
import { Empty } from './ui/empty'
import { FieldGroup, Field, FieldLabel } from './ui/field'
export default function TacticalView() {
  const { data: scopes, error: scopeError } = useResource<Scope[]>('/deck/scopes', 15000)
  const [instanceId, setInstanceId] = useState('')
  const scope = scopes?.find(scope => scope.id === instanceId) ?? scopes?.[0]
  const [world, setWorld] = useState('world')
  const [minutes, setMinutes] = useState('60')
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [allAlerts, setAllAlerts] = useState(false)
  const [paused, setPaused] = useState(false)
  const [view, setView] = useState<Viewport>({ x: 0, z: 0, scale: .2 })
  const [bounds, setBounds] = useState(view)
  const [selected, setSelected] = useState<MapMarker | null>(null)
  const worldName = scope?.worlds.includes(world) ? world : scope?.worlds[0] ?? world
  useEffect(() => { const timer = setTimeout(() => setSearch(query), 300); return () => clearTimeout(timer) }, [query])
  useEffect(() => { const timer = setTimeout(() => setBounds(view), 250); return () => clearTimeout(timer) }, [view])
  const radius = Math.ceil(1600 / bounds.scale)
  const params = new URLSearchParams({
    instanceId: scope?.id ?? '', world: worldName, minutesBack: minutes,
    minX: String(Math.max(-30000000, Math.floor(bounds.x - radius))), maxX: String(Math.min(30000000, Math.ceil(bounds.x + radius))),
    minZ: String(Math.max(-30000000, Math.floor(bounds.z - radius))), maxZ: String(Math.min(30000000, Math.ceil(bounds.z + radius))),
    cellSize: String(bounds.scale < .08 ? 256 : bounds.scale < .2 ? 64 : 0), limit: '2000',
  })
  const { data: markers, error: mapError, refresh } = useResource<MapMarker[]>(scope ? '/deck/map?' + params : null, paused ? 2147483647 : 5000)
  const { data: alerts, error: alertError } = useResource<ThreatAlert[]>(scope ? '/deck/alerts?' + (allAlerts ? '' : 'instanceId=' + scope.id + '&') + 'q=' + encodeURIComponent(search) : null, paused ? 2147483647 : 5000)
  const { data: stats } = useResource<OverviewStats>('/deck/stats', paused ? 2147483647 : 5000)
  return <>
    <header className="page-heading"><div><p className="section-label">Spatial observation</p><h1>Follow the evidence.</h1><p className="muted">Mining events in X/Z space. Each alert includes its observed threshold.</p></div><Button variant="outline" onClick={() => setPaused(!paused)}>{paused ? 'Resume refresh' : 'Pause refresh'}</Button></header>
    {(scopeError || mapError || alertError) && <Alert>{scopeError || mapError || alertError}<Button variant="outline" onClick={refresh}>Retry map</Button></Alert>}
    <FieldGroup><div className="tactical-filters"><Field><FieldLabel htmlFor="map-instance">Instance</FieldLabel><select id="map-instance" value={scope?.id ?? ''} onChange={event => { setInstanceId(event.target.value); setSelected(null); setView({ x: 0, z: 0, scale: .2 }) }}>{scopes?.map(scope => <option key={scope.id} value={scope.id}>{scope.name}</option>)}</select></Field>
      <Field><FieldLabel htmlFor="map-world">World</FieldLabel><input id="map-world" list="known-worlds" value={worldName} onChange={event => setWorld(event.target.value)} /><datalist id="known-worlds">{scope?.worlds.map(world => <option key={world} value={world} />)}</datalist></Field>
      <Field><FieldLabel htmlFor="map-window">History window</FieldLabel><select id="map-window" value={minutes} onChange={event => setMinutes(event.target.value)}><option value="15">15 minutes</option><option value="60">1 hour</option><option value="1440">24 hours</option></select></Field></div></FieldGroup>
    <dl className="metrics-strip"><div><dt>Retained mining events</dt><dd>{stats?.totalEventsLogged.toLocaleString() ?? '—'}</dd></div><div><dt>Events / second · last minute</dt><dd>{stats?.eventsPerSecond?.toFixed(1) ?? '—'}</dd></div><div><dt>Recorded alerts</dt><dd>{stats?.activeThreatsCount.toLocaleString() ?? '—'}</dd></div><div><dt>Automated interventions</dt><dd>{stats?.automatedInterventions?.toLocaleString() ?? '—'}</dd></div></dl>
    {!scope ? <Empty title="No telemetry sources">A fleet administrator must register an instance and connect its agent.</Empty> : <div className="tactical-workspace"><section>
      <TacticalCanvas markers={markers ?? []} view={view} onView={setView} onSelect={setSelected} />
      {selected && <div className="selected-event"><strong>{selected.blockType}</strong><span>{selected.playerName} · X {selected.x} / Y {selected.y} / Z {selected.z}</span><Button variant="ghost" onClick={() => setSelected(null)}>Dismiss</Button></div>}
      <form className="coordinate-form" onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); setView({ ...view, x: Number(data.get('x')), z: Number(data.get('z')) }) }}>
        <label>X <input aria-label="Center X coordinate" name="x" type="number" min="-30000000" max="30000000" defaultValue={0} required /></label><label>Z <input aria-label="Center Z coordinate" name="z" type="number" min="-30000000" max="30000000" defaultValue={0} required /></label><Button type="submit" variant="outline">Go to coordinates</Button>
      </form>
      {!markers?.length && <p className="muted">No mining events in this viewport and time window. Select an alert to move to its coordinates.</p>}
      <details><summary>Accessible event list ({markers?.length ?? 0} markers)</summary><div className="event-table table-wrap"><table><thead><tr><th>Player / cluster</th><th>Block</th><th>Coordinates</th><th>Exposure</th></tr></thead><tbody>{markers?.slice(0, 200).map(marker => <tr key={marker.id}><td>{marker.playerName}{(marker.count ?? 1) > 1 ? ' · ' + marker.count + ' events' : ''}</td><td>{marker.blockType}</td><td>{marker.x}, {marker.y}, {marker.z}</td><td>{marker.isExposed ? 'Exposed' : 'Occluded'}</td></tr>)}</tbody></table></div></details>
    </section><aside className="incident-sidebar"><FieldGroup><Field><FieldLabel htmlFor="incident-search">Search incidents</FieldLabel><input id="incident-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Player, infraction or coordinates" /></Field><label className="check text-sm"><input type="checkbox" checked={allAlerts} onChange={event => setAllAlerts(event.target.checked)} />Search across the fleet</label></FieldGroup>
      <div className="incident-feed">{alerts?.map(alert => <article key={alert.id}><div className="incident-meta"><Badge data-state={alert.severity}>{alert.severity.toLowerCase()}</Badge><time dateTime={alert.createdAt}>{new Date(alert.createdAt).toLocaleTimeString()}</time></div><h3>{alert.playerName}</h3><p>{alert.alertType === 'FAST_MINING_TEMPORAL_BREACH' ? 'Fast mining' : 'Occluded ore pattern'}</p><p className="muted text-sm">{alert.diagnosticData}</p><Button variant="outline" onClick={() => { if (alert.instanceId) setInstanceId(alert.instanceId); setWorld(alert.world); setView({ x: alert.x, z: alert.z, scale: 1.5 }) }}>{alert.world} · {alert.x}, {alert.y}, {alert.z}</Button></article>)}
        {!alerts?.length && <p className="muted">No incidents match this selection.</p>}</div>
    </aside></div>}
  </>
}
