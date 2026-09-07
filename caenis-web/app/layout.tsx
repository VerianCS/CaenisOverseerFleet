import type { Metadata, Viewport } from 'next'
import './globals.css'
export const metadata: Metadata = {
  title: { default: 'Caenis Overseer', template: '%s · Caenis' },
  description: 'Fleet operations and spatial mining telemetry for PaperMC networks.',
}
export const viewport: Viewport = { colorScheme: 'dark', themeColor: '#101719' }
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>
}
