'use client'
import { useEffect, useRef, useState } from 'react'
import { csrfToken } from './api'
import type { CommandEvent } from './types'

// One bounded STOMP connection belongs to the console shell. Events only invalidate REST snapshots.
export function useLive(onChange: () => void, onCommand: (event: CommandEvent) => void, commandsAllowed = true) {
  const [online, setOnline] = useState(false)
  const callbacks = useRef({ onChange, onCommand })
  useEffect(() => { callbacks.current = { onChange, onCommand } }, [onChange, onCommand])
  useEffect(() => {
    let socket: WebSocket | null = null
    let retry: ReturnType<typeof setTimeout> | undefined
    let closed = false
    let delay = 1000
    const open = async () => {
      try {
        const token = await csrfToken()
        if (closed) return
        const origin = process.env.NEXT_PUBLIC_WS_ORIGIN || window.location.origin
        socket = new WebSocket(origin.replace(/^http/, 'ws') + '/ws')
        let buffer = ''
        socket.onopen = () => socket?.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\nX-XSRF-TOKEN:' + token + '\n\n\0')
        socket.onmessage = ({ data }) => {
          buffer += String(data)
          if (buffer.length > 524288) { socket?.close(); return }
          let end: number
          while ((end = buffer.indexOf('\0')) >= 0) {
            const frame = buffer.slice(0, end).replace(/^\n+/, '')
            buffer = buffer.slice(end + 1)
            const split = frame.indexOf('\n\n')
            const head = frame.slice(0, split)
            const body = frame.slice(split + 2)
            if (head.startsWith('CONNECTED')) {
              setOnline(true); delay = 1000
              socket?.send('SUBSCRIBE\nid:changes\ndestination:/topic/changes\nack:auto\n\n\0')
              if (commandsAllowed) socket?.send('SUBSCRIBE\nid:commands\ndestination:/user/queue/commands\nack:auto\n\n\0')
              callbacks.current.onChange()
            } else if (head.startsWith('MESSAGE')) {
              try {
                const payload = JSON.parse(body)
                if (head.includes('subscription:commands')) callbacks.current.onCommand(payload)
                else {
                  window.dispatchEvent(new CustomEvent('caenis:changes', { detail: payload.kinds ?? [payload.kind] }))
                  callbacks.current.onChange()
                }
              } catch { /* Ignore malformed frames; bounded REST snapshots remain available. */ }
            } else if (head.startsWith('ERROR')) socket?.close()
          }
        }
        socket.onerror = () => socket?.close()
        socket.onclose = () => {
          setOnline(false)
          if (!closed) { retry = setTimeout(open, delay); delay = Math.min(delay * 2, 30000) }
        }
      } catch {
        if (!closed) retry = setTimeout(open, 10000)
      }
    }
    void open()
    return () => { closed = true; clearTimeout(retry); socket?.close() }
  }, [commandsAllowed])
  return online
}
