'use client'
import { useEffect, useRef, useState } from 'react'
import { Button } from './ui/button'

const faces = ['X−', 'X+', 'Y−', 'Y+', 'Z−', 'Z+']
export default function PipelineDemo() {
  const [work, setWork] = useState(20)
  const [exposed, setExposed] = useState<boolean[]>([false, false, false, false, false, false])
  const canvas = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const context = canvas.current?.getContext('2d')
    if (!context) return
    context.clearRect(0, 0, 480, 280)
    const cells = 12, size = 28, left = 70, top = 0
    for (let row = 0; row < 10; row++) for (let col = 0; col < cells; col++) {
      const center = row === 4 && col === 5
      const open = (row === 4 && col === 4 && exposed[0]) || (row === 4 && col === 6 && exposed[1]) || (row === 3 && col === 5 && exposed[2]) || (row === 5 && col === 5 && exposed[3])
      context.fillStyle = center ? '#d9b96e' : open ? '#101719' : (row + col) % 3 ? '#31413f' : '#3e514b'
      context.fillRect(left + col * size, top + row * size, size - 2, size - 2)
      if (center) {
        context.strokeStyle = exposed.some(Boolean) ? '#48d0be' : '#e97870'; context.lineWidth = 3
        context.strokeRect(left + col * size - 4, top + row * size - 4, size + 6, size + 6)
      }
    }
  }, [exposed])
  return <div className="explainer-grid">
    <section id="pipeline"><h2>Give every tick room to breathe.</h2><p>Paper has a 50 ms tick budget at 20 TPS. Caenis captures the event and sends analytical work to a separate service.</p>
      <label htmlFor="analysis-work">Illustrative analytical workload <strong>{work} ms</strong></label><input id="analysis-work" type="range" min="0" max="40" value={work} onChange={event => setWork(Number(event.target.value))} />
      <div className="tick-example"><span>Tick-bound analysis</span><div className="tick-track"><i style={{ width: '58%' }} /><b style={{ width: work / 50 * 100 + '%' }} /></div><small>{29 + work} ms total {29 + work > 50 ? '· exceeds the tick budget' : '· within this example’s budget'}</small></div>
      <div className="tick-example"><span>Separate analysis worker</span><div className="tick-track"><i style={{ width: '58%' }} /><em style={{ width: '2%' }} /></div><small>Game + event capture → queue → analytical core</small></div>
      <p className="muted text-sm">Illustration only. These durations explain the architecture and are not performance measurements.</p>
    </section>
    <section id="sandbox"><div className="section-heading"><h2>Six faces. A different story.</h2><span className="demo-label">Interactive simulation</span></div><p>Toggle a neighboring face to expose the ore. A fully hidden break becomes one observation in a player’s rolling history.</p>
      <canvas ref={canvas} width={480} height={280} className="ore-simulation" role="img" aria-label={exposed.some(Boolean) ? 'Ore with an exposed face' : 'Ore surrounded by opaque blocks'} />
      <div className="face-controls" role="group" aria-label="Exposed block faces">{faces.map((face, index) => <Button key={face} variant={exposed[index] ? 'default' : 'outline'} aria-pressed={exposed[index]} onClick={() => setExposed(previous => previous.map((value, i) => i === index ? !value : value))}>{face}</Button>)}</div>
      <p role="status" className="simulation-state">{exposed.some(Boolean) ? 'Exposed ore · a visible route exists' : 'Occluded ore · all six faces are closed'}</p><p className="muted text-sm">The canvas shows a cross-section. Z faces extend in front of and behind the ore. A single hidden break does not prove cheating.</p>
    </section>
  </div>
}
