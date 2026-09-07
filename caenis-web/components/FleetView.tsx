'use client'
import Link from 'next/link'
import { useState } from 'react'
import { useAccount } from './ConsoleShell'
import { useResource } from '@/lib/poll'
import { api, message } from '@/lib/api'
import type { Instance } from '@/lib/types'
import { Button } from './ui/button'
import { Badge } from './ui/badge'
import { Alert } from './ui/alert'
import { Empty } from './ui/empty'
import { FieldGroup, Field, FieldLabel } from './ui/field'

export function bytes(value: number) { return (value / 1073741824).toFixed(1) + ' GiB' }
export function Status({ value }: { value: string }) { return <Badge data-state={value}>{value.replaceAll('_', ' ').toLowerCase()}</Badge> }

export function AgentKey({ id, secret }: { id: string; secret: string }) {
  const [visible, setVisible] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState('')

  async function copy() {
    try {
      await navigator.clipboard.writeText(secret)
      setCopied(true)
    } catch {
      setError('Copy is unavailable. Reveal and select the key to copy it.')
    }
  }

  function download() {
    const value = 'instance:\n  id: "' + id + '"\n  secret: "' + secret + '"\nbackend:\n  url: "' + window.location.origin + '"\n  telemetry-url: ""\n  timeout-seconds: 3\ncollector:\n  queue-capacity: 8192\n  spool-max-mb: 128\n'
    const url = URL.createObjectURL(new Blob([value], { type: 'text/yaml' }))
    const link = document.createElement('a'); link.href = url; link.download = 'config.yml'; link.click(); URL.revokeObjectURL(url)
  }

  return (
      <section className="setup-key">
        <h3>Connect {id}</h3>
        <p>Save this key now. It is only shown once. Place the plugin JAR and this configuration in your Paper plugins folder.</p>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="agent-secret">Agent secret</FieldLabel>
            <input id="agent-secret" type={visible ? 'text' : 'password'} readOnly value={secret} autoComplete="off" />
          </Field>
        </FieldGroup>
        <div className="action-row">
          <Button variant="outline" onClick={() => setVisible(!visible)}>{visible ? 'Hide key' : 'Reveal key'}</Button>
          <Button variant="outline" onClick={copy}>{copied ? 'Copied' : 'Copy key'}</Button>
          <Button onClick={download}>Download agent config</Button>
        </div>
        {error && <Alert>{error}</Alert>}
      </section>
  )
}

export default function FleetView() {
  const user = useAccount()
  const { data, error, loading, refresh } = useResource<Instance[]>('/fleet')
  const [filter, setFilter] = useState('')
  const [create, setCreate] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [key, setKey] = useState<{ id: string; secret: string } | null>(null)
  const nodes = data?.filter(node => [node.name, node.id, node.clusterGroup, node.region].join(' ').toLowerCase().includes(filter.toLowerCase())) ?? []

  async function enroll(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setFormError('')
    const values = Object.fromEntries(new FormData(event.currentTarget))
    try {
      const result = await api<{ instance: Instance; agentSecret: string }>('/fleet', { method: 'POST', body: JSON.stringify(values) })
      setKey({ id: result.instance.id, secret: result.agentSecret }); setCreate(false); refresh()
    } catch (error) { setFormError(message(error)) } finally { setBusy(false) }
  }

  return (
      <>
        <header className="page-heading">
          <div>
            <p className="section-label">Fleet operations</p>
            <h1>Your network, together.</h1>
            <p className="muted">Health, players and control across every enrolled Paper instance.</p>
          </div>
          {user.role === 'SUPERADMIN' && (
              <Button onClick={() => setCreate(!create)}>{create ? 'Close registration' : 'Register instance'}</Button>
          )}
        </header>

        {error && <Alert>{error} <Button variant="outline" onClick={refresh}>Retry</Button></Alert>}
        {key && <AgentKey id={key.id} secret={key.secret} />}

        {create && (
            <section className="surface">
              <h2>Register a Paper instance</h2>
              {formError && <Alert>{formError}</Alert>}
              <form onSubmit={enroll}>
                <FieldGroup>
                  <Field><FieldLabel htmlFor="instance-id">Instance ID</FieldLabel><input id="instance-id" name="id" placeholder="srv-survival-01" pattern="[a-z0-9][a-z0-9-]{2,63}" required /></Field>
                  <Field><FieldLabel htmlFor="instance-name">Display name</FieldLabel><input id="instance-name" name="name" placeholder="Survival" maxLength={100} required /></Field>
                  <div className="field-columns">
                    <Field><FieldLabel htmlFor="cluster">Cluster group</FieldLabel><input id="cluster" name="clusterGroup" defaultValue="default" maxLength={64} required /></Field>
                    <Field><FieldLabel htmlFor="region">Region</FieldLabel><input id="region" name="region" defaultValue="local" maxLength={64} required /></Field>
                  </div>
                  <Button disabled={busy} type="submit">{busy ? 'Registering…' : 'Create identity'}</Button>
                </FieldGroup>
              </form>
            </section>
        )}

        <div className="fleet-summary">
          <span><strong>{data?.filter(node => node.status === 'ONLINE').length ?? '—'}</strong> online</span>
          <span><strong>{data?.filter(node => node.status === 'UNREACHABLE').length ?? '—'}</strong> unreachable</span>
          <span><strong>{data?.filter(node => node.status === 'ONLINE').reduce((sum, node) => sum + (node.health?.players.length ?? 0), 0) ?? '—'}</strong> active players</span>
        </div>

        <FieldGroup>
          <Field><FieldLabel htmlFor="fleet-search">Find an instance</FieldLabel><input id="fleet-search" type="search" value={filter} onChange={event => setFilter(event.target.value)} placeholder="Name, cluster or region" /></Field>
        </FieldGroup>

        {loading ? (
            <p role="status" className="muted">Loading fleet…</p>
        ) : nodes.length ? (
            <div className="table-wrap">
              <table>
                <caption className="sr-only">Registered server health</caption>
                <thead>
                <tr><th>Instance</th><th>Status</th><th>TPS / MSPT</th><th>Heap</th><th>Players</th><th>Region</th></tr>
                </thead>
                <tbody>
                {nodes.map(node => (
                    <tr key={node.id}>
                      <td><Link className="table-link" href={'/fleet/' + node.id}>{node.name}</Link><small>{node.clusterGroup} / {node.id}</small></td>
                      <td><Status value={node.status} /></td>
                      <td>{node.health ? <><span data-performance={node.health.tps[0] >= 19.5 ? 'good' : node.health.tps[0] >= 18 ? 'warn' : 'poor'}>{node.health.tps[0].toFixed(1)}</span> / {node.health.mspt.toFixed(1)} ms{node.status !== 'ONLINE' && <small>Last known sample</small>}</> : 'Awaiting telemetry'}</td>
                      <td>{node.health ? <><span>{bytes(node.health.heapUsed)} / {bytes(node.health.heapMax)}</span><meter min={0} max={node.health.heapMax} value={node.health.heapUsed} aria-label={node.name + ' heap usage'} /></> : '—'}</td>
                      <td>{node.health?.players.length ?? '—'}</td>
                      <td>{node.region}</td>
                    </tr>
                ))}
                </tbody>
              </table>
            </div>
        ) : (
            <Empty title={filter ? 'No matching instances' : 'Your fleet starts here.'}>
              {filter ? 'Try another search.' : 'Register an instance, download its agent configuration and install Caenis Overseer on that Paper server.'}
            </Empty>
        )}
      </>
  )
}