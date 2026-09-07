import 'server-only'
import { cache } from 'react'
import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import type { Account } from './types'

export const requireAccount = cache(async (): Promise<Account> => {
  const cookieStore = await cookies()
  const cookie = cookieStore.get('caenis_session')

  console.log('>>> [Next.js session.ts] caenis_session cookie:', cookie ? 'FOUND (length ' + cookie.value.length + ')' : 'MISSING!')

  if (!cookie) {
    console.log('>>> [Next.js session.ts] 307 Redirecting: Cookie is missing!')
    redirect('/login')
  }

  const backendUrl = (process.env.CAENIS_BACKEND_URL || 'http://127.0.0.1:8080') + '/api/v1/auth/me'
  console.log('>>> [Next.js session.ts] Calling backend:', backendUrl)

  const response = await fetch(backendUrl, {
    headers: { Cookie: 'caenis_session=' + cookie.value },
    cache: 'no-store',
  })

  console.log('>>> [Next.js session.ts] Backend returned status:', response.status)

  if (response.status === 401 || response.status === 403) {
    const errorBody = await response.text().catch(() => '')
    console.log('>>> [Next.js session.ts] 307 Redirecting: Backend returned ' + response.status + ' | Body: ' + errorBody)
    redirect('/login')
  }

  if (!response.ok) {
    throw new Error('The authentication service is unavailable.')
  }

  return response.json()
})