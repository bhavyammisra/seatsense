import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import './legal.css'

const UPDATED = '26 November 2026'
const REPO = 'https://github.com/bhavyammisra/seatsense'

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="legal">
      <header className="legal-header">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span className="brand-name">SeatSense</span>
        </Link>
      </header>
      <main className="legal-main">
        <h1>{title}</h1>
        <p className="legal-updated">Last updated {UPDATED}</p>
        {children}
        <p className="legal-back">
          <Link to="/">Back to free seats</Link>
        </p>
      </main>
    </div>
  )
}

export function PrivacyPage() {
  return (
    <LegalPage title="Privacy policy">
      <section>
        <h2>Who runs SeatSense</h2>
        <p>
          SeatSense is a course project built by two students for the Enterprise Web Applications (Java) course at
          Thapar Institute of Engineering and Technology. It is not an official library or university service.
        </p>
      </section>

      <section>
        <h2>Sample data only</h2>
        <p>
          Every student, ID card and login in this demo is made up and marked as sample data. Please do not enter a
          real ID card number, password or any other personal information.
        </p>
      </section>

      <section>
        <h2>What it stores</h2>
        <ul>
          <li>Students: card number, name, and optionally programme and year.</li>
          <li>Seat sessions: which seat, when it was taken and freed, and breaks taken.</li>
          <li>Gate scans: card number, gate and time.</li>
          <li>Waitlist places and seat holds, with their times.</li>
          <li>Login accounts: username, role and a hashed password. Plain passwords are never stored.</li>
        </ul>
      </section>

      <section>
        <h2>Who can see what</h2>
        <ul>
          <li>The public pages show seat counts and seat states only, never names.</li>
          <li>The gate kiosk shows the name of the student who has just scanned.</li>
          <li>Logged-in library staff can see who is inside and where.</li>
          <li>A logged-in student sees only their own waitlist place.</li>
        </ul>
        <p>Nothing is sold or shared with anyone else.</p>
      </section>

      <section>
        <h2>Cookies</h2>
        <p>Two cookies, both needed for the site to work:</p>
        <ul>
          <li>
            <span className="mono">JSESSIONID</span> keeps you logged in, and is only set after you log in.
          </li>
          <li>
            <span className="mono">XSRF-TOKEN</span> protects forms and live updates from being sent by other sites.
          </li>
        </ul>
        <p>There are no analytics, advertising or tracking cookies.</p>
      </section>

      <section>
        <h2>Where it runs and how long data is kept</h2>
        <p>
          The hosted demo runs on Render, in its Singapore region. The demo database is reset as needed, and
          everything in it is removed when the course ends.
        </p>
      </section>

      <section>
        <h2>Questions</h2>
        <p>
          Open an issue on the <a href={REPO}>project's GitHub page</a>.
        </p>
      </section>
    </LegalPage>
  )
}

export function TermsPage() {
  return (
    <LegalPage title="Terms of use">
      <section>
        <h2>What this is</h2>
        <p>
          SeatSense is a student course project and a demo. It is not an official service of the library or of Thapar
          Institute of Engineering and Technology.
        </p>
      </section>

      <section>
        <h2>No guarantee of a seat</h2>
        <p>
          Seat counts and seat states can be wrong or out of date, and the site can be down. A seat shown as free may
          already be taken. Library staff and library rules always come first.
        </p>
      </section>

      <section>
        <h2>Using the demo</h2>
        <ul>
          <li>Use the sample accounts and sample ID cards only. Do not enter real personal information.</li>
          <li>Do not try to break into accounts, overload the site or get around its security.</li>
          <li>Sample accounts are shared for the demo and can be reset at any time.</li>
        </ul>
      </section>

      <section>
        <h2>No warranty</h2>
        <p>
          The site is provided as it is, without any warranty. The authors are not responsible for any loss that comes
          from using it.
        </p>
      </section>

      <section>
        <h2>Changes</h2>
        <p>These terms can change. The date at the top shows when they last did.</p>
      </section>
    </LegalPage>
  )
}

/** Links to the privacy policy and terms, for the bottom of public pages. */
export function LegalLinks() {
  return (
    <footer className="legal-links">
      <Link to="/privacy">Privacy</Link>
      <Link to="/terms">Terms</Link>
      <span>A student course project, not an official library service.</span>
    </footer>
  )
}
