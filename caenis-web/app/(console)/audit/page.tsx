import { redirect } from 'next/navigation'
import { requireAccount } from '@/lib/session'
import AuditView from '@/components/AuditView'
export default async function Page() {
  if ((await requireAccount()).role === 'ANALYST') redirect('/tactical')
  return <AuditView />
}
