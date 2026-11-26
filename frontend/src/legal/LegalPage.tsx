import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import './legal.css'

const UPDATED = '26 November 2026'
const REPO = 'https://github.com/bhavyammisra/seatsense'

function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="legal">
      <header className="legal-header">
        <Link to="/" className="legal-brand">
          <span className="legal-mark" aria-hidden="true" />
          SeatSense
        </Link>
      </header>
      <main className="legal-main">
        <h1>{title}</h1>
        <p className="legal-updated">Last updated {UPDATED}</p>
        {children}
        <p className="legal-contact">
          Questions: open an issue on the project's <a href={`${REPO}/issues`}>GitHub page</a>.
        </p>
      </main>
      <LegalFooter />
    </div>
  )
}

/** Links to the privacy policy and terms, for the bottom of public pages. */
export function LegalFooter() {
  return (
    <footer className="legal-footer">
      <span className="legal-footer-thapar">
        <img src="/thapar-logo.jpg" alt="Thapar Institute of Engineering and Technology" width="52" height="46" />
        <span>
          A course project at Thapar Institute of Engineering and Technology, Patiala.
          <br />
          SeatSense is not an official Thapar service.
        </span>
      </span>
      <nav aria-label="Legal">
        <Link to="/privacy">Privacy</Link>
        <Link to="/terms">Terms</Link>
      </nav>
    </footer>
  )
}

export function PrivacyPage() {
  return (
    <LegalLayout title="Privacy policy">
      <p>
        SeatSense is a course project for Enterprising Web Applications at Thapar Institute, built by three students,
        Shefali, Bhavyam and Tushita Jindal. It is a demonstration, not a service run by the Nava Nalanda library. The online demo
        holds <strong>sample data only</strong>: made-up students and ID cards marked SAMPLE. Do not enter real
        personal details.
      </p>

      <h2>What SeatSense stores</h2>
      <ul>
        <li>
          <strong>Students:</strong> ID card number, name, programme and year.
        </li>
        <li>
          <strong>Gate scans:</strong> which card was scanned, at which gate, whether it was an entry or exit, and when.
        </li>
        <li>
          <strong>Seats:</strong> which seat a student took, when they arrived and left, and their breaks.
        </li>
        <li>
          <strong>Waitlist:</strong> the floor a student asked for, when they joined, and any seat offered to them.
        </li>
        <li>
          <strong>Logins:</strong> username, role and password. Passwords are stored only as a one-way hash.
        </li>
      </ul>

      <h2>Why</h2>
      <p>
        To give out seats at the gate, show how many seats are free, run the waitlist, and show librarians how many
        people are inside. The public page and the staff pages show seat states and counts. They never show who is
        sitting where.
      </p>

      <h2>Camera</h2>
      <p>
        The scanner page uses the phone camera to read the barcode on an ID card. The barcode is read on the phone
        itself. Only the card number is sent to SeatSense. No pictures or video are sent or stored.
      </p>

      <h2>Cookies</h2>
      <p>SeatSense sets two cookies, both needed for it to work:</p>
      <ul>
        <li>a session cookie that keeps you logged in, removed when you log out or close the browser;</li>
        <li>a security token (XSRF-TOKEN) that stops other websites sending requests in your name.</li>
      </ul>
      <p>There are no analytics, advertising or tracking cookies.</p>

      <h2>Other services</h2>
      <ul>
        <li>
          <strong>Google Fonts</strong> provides the typeface. Your browser downloads it from Google's servers, which
          see your IP address.
        </li>
        <li>
          <strong>Render</strong> hosts the online demo and its database, on servers in Singapore.
        </li>
      </ul>
      <p>Nothing is sold or shared with anyone else.</p>

      <h2>How long data is kept</h2>
      <p>
        There is no automatic deletion yet. Sample data stays in the database until the project team resets or
        deletes it. The demo reset ends every seat and clears the waitlist.
      </p>
    </LegalLayout>
  )
}

export function TermsPage() {
  return (
    <LegalLayout title="Terms of use">
      <p>
        SeatSense is a student course project built for Enterprising Web Applications at Thapar Institute. By using
        it you agree to these terms.
      </p>

      <h2>A demonstration, not the library</h2>
      <p>
        SeatSense is not run by the Nava Nalanda library or Thapar Institute. Seat information shown here may be
        wrong or out of date. A seat, a hold or a waitlist place on SeatSense does not give anyone a right to a seat
        in the real library. Library staff and library rules always come first.
      </p>

      <h2>Sample data only</h2>
      <p>
        The online demo uses made-up students and ID cards marked SAMPLE. Do not enter real names, card numbers or
        passwords you use anywhere else.
      </p>

      <h2>Fair use</h2>
      <ul>
        <li>Use only the sample cards and accounts the project team gave you.</li>
        <li>Do not try to get into other accounts or parts of the system you were not given access to.</li>
        <li>Do not overload, scrape or try to break the service.</li>
      </ul>

      <h2>No guarantee</h2>
      <p>
        SeatSense is provided as it is, without any guarantee. The online demo may be slow to wake up, reset, change
        or go offline at any time, and its data may be deleted.
      </p>

      <h2>Changes</h2>
      <p>These terms may change as the project changes. The date at the top shows the latest version.</p>
    </LegalLayout>
  )
}
