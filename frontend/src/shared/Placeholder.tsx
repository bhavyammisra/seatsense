import './placeholder.css'

type Props = {
  title: string
  owner: string
  task: string
  children?: React.ReactNode
}

// Temporary page used until the real screen is built.
export default function Placeholder({ title, owner, task, children }: Props) {
  return (
    <main className="placeholder">
      <p className="placeholder-brand">SeatSense</p>
      <h1>{title}</h1>
      <p className="placeholder-meta">
        Not built yet. Owner: {owner}, task <span className="mono">{task}</span> in docs/WORKPLAN.md.
      </p>
      {children}
    </main>
  )
}
