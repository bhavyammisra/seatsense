import { useEffect, useState } from 'react'
import { LogoMark } from './Logo'
import './brand.css'

const KEY = 'seatsense.splash.seen'

function shouldShow(): boolean {
  try {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false
    return sessionStorage.getItem(KEY) !== '1'
  } catch {
    return false
  }
}

// Opening screen on the homepage: the seats light up one by one, the gold
// "your seat" last, then the curtain lifts. Once per browser session; a tap skips it.
export default function Splash() {
  const [phase, setPhase] = useState<'show' | 'leave' | 'gone'>(() => (shouldShow() ? 'show' : 'gone'))

  useEffect(() => {
    if (phase === 'gone') return
    try {
      sessionStorage.setItem(KEY, '1')
    } catch {
      // private mode: the splash just plays again next time
    }
    const leave = window.setTimeout(() => setPhase('leave'), phase === 'show' ? 1750 : 0)
    return () => window.clearTimeout(leave)
  }, [phase])

  useEffect(() => {
    if (phase !== 'leave') return
    const gone = window.setTimeout(() => setPhase('gone'), 700)
    return () => window.clearTimeout(gone)
  }, [phase])

  if (phase === 'gone') return null
  return (
    <div className="splash" data-phase={phase} onClick={() => setPhase('leave')} role="presentation">
      <div className="splash-inner">
        <LogoMark size={168} animated />
        <p className="splash-name">SeatSense</p>
        <p className="splash-place">Nava Nalanda Central Library</p>
      </div>
    </div>
  )
}
