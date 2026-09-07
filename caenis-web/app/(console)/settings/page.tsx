import { redirect } from 'next/navigation'
import { requireAccount } from '@/lib/session'
import SettingsView from '@/components/SettingsView'
export default async function Page() {
  const account = await requireAccount()
  if (account.role !== 'SUPERADMIN') redirect(account.role === 'ANALYST' ? '/tactical' : '/fleet')
  return <SettingsView />
}
