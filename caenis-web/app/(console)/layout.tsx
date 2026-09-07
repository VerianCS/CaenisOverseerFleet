import ConsoleShell from '@/components/ConsoleShell'
import { requireAccount } from '@/lib/session'
export default async function Layout({ children }: { children: React.ReactNode }) {
  return <ConsoleShell account={await requireAccount()}>{children}</ConsoleShell>
}
