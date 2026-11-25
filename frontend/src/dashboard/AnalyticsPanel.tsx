import { useEffect, useState } from 'react'
import { fetchAnalytics, type Analytics } from '../shared/api/adminApi'

// Librarian analytics (WORKPLAN B4.3): busiest hours, most-used seats, and how often the public
// site showed FULL. Counted from stored rows only; empty means "no data yet", never a guess.

const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
const dayFormat = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })

function hourLabel(h: number): string {
  return `${h % 12 === 0 ? 12 : h % 12}${h < 12 ? 'am' : 'pm'}`
}

function minutesLabel(m: number): string {
  return m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60} min`
}

export default function AnalyticsPanel({ refreshKey }: { refreshKey: number }) {
  const [data, setData] = useState<Analytics | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchAnalytics()
      .then((a) => !cancelled && setData(a))
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [refreshKey])

  if (failed) return null
  if (!data) {
    return (
      <section className="dash-panel">
        <h2>Analytics</h2>
        <p className="dash-muted">Loading…</p>
      </section>
    )
  }

  const hours = data.heatmap.length
    ? Array.from(
        { length: Math.max(...data.heatmap.map((c) => c.hour)) - Math.min(...data.heatmap.map((c) => c.hour)) + 1 },
        (_, i) => Math.min(...data.heatmap.map((c) => c.hour)) + i,
      )
    : []
  const cell = (weekday: number, hour: number) =>
    data.heatmap.find((c) => c.weekday === weekday && c.hour === hour)?.occupancy

  return (
    <section className="dash-panel dash-analytics" aria-label="Analytics">
      <h2>Analytics</h2>

      <div className="dash-analytics-block">
        <h3>Busiest hours</h3>
        {hours.length === 0 ? (
          <p className="dash-muted">No history yet. The library is measured every 5 minutes; this fills in over the coming days.</p>
        ) : (
          <>
            <p className="dash-muted">
              Share of seats taken, by weekday and hour, from {data.daysOfData} {data.daysOfData === 1 ? 'day' : 'days'} of{' '}
              {data.sample ? <strong>sample data</strong> : 'measurements'}.
            </p>
            <div className="dash-heat-scroll">
              <table className="dash-heat">
                <thead>
                  <tr>
                    <td />
                    {hours.map((h) => (
                      <th key={h} scope="col">
                        {hourLabel(h)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {WEEKDAYS.map((name, i) => (
                    <tr key={name}>
                      <th scope="row">{name.slice(0, 3)}</th>
                      {hours.map((h) => {
                        const value = cell(i + 1, h)
                        if (value === undefined) {
                          return (
                            <td key={h} className="dash-heat-empty" title={`${name} ${hourLabel(h)}: no data`}>
                              <span className="visually-hidden">no data</span>
                            </td>
                          )
                        }
                        const pct = Math.round(value * 100)
                        return (
                          <td
                            key={h}
                            title={`${name} ${hourLabel(h)}: ${pct}% of seats taken`}
                            data-strong={pct >= 70 || undefined}
                            style={{ background: `color-mix(in oklab, var(--accent) ${pct}%, var(--surface))` }}
                          >
                            {pct}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="dash-heat-legend" aria-hidden="true">
              <span>0%</span>
              <span className="dash-heat-ramp" />
              <span>100% taken</span>
              <span className="dash-heat-empty-key" />
              <span>no data</span>
            </div>
          </>
        )}
      </div>

      <div className="dash-analytics-row">
        <div className="dash-analytics-block">
          <h3>Most-used seats, last 30 days</h3>
          {data.topSeats.length === 0 ? (
            <p className="dash-muted">No seats used in the last 30 days.</p>
          ) : (
            <>
              <table className="dash-table">
                <thead>
                  <tr>
                    <th scope="col">Seat</th>
                    <th scope="col">Times taken</th>
                    <th scope="col">Time used</th>
                  </tr>
                </thead>
                <tbody>
                  {data.topSeats.map((s) => (
                    <tr key={s.code}>
                      <th scope="row" className="mono">
                        {s.code}
                      </th>
                      <td>{s.sessions}</td>
                      <td>{minutesLabel(s.minutes)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {data.seatsSample && <p className="dash-muted">Includes sessions of sample students.</p>}
            </>
          )}
        </div>

        <div className="dash-analytics-block">
          <h3>Saw the library full</h3>
          <p className="dash-big-number">{data.sawFullTotal}</p>
          <p className="dash-muted">
            times in the last 7 days, someone opened the public site while every seat was taken.
          </p>
          <ul className="dash-days">
            {data.sawFull.map((d) => (
              <li key={d.date}>
                <span>{dayFormat.format(new Date(`${d.date}T12:00:00`))}</span>
                <span>{d.count}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
