export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message) }
}
let csrf: string | null = null
let csrfRequest: Promise<string> | null = null
export async function csrfToken(): Promise<string> {
  // 1. Always check for the active XSRF-TOKEN cookie first
  if (typeof document !== 'undefined') {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
    if (match && match[1]) {
      csrf = decodeURIComponent(match[1])
      return csrf
    }
  }

  // 2. If cookie isn't present yet, fetch it from the backend
  if (!csrfRequest) {
    csrfRequest = fetch('/api/v1/auth/csrf', { cache: 'no-store', credentials: 'same-origin' })
        .then(async response => {
          if (!response.ok) throw new ApiError('The authentication service is unavailable.', response.status)
          const value = await response.json() as { token: string }
          csrf = value.token
          return value.token
        })
        .finally(() => { csrfRequest = null })
  }

  return csrfRequest
}


export function clearCsrf() {
  csrf = null
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const method = init.method ?? 'GET'
  if (method !== 'GET' && method !== 'HEAD') {
    headers.set('X-XSRF-TOKEN', await csrfToken())
    headers.set('Content-Type', 'application/json')
  }
  const response = await fetch('/api/v1' + path, { ...init, headers, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    console.error(`[API ERROR] Endpoint "${path}" returned status ${response.status}:`, body)

    if (response.status === 401 && path !== '/auth/login' && typeof window !== 'undefined') {
      console.error(`[AUTH] 401 encountered on ${path} — redirect temporarily disabled for debugging`)
      // window.location.assign('/login') // <-- TEMPORARILY COMMENT THIS OUT
    }
    throw new ApiError(body?.message ?? (response.status === 403 ? 'Your role cannot perform this action.' : 'The service could not complete this request.'), response.status)
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
export function message(error: unknown) { return error instanceof Error ? error.message : 'The operation could not be completed.' }
