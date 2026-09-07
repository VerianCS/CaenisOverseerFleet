import type { ReactNode } from 'react'
export function Empty({ title, children }: { title: string; children: ReactNode }) {
  return <div className="empty"><h2>{title}</h2><div className="muted">{children}</div></div>
}
