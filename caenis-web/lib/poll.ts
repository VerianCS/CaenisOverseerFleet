'use client'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api, message } from './api'

export function useResource<T>(path: string | null, interval = 5000) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const refreshAction = useRef<() => void>(() => {})
  const refresh = useCallback(() => refreshAction.current(), [])
  useEffect(() => {
    if (!path) { setData(null); setLoading(false); refreshAction.current = () => {}; return }
    let disposed = false, inFlight = false
    let timer: ReturnType<typeof setTimeout>
    const controller = new AbortController()
    const load = async () => {
      if (inFlight || disposed) return
      inFlight = true
      clearTimeout(timer)
      try {
        const value = await api<T>(path, { signal: controller.signal })
        if (!disposed) { setData(value); setError('') }
      } catch (error) {
        if (!disposed) setError(message(error))
      } finally {
        inFlight = false
        if (!disposed) { setLoading(false); timer = setTimeout(load, interval) }
      }
    }
    const invalidated = (event: Event) => {
      if (interval > 60000) return
      const kinds = (event as CustomEvent<string[]>).detail
      if ((kinds.includes('fleet') && path.startsWith('/fleet') && !path.includes('/history')) ||
          (kinds.includes('tactical') && path.startsWith('/deck'))) void load()
    }
    refreshAction.current = () => { void load() }
    setData(null); setLoading(true); setError('')
    window.addEventListener('caenis:changes', invalidated)
    void load()
    return () => {
      disposed = true; controller.abort(); clearTimeout(timer)
      window.removeEventListener('caenis:changes', invalidated)
      refreshAction.current = () => {}
    }
  }, [path, interval])
  return { data, error, loading, refresh }
}
