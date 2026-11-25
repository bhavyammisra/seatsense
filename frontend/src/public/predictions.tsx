import { useEffect, useState } from 'react'
import { fetchPredictions, type FloorPrediction, type Predictions } from '../shared/api/layoutApi'

// Predictions (B4.1) for the public site and the entrance display. They change slowly,
// so they are fetched on load and every 5 minutes, not pushed live.

const REFRESH_MS = 5 * 60 * 1000

export function usePredictions(): Predictions | null {
  const [predictions, setPredictions] = useState<Predictions | null>(null)
  useEffect(() => {
    let cancelled = false
    const load = () =>
      fetchPredictions()
        .then((p) => !cancelled && setPredictions(p))
        .catch(() => {}) // no prediction is better than an error: the page works without it
    load()
    const timer = setInterval(load, REFRESH_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])
  return predictions
}

/** "19:30:00" as "7:30 pm". */
export function clockTime(hhmmss: string): string {
  const [h, m] = hhmmss.split(':').map(Number)
  const suffix = h < 12 ? 'am' : 'pm'
  return `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, '0')} ${suffix}`
}

/** The prediction for a floor that is short of seats, or null when there is nothing honest to say. */
export function predictionFor(
  predictions: Predictions | null,
  floorId: string,
  free: number,
): FloorPrediction | null {
  if (!predictions || free >= predictions.minFree) return null
  const p = predictions.floors.find((f) => f.floorId === floorId)
  return p?.freesUpAround ? p : null
}

/** "Based on 4 weeks of sample data." for the predictions on screen. */
export function predictionSource(shown: FloorPrediction[]): string | null {
  if (shown.length === 0) return null
  const weeks = Math.min(...shown.map((p) => p.weeksOfData))
  const sample = shown.some((p) => p.sample)
  return `Usual times are based on ${weeks} ${weeks === 1 ? 'week' : 'weeks'} of ${sample ? 'sample ' : ''}data.`
}
