'use client'
import { Button } from '@/components/ui/button'
export default function ErrorPage({ reset }: { reset: () => void }) {
  return <div className="empty" role="alert"><h1>The console is unavailable</h1><p>The backend could not complete this request. Try again after restoring the connection.</p><Button onClick={reset}>Try again</Button></div>
}
