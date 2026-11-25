import { useEffect, useRef } from 'react'
import JsBarcode from 'jsbarcode'
import './cards.css'

// Printable SAMPLE ID cards for the demo (WORKPLAN X3.1). Each barcode holds the
// card number of one sample student (SAMPLE0001 to SAMPLE0040, seeded by V1).
// These are not real people or real cards.

const SAMPLE_NAMES = [
  'Aarav S.', 'Ananya K.', 'Arjun M.', 'Diya P.', 'Ishaan R.', 'Kavya G.', 'Rohan B.', 'Meera T.',
  'Vihaan D.', 'Saanvi N.', 'Kabir J.', 'Aditi V.', 'Reyansh C.', 'Myra A.', 'Aryan L.', 'Tara H.',
  'Dhruv E.', 'Nisha F.', 'Kunal O.', 'Riya W.', 'Harsh Y.', 'Pooja Z.', 'Manav Q.', 'Sneha U.',
  'Yash I.', 'Simran K.', 'Nikhil S.', 'Isha M.', 'Arnav P.', 'Jasleen R.', 'Kartik G.', 'Avni B.',
  'Gurnoor T.', 'Prisha D.', 'Rahul N.', 'Navya J.', 'Siddharth V.', 'Tanvi C.', 'Parth A.', 'Zoya L.',
]

export default function CardsPage() {
  return (
    <main className="cards">
      <header className="cards-header">
        <h1>Sample ID cards</h1>
        <p>
          For the demo scanner. Print this page (cards are sized to cut out). Sample data: not real students or real
          cards.
        </p>
        <button type="button" className="btn btn-primary" onClick={() => window.print()}>
          Print
        </button>
      </header>
      <div className="cards-grid">
        {SAMPLE_NAMES.map((name, i) => (
          <Card key={name} name={name} cardId={`SAMPLE${String(i + 1).padStart(4, '0')}`} />
        ))}
      </div>
    </main>
  )
}

function Card({ name, cardId }: { name: string; cardId: string }) {
  const barcode = useRef<SVGSVGElement>(null)

  useEffect(() => {
    if (barcode.current) {
      JsBarcode(barcode.current, cardId, { format: 'CODE128', height: 48, width: 2, displayValue: true, fontSize: 12, margin: 0 })
    }
  }, [cardId])

  return (
    <article className="card-id">
      <div className="card-id-top">
        <span className="card-id-library">Nava Nalanda Central Library</span>
        <span className="card-id-sample">SAMPLE</span>
      </div>
      <p className="card-id-name">{name}</p>
      <svg ref={barcode} aria-label={`Barcode ${cardId}`} />
    </article>
  )
}
