// The SeatSense mark: six seats around a library table, seen from above,
// with one seat lit gold for "your seat". The same drawing is the favicon.

type Props = {
  size?: number
  /** Light up the seats one by one (splash screen). */
  animated?: boolean
  /** Draw the wine tile behind the seats (favicon style). */
  tile?: boolean
  className?: string
}

const SEATS: [number, number][] = [
  [7, 7],
  [16.5, 7],
  [26, 7],
  [7, 26],
  [26, 26],
]

export function LogoMark({ size = 40, animated = false, tile = false, className }: Props) {
  return (
    <svg
      viewBox="0 0 40 40"
      width={size}
      height={size}
      className={['logo-mark', animated ? 'logo-mark-animated' : '', className ?? ''].join(' ').trim()}
      aria-hidden="true"
    >
      {tile && <rect width="40" height="40" rx="8" className="logo-tile" />}
      {SEATS.map(([x, y], i) => (
        <rect key={i} x={x} y={y} width="7" height="7" rx="1.5" className="logo-seat" style={{ '--i': i } as React.CSSProperties} />
      ))}
      <rect x="6" y="17.5" width="28" height="5" rx="1.2" className="logo-table" />
      <rect x="16.5" y="26" width="7" height="7" rx="1.5" className="logo-yours" />
    </svg>
  )
}

/** Mark plus the serif wordmark, as used in headers. */
export function Logo({ place = 'Nava Nalanda Library', size = 38 }: { place?: string; size?: number }) {
  return (
    <span className="logo">
      <LogoMark size={size} />
      <span className="logo-words">
        <span className="logo-name">SeatSense</span>
        {place && <span className="logo-place">{place}</span>}
      </span>
    </span>
  )
}
