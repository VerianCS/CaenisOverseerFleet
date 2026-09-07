import { redirect } from 'next/navigation'
import { requireAccount } from '@/lib/session'
import TerminalView from '@/components/TerminalView'
export default async function Page() {
  if ((await requireAccount()).role === 'ANALYST') redirect('/tactical')
  return <><header className="page-heading"><div><p className="section-label">Command gateway</p><h1>A direct line to your fleet.</h1><p className="muted">Choose a target and send an audited command.</p></div></header><TerminalView /></>
}
