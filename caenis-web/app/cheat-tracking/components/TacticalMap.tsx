'use client';

import React, { useRef, useEffect, useState, useCallback } from 'react';
import { MapMarker } from '../types/deck';
import { ZoomIn, ZoomOut, Crosshair } from 'lucide-react';

interface TacticalMapProps {
  markers: MapMarker[];
  onSelectMarker?: (marker: MapMarker) => void;
}

export const TacticalMap: React.FC<TacticalMapProps> = ({ markers, onSelectMarker }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  
  // Viewport State
  const [zoom, setZoom] = useState<number>(0.8);
  const [pan, setPan] = useState<{ x: number; z: number }>({ x: 0, z: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [hoveredMarker, setHoveredMarker] = useState<MapMarker | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;

    // 1. Draw Grid Lines
    ctx.strokeStyle = '#1f3247';
    ctx.lineWidth = 1;
    const gridSize = 100 * zoom;
    const offsetX = (centerX + pan.x * zoom) % gridSize;
    const offsetZ = (centerY + pan.z * zoom) % gridSize;

    for (let x = offsetX; x < canvas.width; x += gridSize) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, canvas.height);
      ctx.stroke();
    }
    for (let y = offsetZ; y < canvas.height; y += gridSize) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(canvas.width, y);
      ctx.stroke();
    }

    // 2. Draw World Origin (0,0) as a small guardian sigil
    const originX = centerX + pan.x * zoom;
    const originZ = centerY + pan.z * zoom;
    ctx.strokeStyle = '#c9a55c';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(originX, originZ, 9, 0, Math.PI * 2);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(originX, originZ - 6);
    ctx.lineTo(originX, originZ + 6);
    ctx.moveTo(originX - 4, originZ - 6);
    ctx.lineTo(originX - 4, originZ - 2);
    ctx.moveTo(originX + 4, originZ - 6);
    ctx.lineTo(originX + 4, originZ - 2);
    ctx.stroke();

    // 3. Render Mining Markers
    markers.forEach((m) => {
      const screenX = centerX + (m.x + pan.x) * zoom;
      const screenZ = centerY + (m.z + pan.z) * zoom;

      // Cull markers outside viewport
      if (screenX < -20 || screenX > canvas.width + 20 || screenZ < -20 || screenZ > canvas.height + 20) {
        return;
      }

      const isPrecious = m.blockType.includes('DIAMOND') || m.blockType.includes('DEBRIS');
      const radius = isPrecious ? 5 : 3;

      if (!m.isExposed) {
        // Occluded / blind extraction: fracture crimson
        ctx.fillStyle = '#e2543a';
        ctx.shadowColor = '#e2543a';
        ctx.shadowBlur = 8;
      } else {
        // Natural cave exposure: tidewake teal
        ctx.fillStyle = '#48d0be';
        ctx.shadowColor = '#48d0be';
        ctx.shadowBlur = 4;
      }

      ctx.beginPath();
      ctx.arc(screenX, screenZ, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0; // Reset shadow

      if (isPrecious) {
        ctx.strokeStyle = '#c9a55c';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(screenX, screenZ, radius + 3, 0, Math.PI * 2);
        ctx.stroke();
      }
    });
  }, [markers, zoom, pan]);

  // Handle Resize
  useEffect(() => {
    const handleResize = () => {
      if (canvasRef.current && canvasRef.current.parentElement) {
        canvasRef.current.width = canvasRef.current.parentElement.clientWidth;
        canvasRef.current.height = canvasRef.current.parentElement.clientHeight;
        draw();
      }
    };
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [draw]);

  useEffect(() => {
    draw();
  }, [draw]);

  // Mouse Controls
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    setDragStart({ x: e.clientX, y: e.clientY });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!canvasRef.current) return;

    if (isDragging) {
      const deltaX = (e.clientX - dragStart.x) / zoom;
      const deltaZ = (e.clientY - dragStart.y) / zoom;
      setPan((prev) => ({ x: prev.x + deltaX, z: prev.z + deltaZ }));
      setDragStart({ x: e.clientX, y: e.clientY });
      return;
    }

    // Hover Detection
    const rect = canvasRef.current.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseZ = e.clientY - rect.top;
    const centerX = canvasRef.current.width / 2;
    const centerY = canvasRef.current.height / 2;

    const hit = markers.find((m) => {
      const screenX = centerX + (m.x + pan.x) * zoom;
      const screenZ = centerY + (m.z + pan.z) * zoom;
      const dist = Math.hypot(screenX - mouseX, screenZ - mouseZ);
      return dist < 8;
    });

    setHoveredMarker(hit || null);
  };

  const handleMouseUp = () => setIsDragging(false);

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.15 : 0.85;
    setZoom((prev) => Math.min(Math.max(prev * zoomFactor, 0.1), 8.0));
  };

  const resetView = () => {
    setPan({ x: 0, z: 0 });
    setZoom(0.8);
  };

  return (
    <div className="relative h-full w-full select-none overflow-hidden rounded-sm border border-depth-line bg-depth">
      <canvas
        ref={canvasRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        className="h-full w-full cursor-crosshair"
      />

      {/* Ambient depth vignette */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'radial-gradient(ellipse at center, transparent 45%, rgba(7,12,20,0.55) 100%)',
        }}
      />

      {/* Floating Tactical Overlay Controls */}
      <div className="absolute right-4 top-4 flex flex-col gap-2 rounded-sm border border-depth-line bg-depth-raised/90 p-1.5 backdrop-blur">
        <button
          onClick={() => setZoom((z) => Math.min(z * 1.25, 8))}
          className="rounded-sm p-1.5 text-current transition-colors hover:bg-depth-line hover:text-tidewake"
          title="Zoom In"
        >
          <ZoomIn size={18} />
        </button>
        <button
          onClick={() => setZoom((z) => Math.max(z * 0.75, 0.1))}
          className="rounded-sm p-1.5 text-current transition-colors hover:bg-depth-line hover:text-tidewake"
          title="Zoom Out"
        >
          <ZoomOut size={18} />
        </button>
        <button
          onClick={resetView}
          className="rounded-sm p-1.5 text-current transition-colors hover:bg-depth-line hover:text-adamant"
          title="Center Origin (0,0)"
        >
          <Crosshair size={18} />
        </button>
      </div>

      {/* Coordinate & Zoom Telemetry */}
      <div className="absolute bottom-4 left-4 flex gap-4 rounded-sm border border-depth-line bg-depth-raised/90 px-3 py-1.5 font-data text-xs text-current backdrop-blur">
        <span>cam: [{(-pan.x).toFixed(0)}, {(-pan.z).toFixed(0)}]</span>
        <span>mag: {(zoom * 100).toFixed(0)}%</span>
        <span>nodes: {markers.length}</span>
      </div>

      {/* Target Marker Tooltip */}
      {hoveredMarker && (
        <div className="pointer-events-none absolute left-4 top-4 rounded-sm border border-depth-line bg-depth-raised/95 p-3 font-data text-xs text-foam shadow-xl">
          <div className="mb-1 font-semibold text-foam">{hoveredMarker.playerName}</div>
          <div className="text-current">
            target: <span className="text-tidewake">{hoveredMarker.blockType}</span>
          </div>
          <div className="text-current">
            position: [{hoveredMarker.x}, {hoveredMarker.y}, {hoveredMarker.z}]
          </div>
          <div className="mt-1 text-current">
            status:{' '}
            {hoveredMarker.isExposed ? (
              <span className="text-tidewake">exposed cave</span>
            ) : (
              <span className="font-semibold text-fracture">unexposed / occluded</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
};