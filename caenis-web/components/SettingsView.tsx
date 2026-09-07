'use client'
import { useState } from 'react'
import { api, message } from '@/lib/api'
import { useResource } from '@/lib/poll'
import type { Account, Instance, Role } from '@/lib/types'
import { useAccount } from './ConsoleShell'
import { Button } from './ui/button'
import { Alert } from './ui/alert'
import { Badge } from './ui/badge'
import { FieldGroup, Field, FieldLabel } from './ui/field'
type User = Account & { enabled: boolean }
type Model = { instanceId: string; playerId: string; world: string; samples: number; occluded: number; trainedAt: string }
export default function SettingsView() {
  const account = useAccount()
  const { data: users, error: usersError, refresh } = useResource<User[]>('/users', 30000)
  const { data: instances, error: fleetError } = useResource<Instance[]>('/fleet', 30000)
  const { data: models, error: modelsError, refresh: refreshModels } = useResource<Model[]>('/settings/baselines', 30000)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  async function add(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; setBusy(true); setError('')
    try { await api('/users', { method: 'POST', body: JSON.stringify(Object.fromEntries(new FormData(form))) }); form.reset(); refresh(); setNotice('Account created.') }
    catch (error) { setError(message(error)) } finally { setBusy(false) }
  }
  async function update(user: User, role: Role, enabled: boolean) {
    setBusy(true); setError('')
    try { await api('/users/' + user.id, { method: 'PUT', body: JSON.stringify({ role, enabled }) }); refresh(); setNotice('Account updated. Its previous sessions were revoked.') }
    catch (error) { setError(message(error)) } finally { setBusy(false) }
  }
  async function password(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    const form = event.currentTarget, data = new FormData(form), user = users?.find(user => user.id === data.get('userId'))
    if (!user) { setBusy(false); return }
    try { await api('/users/' + user.id, { method: 'PUT', body: JSON.stringify({ role: user.role, enabled: user.enabled, password: data.get('password') }) }); form.reset(); setNotice('Password changed and previous sessions revoked.'); if (user.id === account.id) window.location.assign('/login') }
    catch (error) { setError(message(error)) } finally { setBusy(false) }
  }
  async function train(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); setBusy(true); setError('')
    try {
      const result = await api<{ models: number }>('/settings/baselines/train', { method: 'POST', body: JSON.stringify({
        instanceId: data.get('instanceId'), from: new Date(String(data.get('from'))).toISOString(), until: new Date(String(data.get('until'))).toISOString(), reviewedAsLegitimate: data.has('reviewed'),
      }) }); setNotice(result.models + ' player baselines trained from the reviewed interval.'); refreshModels()
    } catch (error) { setError(message(error)) } finally { setBusy(false) }
  }
  return <><header className="page-heading"><div><p className="section-label">Platform administration</p><h1>Access and detector baselines.</h1></div></header>
    {(error || usersError || fleetError || modelsError) && <Alert>{error || usersError || fleetError || modelsError}</Alert>}{notice && <p className="notice" role="status">{notice}</p>}
    <section className="surface"><h2>Console accounts</h2><div className="table-wrap"><table><thead><tr><th>Username</th><th>Role</th><th>Access</th><th>Action</th></tr></thead><tbody>{users?.map(user => <tr key={user.id}><td>{user.username}{user.id === account.id ? ' (you)' : ''}</td><td><select aria-label={'Role for ' + user.username} disabled={busy || user.id === account.id} value={user.role} onChange={event => update(user, event.target.value as Role, user.enabled)}><option value="SUPERADMIN">SuperAdmin</option><option value="MODERATOR">Moderator</option><option value="ANALYST">Analyst / Observer</option></select></td><td><Badge>{user.enabled ? 'Enabled' : 'Disabled'}</Badge></td><td><Button variant="outline" disabled={busy || user.id === account.id} onClick={() => update(user, user.role, !user.enabled)}>{user.enabled ? 'Disable account' : 'Enable account'}</Button></td></tr>)}</tbody></table></div></section>
    <section className="surface"><h2>Create an account</h2><form onSubmit={add}><FieldGroup><div className="field-columns">
      <Field><FieldLabel htmlFor="new-user">Username</FieldLabel><input id="new-user" name="username" required pattern="[a-zA-Z0-9._-]{3,64}" autoComplete="off" /></Field>
      <Field><FieldLabel htmlFor="new-password">Password · 16–72 bytes</FieldLabel><input id="new-password" name="password" required minLength={16} maxLength={72} type="password" autoComplete="new-password" /></Field>
      <Field><FieldLabel htmlFor="new-role">Role</FieldLabel><select id="new-role" name="role" defaultValue="ANALYST"><option value="ANALYST">Analyst / Observer</option><option value="MODERATOR">Moderator</option><option value="SUPERADMIN">SuperAdmin</option></select></Field>
    </div><Button disabled={busy} type="submit">Create account</Button></FieldGroup></form></section>
    <details className="surface"><summary>Reset an account password</summary><form onSubmit={password}><FieldGroup><Field><FieldLabel htmlFor="password-user">Account</FieldLabel><select id="password-user" name="userId">{users?.map(user => <option key={user.id} value={user.id}>{user.username}</option>)}</select></Field><Field><FieldLabel htmlFor="reset-password">New password</FieldLabel><input id="reset-password" name="password" type="password" required minLength={16} maxLength={72} autoComplete="new-password" /></Field><Button type="submit" disabled={busy}>Update password</Button></FieldGroup></form></details>
    <section className="surface"><h2>Per-player baselines</h2><p className="muted">Train a statistical reference from an interval reviewed as legitimate. Each player and world needs at least 100 valuable ore events. Untrained players retain the default 75% occlusion threshold.</p>
      <form onSubmit={train}><FieldGroup><Field><FieldLabel htmlFor="training-instance">Instance</FieldLabel><select id="training-instance" name="instanceId" required>{instances?.map(instance => <option key={instance.id} value={instance.id}>{instance.name}</option>)}</select></Field><div className="field-columns"><Field><FieldLabel htmlFor="training-from">From (local time)</FieldLabel><input id="training-from" type="datetime-local" name="from" required /></Field><Field><FieldLabel htmlFor="training-until">Until (local time)</FieldLabel><input id="training-until" type="datetime-local" name="until" required /></Field></div>
        <label className="check"><input type="checkbox" name="reviewed" required />I reviewed this interval as legitimate gameplay.</label><Button type="submit" disabled={busy || !instances?.length}>Train baselines</Button></FieldGroup></form>
      <div className="table-wrap"><table><thead><tr><th>Instance / world</th><th>Player</th><th>Samples</th><th>Occluded</th><th>Trained</th></tr></thead><tbody>{models?.map(model => <tr key={model.instanceId + model.playerId + model.world}><td>{model.instanceId} / {model.world}</td><td className="data">{model.playerId}</td><td>{model.samples}</td><td>{model.occluded}</td><td>{new Date(model.trainedAt).toLocaleString()}</td></tr>)}</tbody></table></div>
    </section>
  </>
}
