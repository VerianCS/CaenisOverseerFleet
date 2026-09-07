'use client'
import Link from 'next/link'
import { useState } from 'react'
import { api, message } from '@/lib/api'
import type { Account } from '@/lib/types'
import { FieldGroup, Field, FieldLabel } from '@/components/ui/field'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
export default function Login() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    const data = new FormData(event.currentTarget)
    try {
      const account = await api<Account>('/auth/login', { method: 'POST', body: JSON.stringify({ username: data.get('username'), password: data.get('password') }) })
      window.location.assign(account.role === 'ANALYST' ? '/tactical' : '/fleet')
    } catch (error) { setError(message(error)); setBusy(false) }
  }
  return <main className="login-page"><div className="login-panel"><Link className="wordmark" href="/">CÆNIS</Link>
    <h1>Welcome to the watch.</h1><p className="muted">Sign in to your fleet operations console.</p>
    {error && <Alert>{error}</Alert>}<form onSubmit={submit}><FieldGroup>
      <Field><FieldLabel htmlFor="username">Username</FieldLabel><input id="username" name="username" autoComplete="username" required maxLength={64} /></Field>
      <Field><FieldLabel htmlFor="password">Password</FieldLabel><input id="password" name="password" type="password" autoComplete="current-password" required maxLength={72} /></Field>
      <Button type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Open console'}</Button>
    </FieldGroup></form><p className="muted text-sm">Use the administrator credentials created during installation. Ask your fleet administrator for a moderator or analyst account.</p>
  </div></main>
}
