'use client'
import { useEffect, useRef, useState } from 'react'
import { ZoomIn, ZoomOut, Crosshair } from 'lucide-react'
import type { MapMarker } from '@/app/cheat-tracking/types/deck'
import { Button } from './ui/button'

export interface Viewport { x: number; z: number; scale: number }
export default function TacticalCanvas({ markers, view, onView, onSelect }: { markers: MapMarker[]; view: Viewport; onView: (view: Viewport) => void; onSelect: (marker: MapMarker) => void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [size, setSize] = useState({ width: 800, height: 560 })
  const drag = useRef<{ x: number; y: number; moved: boolean } | null>(null)
  useEffect(() => {
    const element = canvas.current
    if (!element) return
    const observer = new ResizeObserver(entries => {
      const rect = entries[0].contentRect
      setSize({ width: Math.max(1, rect.width), height: Math.max(1, rect.height) })
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    const element = canvas.current, context = element?.getContext('2d')
    if (!element || !context) return
    const ratio = Math.min(window.devicePixelRatio || 1, 2)
    element.width = Math.round(size.width * ratio); element.height = Math.round(size.height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    context.clearRect(0, 0, size.width, size.height)
    const step = (view.scale < .1 ? 1024 : view.scale < .5 ? 256 : 64) * view.scale
    const offsetX = ((size.width / 2 - view.x * view.scale) % step + step) % step
    const offsetZ = ((size.height / 2 - view.z * view.scale) % step + step) % step
    context.strokeStyle = '#253a39'; context.lineWidth = 1
    context.beginPath()
    for (let x = offsetX; x < size.width; x += step) { context.moveTo(x, 0); context.lineTo(x, size.height) }
    for (let z = offsetZ; z < size.height; z += step) { context.moveTo(0, z); context.lineTo(size.width, z) }
    context.stroke()
    for (const marker of markers) {
      const x = size.width / 2 + (marker.x - view.x) * view.scale
      const z = size.height / 2 + (marker.z - view.z) * view.scale
      if (x < -12 || z < -12 || x > size.width + 12 || z > size.height + 12) continue
      const cluster = (marker.count ?? 1) > 1
      const radius = cluster ? Math.min(12, 4 + Math.log2(marker.count ?? 1)) : 4
      context.fillStyle = marker.isExposed ? '#48d0be' : '#e97870'
      context.beginPath(); context.arc(x, z, radius, 0, Math.PI * 2); context.fill()
      if (/DIAMOND|DEBRIS/.test(marker.blockType)) { context.strokeStyle = '#d9b96e'; context.beginPath(); context.arc(x, z, radius + 4, 0, Math.PI * 2); context.stroke() }
      if (cluster) { context.fillStyle = '#101719'; context.font = '11px monospace'; context.textAlign = 'center'; context.fillText(String(marker.count), x, z + 4) }
    }
    context.fillStyle = '#b7c9c4'; context.font = '12px monospace'; context.textAlign = 'left'
    context.fillText('X ' + Math.round(view.x) + '   Z ' + Math.round(view.z), 16, size.height - 18)
  }, [markers, view, size])
  function zoom(factor: number) { onView({ ...view, scale: Math.max(.015, Math.min(5, view.scale * factor)) }) }
  function fit() {
    if (!markers.length) { onView({ x: 0, z: 0, scale: .4 }); return }
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity
    for (const marker of markers) { minX = Math.min(minX, marker.x); maxX = Math.max(maxX, marker.x); minZ = Math.min(minZ, marker.z); maxZ = Math.max(maxZ, marker.z) }
    onView({ x: (minX + maxX) / 2, z: (minZ + maxZ) / 2, scale: Math.max(.015, Math.min(2, (size.width - 80) / Math.max(1, maxX - minX), (size.height - 80) / Math.max(1, maxZ - minZ))) })
  }
  return <div className="tactical-canvas"><div className="map-toolbar"><Button size="icon" variant="outline" aria-label="Zoom in" onClick={() => zoom(1.5)}><ZoomIn /></Button><Button size="icon" variant="outline" aria-label="Zoom out" onClick={() => zoom(1 / 1.5)}><ZoomOut /></Button><Button size="icon" variant="outline" aria-label="Fit loaded events" onClick={fit}><Crosshair /></Button></div>
    <canvas ref={canvas} tabIndex={0} role="img" aria-label="Mining map. Drag to pan, use plus or minus to zoom, and arrow keys to pan. Events are also listed below."
      onKeyDown={event => {
        const distance = 100 / view.scale
        if (event.key === '+' || event.key === '=') zoom(1.5)
        else if (event.key === '-') zoom(1 / 1.5)
        else if (event.key.startsWith('Arrow')) { event.preventDefault(); onView({ ...view, x: view.x + (event.key === 'ArrowRight' ? distance : event.key === 'ArrowLeft' ? -distance : 0), z: view.z + (event.key === 'ArrowDown' ? distance : event.key === 'ArrowUp' ? -distance : 0) }) }
      }}
      onPointerDown={event => { event.currentTarget.setPointerCapture(event.pointerId); drag.current = { x: event.clientX, y: event.clientY, moved: false } }}
      onPointerMove={event => {
        if (!drag.current) return
        const dx = event.clientX - drag.current.x, dz = event.clientY - drag.current.y
        if (Math.abs(dx) + Math.abs(dz) > 2) drag.current.moved = true
        drag.current.x = event.clientX; drag.current.y = event.clientY
        onView({ ...view, x: Math.max(-30000000, Math.min(30000000, view.x - dx / view.scale)), z: Math.max(-30000000, Math.min(30000000, view.z - dz / view.scale)) })
      }}
      onPointerCancel={() => { drag.current = null }}
      onPointerUp={event => {
        const moved = drag.current?.moved; drag.current = null
        if (moved) return
        const rect = event.currentTarget.getBoundingClientRect()
        const x = view.x + (event.clientX - rect.left - size.width / 2) / view.scale
        const z = view.z + (event.clientY - rect.top - size.height / 2) / view.scale
        const marker = markers.find(marker => Math.hypot(marker.x - x, marker.z - z) * view.scale < 12)
        if (marker) onSelect(marker)
      }} />
    <div className="map-legend"><span><i className="exposed-dot" />Exposed</span><span><i className="occluded-dot" />Occluded</span><span><i className="rare-dot" />Rare ore</span><span>Clusters at wider scales</span></div>
  </div>
}
