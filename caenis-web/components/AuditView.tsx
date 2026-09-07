'use client'
import { useEffect, useState } from 'react'
import { api, message } from '@/lib/api'
import type { AuditEvent } from '@/lib/types'
import { Button } from './ui/button'
import { Alert } from './ui/alert'
import { Badge } from './ui/badge'
import { FieldGroup, Field, FieldLabel } from './ui/field'
import { Empty } from './ui/empty'
export default function AuditView() {
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [more, setMore] = useState(false)
  useEffect(() => { const timeout = setTimeout(() => setSearch(query), 300); return () => clearTimeout(timeout) }, [query])
  async function load(before?: number) {
    setBusy(true); setError('')
    try {
      const data = await api<AuditEvent[]>('/audit?q=' + encodeURIComponent(search) + (before ? '&before=' + before : ''))
      setEvents(previous => before ? [...previous, ...data].slice(-1000) : data); setMore(data.length === 100)
    } catch (error) { setError(message(error)) } finally { setBusy(false) }
  }
  useEffect(() => {
    const controller = new AbortController()
    setBusy(true); setError('')
    api<AuditEvent[]>('/audit?q=' + encodeURIComponent(search), { signal: controller.signal })
      .then(data => { setEvents(data); setMore(data.length === 100) })
      .catch(error => { if (!controller.signal.aborted) setError(message(error)) })
      .finally(() => { if (!controller.signal.aborted) setBusy(false) })
    return () => controller.abort()
  }, [search])
  return <><header className="page-heading"><div><p className="section-label">Accountability</p><h1>The complete command trail.</h1><p className="muted">Requests, dispatches and outcomes remain separate, append-only entries.</p></div><Button variant="outline" disabled={busy} onClick={() => load()}>Refresh</Button></header>
    <FieldGroup><Field><FieldLabel htmlFor="audit-search">Search audit log</FieldLabel><input id="audit-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Actor, command or outcome" /></Field></FieldGroup>
    {error && <Alert>{error}</Alert>}{busy && <p role="status">Loading entries…</p>}
    {!events.length && !busy ? <Empty title="No audit entries">Command and configuration activity will appear here.</Empty> : <div className="audit-list">{events.map(event =>
      <article key={event.id}><header><Badge data-state={event.outcome}>{event.outcome.toLowerCase()}</Badge><time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time><span>{event.instanceId ?? 'Platform'}</span></header>
        <p className="audit-command">{event.command}</p><div className="muted text-sm">{event.action} · Actor {event.actor} · Reference {event.correlationId}</div>
        {event.detail && <details><summary>Response / details</summary><pre>{event.detail}</pre></details>}</article>)}</div>}
    {more && <Button disabled={busy} variant="outline" onClick={() => load(events.at(-1)?.id)}>Load older entries</Button>}
  </>
}
