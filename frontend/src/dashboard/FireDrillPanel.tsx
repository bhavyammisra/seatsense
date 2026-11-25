import { useEffect, useState } from 'react'
import { fetchFireDrill, type DrillPerson, type FireDrill } from '../shared/api/adminApi'

// Emergency headcount (WORKPLAN B4.4): everyone who should be in the building, by floor, to tick
// off at the assembly point. Students on a break scanned out at the gate, so they are listed apart.

const timeFormat = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

export default function FireDrillPanel({ refreshKey }: { refreshKey: string }) {
  const [open, setOpen] = useState(false)
  const [drill, setDrill] = useState<FireDrill | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    fetchFireDrill()
      .then((d) => {
        if (cancelled) return
        setDrill(d)
        setFailed(false)
      })
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
    }
  }, [open, refreshKey])

  function print() {
    document.body.classList.add('printing-drill')
    window.print()
    document.body.classList.remove('printing-drill')
  }

  return (
    <section className="dash-panel dash-drill" aria-label="Fire drill">
      <div className="dash-drill-head">
        <h2>Fire drill</h2>
        <div className="dash-drill-actions">
          {open && drill && (
            <button type="button" className="btn btn-ghost" onClick={print}>
              Print
            </button>
          )}
          <button type="button" className="btn btn-primary" onClick={() => setOpen(!open)} aria-expanded={open}>
            {open ? 'Hide the list' : 'Who is inside?'}
          </button>
        </div>
      </div>
      {!open ? (
        <p className="dash-muted">Names of everyone inside, floor by floor, to check off at the assembly point.</p>
      ) : failed ? (
        <p className="dash-error" role="alert">
          The list could not be loaded. Try again.
        </p>
      ) : !drill ? (
        <p className="dash-muted">Loading…</p>
      ) : (
        <>
          <p className="dash-drill-summary">
            <strong>{drill.inside}</strong> inside, <strong>{drill.onBreak}</strong> out on a break, as of{' '}
            {timeFormat.format(new Date(drill.at))}. The list updates as people scan in and out.
          </p>
          {drill.floors.map((f) => (
            <div key={f.id} className="dash-drill-floor">
              <h3>
                {f.name} <span className="dash-muted">({f.inside.length} inside)</span>
              </h3>
              {f.inside.length === 0 ? (
                <p className="dash-muted">Nobody.</p>
              ) : (
                <PeopleTable people={f.inside} />
              )}
              {f.onBreak.length > 0 && (
                <>
                  <h4>Out on a break, should not be inside ({f.onBreak.length})</h4>
                  <PeopleTable people={f.onBreak} onBreak />
                </>
              )}
            </div>
          ))}
          {drill.floors.some((f) => f.inside.some((p) => p.sample) || f.onBreak.some((p) => p.sample)) && (
            <p className="dash-muted">Names marked SAMPLE are sample students, not real people.</p>
          )}
        </>
      )}
    </section>
  )
}

function PeopleTable({ people, onBreak = false }: { people: DrillPerson[]; onBreak?: boolean }) {
  return (
    <table className="dash-table dash-drill-table">
      <thead>
        <tr>
          <th scope="col" className="dash-drill-check">
            <span className="visually-hidden">Checked</span>
          </th>
          <th scope="col">Name</th>
          <th scope="col">Card</th>
          <th scope="col">Seat</th>
          <th scope="col">{onBreak ? 'Back by' : 'Since'}</th>
        </tr>
      </thead>
      <tbody>
        {people.map((p) => (
          <tr key={p.cardId}>
            <td className="dash-drill-check">
              <input type="checkbox" aria-label={`${p.name} checked`} />
            </td>
            <th scope="row">
              {p.name}
              {p.sample && <span className="dash-sample-tag">SAMPLE</span>}
            </th>
            <td className="mono">{p.cardId}</td>
            <td className="mono">{p.seat}</td>
            <td>{timeFormat.format(new Date(onBreak && p.breakUntil ? p.breakUntil : p.since))}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
