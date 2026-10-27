import type { ReactNode } from 'react'

export type CardAccent = 'violet' | 'lavender' | 'blush' | 'deep'

interface DashboardCardProps {
  title: string
  accent: CardAccent
  icon: ReactNode
  children: ReactNode
}

/**
 * One glass dashboard tile: a hairline-edged translucent panel, a colored
 * accent icon and a spacious value area. Layout responsibility stays here;
 * the semantics of the value live with the caller.
 */
export default function DashboardCard({ title, accent, icon, children }: DashboardCardProps) {
  return (
    <section className={`dashboard-card accent-${accent}`}>
      <header className="card-header">
        <span className="card-icon">{icon}</span>
        <span className="card-title">{title}</span>
      </header>
      <div className="card-body">{children}</div>
    </section>
  )
}