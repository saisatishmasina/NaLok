/** UI-facing formatting helpers: durations, dates, week labels. */

export function formatDuration(seconds: number): string {
  if (seconds < 60) {
    return seconds > 0 ? `${seconds}s` : '0s'
  }
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const remMinutes = minutes % 60
  if (hours === 0) {
    return `${minutes}m`
  }
  if (remMinutes === 0) {
    return `${hours}h`
  }
  return `${hours}h ${remMinutes}m`
}

export function formatShortDate(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  return new Date(iso).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
  })
}

export function formatTime(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  return new Date(iso).toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** The browser zone is the truthful default for weekly summaries. */
export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
