import Link from 'next/link'
import PipelineDemo from '@/components/PipelineDemo'

/*
THESIS: Make the separation between game ticks and fleet analysis visible.
OWN-WORLD: Existing mineral green, charcoal, teal telemetry and gold ore; readable operating typography.
STORY: Understand capture, inspect a six-face sample, enter the authenticated console.
FIRST VIEWPORT: Product name and access link above a concise thesis and an annotated processing path.
FORM: Extend the incumbent Caenis identity with the architecture and sandbox prescribed by the supplied brief.
*/
export default function Home() {
  return <div className="public-page"><header className="public-nav"><Link className="wordmark" href="/">CÆNIS<span>OVERSEER</span></Link><nav aria-label="Public navigation"><a href="#pipeline">Architecture</a><a href="#sandbox">Try the simulation</a><Link href="/login" className="access-link">Open console ↗</Link></nav></header>
    <main><section className="public-hero"><p className="section-label">PaperMC fleet operations & spatial telemetry</p><h1>Keep the tick<br />for the game.</h1><div className="hero-bottom"><p>Observe mining patterns outside the server loop. Follow the health of your network. Act from one accountable console.</p><Link className="primary-link" href="/login">Enter fleet console <span>↗</span></Link></div>
      <div className="pipeline-path" aria-label="Paper captures an event, a queue buffers it, the core analyzes it, and the console shows its evidence">
        <div><span className="path-dot" /><strong>Paper agent</strong><small>Capture & sample</small></div><span aria-hidden="true">→</span>
        <div><strong>Buffered transport</strong><small>Signed event batches</small></div><span aria-hidden="true">→</span>
        <div><strong>Analytical core</strong><small>Physics & spatial history</small></div><span aria-hidden="true">→</span>
        <div><strong>Fleet console</strong><small>Observe · review · act</small></div>
      </div>
    </section>
    <PipelineDemo />
    <section className="public-operations"><div><p className="section-label">One operational picture</p><h2>From a signal<br />to an informed action.</h2><p>Telemetry is evidence. Operators can inspect a world, compare a player’s history and issue a command with an attributable result.</p></div>
      <dl><div><dt>Fleet health</dt><dd>TPS, tick time, memory, players and recent performance history, scoped to each instance.</dd></div><div><dt>Tactical observation</dt><dd>A coordinate canvas and searchable incident feed keep locations and detector diagnostics together.</dd></div><div><dt>Controlled intervention</dt><dd>Role-aware commands, temporary containment and an append-only record of requests and outcomes.</dd></div></dl>
    </section><section className="public-close"><h2>Take the watch.</h2><p>Connect your first instance from the fleet console.</p><Link className="primary-link" href="/login">Sign in to Caenis ↗</Link></section></main>
    <footer className="public-footer"><span>Caenis Overseer & Fleet Console</span><span>Built for PaperMC operators.</span></footer>
  </div>
}
