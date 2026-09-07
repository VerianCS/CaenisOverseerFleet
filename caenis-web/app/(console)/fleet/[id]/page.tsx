import { redirect } from 'next/navigation'
import { requireAccount } from '@/lib/session'
import NodeView from '@/components/NodeView'
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  if ((await requireAccount()).role === 'ANALYST') redirect('/tactical')
  const { id } = await params
  return <NodeView id={id} />
}
