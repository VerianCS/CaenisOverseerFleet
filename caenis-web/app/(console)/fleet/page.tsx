import { redirect } from 'next/navigation'
import { requireAccount } from '@/lib/session'
import FleetView from '@/components/FleetView'
export default async function Page() {
  if ((await requireAccount()).role === 'ANALYST') redirect('/tactical')
  return <FleetView />
}
