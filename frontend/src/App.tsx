import { Navigate, Route, Routes } from 'react-router-dom'
import KioskPage from './kiosk/KioskPage'
import ScannerPage from './scanner/ScannerPage'
import PublicPage from './public/PublicPage'
import PortalPage from './portal/PortalPage'
import DashboardPage from './dashboard/DashboardPage'
import CardsPage from './cards/CardsPage'
import { PrivacyPage, TermsPage } from './legal/LegalPage'

// Route map (docs/PROJECT.md Section 6):
//   /           public availability site   (Bhavyam, B1.4)
//   /kiosk      gate kiosk                 (Shefali, S1.8)
//   /scanner    phone ID scanner           (Shefali, S1.7)
//   /portal     student portal             (Bhavyam, B2.3)
//   /dashboard  librarian dashboard        (Bhavyam, B1.6)
//   /cards      printable SAMPLE ID cards for the demo (X3.1)
//   /privacy    privacy policy
//   /terms      terms of use
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PublicPage />} />
      <Route path="/kiosk" element={<KioskPage />} />
      <Route path="/scanner" element={<ScannerPage />} />
      <Route path="/portal" element={<PortalPage />} />
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/cards" element={<CardsPage />} />
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
