import { NextResponse, type NextRequest } from 'next/server'
import { createHmac, timingSafeEqual } from 'node:crypto'

// Optimistic route gate. The core rechecks the session and current role at every API effect.
export function proxy(request: NextRequest) {
  const token = request.cookies.get('caenis_session')?.value
  const secret = process.env.CAENIS_SESSION_SECRET
  try {
    if (!token || !secret) throw new Error(`Session or secret missing (token: ${!!token}, secret: ${!!secret})`)
    const [header, payload, signature, extra] = token.split('.')
    if (extra || !header || !payload || !signature) throw new Error('Invalid session token format')
    const expected = createHmac('sha256', secret).update(header + '.' + payload).digest()
    const received = Buffer.from(signature, 'base64url')
    if (received.length !== expected.length || !timingSafeEqual(expected, received)) throw new Error('Signature rejected')
    const metadata = JSON.parse(Buffer.from(header, 'base64url').toString())
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString())
    if (metadata.alg !== 'HS256' || claims.iss !== 'caenis' || ![claims.aud].flat().includes('caenis-console') || claims.exp <= Date.now() / 1000) throw new Error('Expired or invalid claims')
    return NextResponse.next()
  } catch (err: any) {
    console.error('>>> [proxy.ts REJECTED]:', err.message)
    return NextResponse.redirect(new URL('/login', request.url))
  }
}
export const config = { matcher: ['/fleet/:path*', '/tactical/:path*', '/rcon/:path*', '/audit/:path*', '/settings/:path*'] }
