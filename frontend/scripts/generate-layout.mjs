// Generates the PROVISIONAL library layout used until the real survey (S0.4).
//
// What is known publicly about the Nava Nalanda Central Library:
//   - a five-storied building
//   - designed for "over eight hundred" seats
//   - group discussion rooms and an 80-seat seminar hall
// Nothing public gives a floor-by-floor plan, so the sections below are an
// assumption that matches those totals. Replace them with the survey data.
//
// Run: npm run layout   (writes both output files listed at the bottom)

import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))

// Row letters skip I and O so they are never confused with 1 and 0.
const ROW_LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ'.split('')

/**
 * Section kinds:
 *  - rows:   straight rows of seats, optional aisle gap after `aisleAfter`
 *  - tables: tables of 4 drawn as 2x2 blocks, `tablesPerRow` per band
 */
const upperFloor = [
  {
    id: 'window', name: 'Window carrels', zone: 'QUIET',
    note: 'Quiet. Every seat has a charging point.',
    kind: 'rows', rows: 2, seatsPerRow: 18, aisleAfter: 9,
    power: () => true, window: (row) => row === 0,
  },
  {
    id: 'hall', name: 'Reading hall', zone: 'GENERAL',
    note: 'Charging points on aisle and end seats.',
    kind: 'rows', rows: 5, seatsPerRow: 20, aisleAfter: 10,
    power: (_row, col, n) => col === 0 || col === n - 1 || col === 9 || col === 10,
    window: () => false,
  },
  {
    id: 'tables', name: 'Group tables', zone: 'DISCUSSION',
    note: 'Tables of 4. Talking allowed.',
    kind: 'tables', tables: 10, tablesPerRow: 5,
    power: () => true, window: () => false,
  },
]

const floors = [
  {
    id: 'GF', number: 0, name: 'Ground floor',
    landmark: 'Entrance, circulation desk, stairs and lift',
    sections: [
      {
        id: 'hall', name: 'Reading hall', zone: 'GENERAL',
        note: 'Charging points on end seats.',
        kind: 'rows', rows: 6, seatsPerRow: 16, aisleAfter: 8,
        power: (_row, col, n) => col === 0 || col === n - 1,
        window: () => false,
      },
    ],
  },
  { id: '1F', number: 1, name: 'First floor', landmark: 'Stairs and lift', sections: upperFloor },
  { id: '2F', number: 2, name: 'Second floor', landmark: 'Stairs and lift', sections: upperFloor },
  { id: '3F', number: 3, name: 'Third floor', landmark: 'Stairs and lift', sections: upperFloor },
  { id: '4F', number: 4, name: 'Fourth floor', landmark: 'Stairs and lift', sections: upperFloor },
]

const GRID_WIDTH = 21 // widest section: 20 seats plus one aisle column

function buildFloor(floor) {
  const seats = []
  const sections = []
  let rowIndex = 0 // floor-wide row letter counter
  let gy = 0 // grid row on the floor

  for (const s of floor.sections) {
    const sectionStartY = gy
    if (s.kind === 'rows') {
      const width = s.seatsPerRow + (s.aisleAfter ? 1 : 0)
      const offset = Math.floor((GRID_WIDTH - width) / 2)
      for (let r = 0; r < s.rows; r++) {
        const letter = ROW_LETTERS[rowIndex++]
        for (let c = 0; c < s.seatsPerRow; c++) {
          const gx = offset + c + (s.aisleAfter && c >= s.aisleAfter ? 1 : 0)
          seats.push({
            code: `${floor.id}-${letter}${c + 1}`,
            floor: floor.id,
            section: s.id,
            zone: s.zone,
            row: letter,
            number: c + 1,
            tableId: null,
            gx,
            gy,
            power: s.power(r, c, s.seatsPerRow),
            window: s.window(r, c, s.seatsPerRow),
            accessible: floor.number === 0 && r === 0 && (c === 0 || c === s.seatsPerRow - 1),
          })
        }
        gy++
      }
    } else if (s.kind === 'tables') {
      const bands = Math.ceil(s.tables / s.tablesPerRow)
      const width = s.tablesPerRow * 2 + (s.tablesPerRow - 1)
      const offset = Math.floor((GRID_WIDTH - width) / 2)
      let table = 1
      for (let b = 0; b < bands; b++) {
        const top = ROW_LETTERS[rowIndex++]
        const bottom = ROW_LETTERS[rowIndex++]
        const numbers = { [top]: 1, [bottom]: 1 }
        for (let t = 0; t < s.tablesPerRow && table <= s.tables; t++, table++) {
          const tableId = `${floor.id}-T${table}`
          for (const [dy, letter] of [[0, top], [1, bottom]]) {
            for (let dx = 0; dx < 2; dx++) {
              seats.push({
                code: `${floor.id}-${letter}${numbers[letter]++}`,
                floor: floor.id,
                section: s.id,
                zone: s.zone,
                row: letter,
                number: numbers[letter] - 1,
                tableId,
                gx: offset + t * 3 + dx,
                gy: gy + dy,
                power: s.power(),
                window: s.window(),
                accessible: false,
              })
            }
          }
        }
        gy += 2
        if (b < bands - 1) gy++ // walkway between table bands
      }
    }
    sections.push({
      id: s.id,
      name: s.name,
      zone: s.zone,
      note: s.note,
      fromRow: sectionStartY,
      toRow: gy - 1,
    })
    gy++ // gap before next section heading
  }

  return {
    id: floor.id,
    number: floor.number,
    name: floor.name,
    landmark: floor.landmark,
    gridWidth: GRID_WIDTH,
    gridHeight: gy - 1,
    sections,
    seats,
  }
}

const builtFloors = floors.map(buildFloor)
const total = builtFloors.reduce((n, f) => n + f.seats.length, 0)

const layout = {
  library: 'Nava Nalanda Central Library, Thapar Institute of Engineering and Technology',
  provisional: true,
  note:
    'PROVISIONAL LAYOUT. Floor count (5) and total of about 800 seats come from the library\'s public description. ' +
    'Sections, rows and seat positions are assumptions until the on-site survey (WORKPLAN task S0.4).',
  totalSeats: total,
  floors: builtFloors,
}

const json = JSON.stringify(layout, null, 2) + '\n'
const outputs = [
  resolve(here, '../src/shared/layout/provisional-layout.json'),
  resolve(here, '../../docs/layout/provisional-layout.json'),
]
for (const out of outputs) writeFileSync(out, json)
console.log(`Wrote ${total} seats across ${builtFloors.length} floors to:\n  ${outputs.join('\n  ')}`)
