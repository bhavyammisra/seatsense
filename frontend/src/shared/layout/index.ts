import raw from './provisional-layout.json'
import type { Layout } from './types'

// Provisional until the library survey (WORKPLAN S0.4). Once the backend
// layout API exists (S1.1), screens load the layout from there instead.
export const provisionalLayout = raw as Layout

export * from './types'
