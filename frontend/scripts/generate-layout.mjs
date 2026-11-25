// Generates the PROVISIONAL library layout used until the on-site survey (S0.4).
//
// Source: the library's official 360 walkthrough, https://tiet360.in/library/
// The tour shows four floors (Ground, First, Second, Third) built around a
// central open atrium with a stepped main staircase. What each floor has, and
// the furniture types, come from the tour. Seat COUNTS are estimates from the
// photos and must be replaced with real counts from the survey.
//
// Tour scenes per floor (labels inside the tour):
//   Ground  LIBRARY, L1, L2, L3  entrance and security, entry gates, circulation
//                                counter, main stairs, lift, periodicals, red
//                                sofa lounge, desks along the timber wall, long
//                                reading tables under the atrium
//   First   L5, L6, L7           long double-sided tables with a centre divider,
//                                counters along the atrium rail, lounge
//   Second  L8, L9, L10, L11     mostly book stacks, faculty cabins (round timber
//                                pods), counters along the atrium rail, window desks
//   Third   L12 to L18           "Quiet Zone", atrium rail counters, tables among
//                                stacks, desks along the slanted glass facade,
//                                glass group discussion rooms, seminar hall,
//                                bound volumes
//
// Run: npm run layout   (writes both output files listed at the bottom)

import { writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))

// Row letters skip I and O so they are never confused with 1 and 0.
const ROW_LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ'.split('')

/*
  Section kinds
  - tables:  long double-sided tables. Each table is a row of seats, the table
             (with its centre divider), then the facing row of seats.
  - counter: a single row of seats facing a rail, wall or window.
  - rooms:   enclosed group rooms, each with one table seating 4 a side.
  - area:    a part of the floor that is shown but cannot be booked.
*/
const floors = [
  {
    id: 'GF', number: 0, name: 'Ground floor', short: 'Ground',
    landmark: 'Entrance gates, circulation counter, main stairs and lift',
    tour: ['LIBRARY', 'L1', 'L2', 'L3'],
    sections: [
      {
        id: 'atrium', name: 'Atrium reading hall', zone: 'GENERAL', kind: 'tables',
        note: 'Long tables under the open atrium. Charging point at every seat.',
        tables: 8, perSide: 6, columns: 2,
      },
      {
        id: 'wall', name: 'Timber wall desks', zone: 'QUIET', kind: 'counter',
        note: 'Single desks with lamps along the timber wall.',
        faces: 'Timber wall', runs: 2, perRun: 12, runsPerLine: 2,
      },
      {
        id: 'lounge', name: 'Lounge and periodicals', zone: 'GENERAL', kind: 'area',
        note: 'Red sofa lounge, newspapers and magazines. Not bookable.',
        w: 14, h: 2,
      },
    ],
  },
  {
    id: '1F', number: 1, name: 'First floor', short: 'First',
    landmark: 'Main stairs, open atrium and lift',
    tour: ['L5', 'L6', 'L7'],
    sections: [
      {
        id: 'tables', name: 'Reading tables', zone: 'GENERAL', kind: 'tables',
        note: 'Long tables with a centre divider and charging points.',
        tables: 10, perSide: 8, columns: 2,
      },
      {
        id: 'rail', name: 'Atrium edge', zone: 'QUIET', kind: 'counter',
        note: 'Counters along the atrium rail, looking down into the hall.',
        faces: 'Atrium rail', runs: 2, perRun: 12, runsPerLine: 2,
      },
    ],
  },
  {
    id: '2F', number: 2, name: 'Second floor', short: 'Second',
    landmark: 'Main stairs, open atrium and lift',
    tour: ['L8', 'L9', 'L10', 'L11'],
    sections: [
      {
        id: 'rail', name: 'Atrium edge', zone: 'QUIET', kind: 'counter',
        note: 'Counters along the atrium rail.',
        faces: 'Atrium rail', runs: 2, perRun: 12, runsPerLine: 2,
      },
      {
        id: 'stack-tables', name: 'Tables among the stacks', zone: 'GENERAL', kind: 'tables',
        note: 'Smaller tables between the book stacks.',
        tables: 6, perSide: 6, columns: 3,
      },
      {
        id: 'windows', name: 'Window desks', zone: 'QUIET', kind: 'counter',
        note: 'Desks facing the red-fin windows.',
        faces: 'Windows', runs: 2, perRun: 12, runsPerLine: 2, window: true,
      },
      {
        id: 'stacks', name: 'Book stacks and faculty cabins', zone: 'GENERAL', kind: 'area',
        note: 'Not bookable.',
        w: 18, h: 2,
      },
    ],
  },
  {
    id: '3F', number: 3, name: 'Third floor', short: 'Third',
    landmark: 'Main stairs, open atrium and lift',
    tour: ['L12', 'L13', 'L14', 'L15', 'L16', 'L17', 'L18'],
    sections: [
      {
        id: 'rail', name: 'Quiet zone, atrium edge', zone: 'QUIET', kind: 'counter',
        note: 'Counters along the atrium rail. Silence please.',
        faces: 'Atrium rail', runs: 2, perRun: 12, runsPerLine: 2,
      },
      {
        id: 'quiet-tables', name: 'Quiet zone tables', zone: 'QUIET', kind: 'tables',
        note: 'Tables among the stacks. Silence please.',
        tables: 6, perSide: 6, columns: 3,
      },
      {
        id: 'facade', name: 'Glass facade desks', zone: 'QUIET', kind: 'counter',
        note: 'Desks along the slanted glass wall, garden view.',
        faces: 'Glass facade', runs: 2, perRun: 12, runsPerLine: 2, window: true,
      },
      {
        id: 'rooms', name: 'Group discussion rooms', zone: 'DISCUSSION', kind: 'rooms',
        note: 'Glass rooms with a screen. Talking allowed.',
        rooms: 3, perSide: 4,
      },
      {
        id: 'seminar', name: 'Seminar hall', zone: 'GENERAL', kind: 'area',
        note: '80-seat hall for orientation and training. Booked through the library office.',
        w: 18, h: 2,
      },
    ],
  },
]

function sectionWidth(s) {
  switch (s.kind) {
    case 'tables':
      return s.columns * s.perSide + (s.columns - 1) * 2
    case 'counter':
      return s.runsPerLine * s.perRun + (s.runsPerLine - 1) * 2
    case 'rooms':
      return s.rooms * (s.perSide + 2) + (s.rooms - 1)
    case 'area':
      return s.w
  }
}

function buildFloor(floor) {
  const gridWidth = Math.max(...floor.sections.map(sectionWidth))
  const seats = []
  const fixtures = []
  const sections = []
  let letter = 0
  let gy = 0

  const seat = (s, row, number, gx, y, extra = {}) => ({
    code: `${floor.id}-${row}${number}`,
    floor: floor.id,
    section: s.id,
    zone: s.zone,
    row,
    number,
    tableId: null,
    gx,
    gy: y,
    power: true,
    window: Boolean(s.window),
    accessible: false,
    ...extra,
  })

  for (const s of floor.sections) {
    const start = gy
    const offset = Math.floor((gridWidth - sectionWidth(s)) / 2)

    if (s.kind === 'tables') {
      const bands = Math.ceil(s.tables / s.columns)
      let table = 1
      for (let b = 0; b < bands; b++) {
        const top = ROW_LETTERS[letter++]
        const bottom = ROW_LETTERS[letter++]
        let nTop = 1
        let nBottom = 1
        for (let c = 0; c < s.columns && table <= s.tables; c++, table++) {
          const x0 = offset + c * (s.perSide + 2)
          const tableId = `${floor.id}-${s.id}-T${table}`
          fixtures.push({ kind: 'table', section: s.id, gx: x0, gy: gy + 1, w: s.perSide, h: 1, label: `Table ${table}` })
          for (let k = 0; k < s.perSide; k++) {
            seats.push(seat(s, top, nTop++, x0 + k, gy, { tableId }))
            seats.push(seat(s, bottom, nBottom++, x0 + k, gy + 2, { tableId }))
          }
        }
        gy += 3
        if (b < bands - 1) gy++ // walkway between bands
      }
    } else if (s.kind === 'counter') {
      const lines = Math.ceil(s.runs / s.runsPerLine)
      let run = 0
      for (let l = 0; l < lines; l++) {
        const row = ROW_LETTERS[letter++]
        let n = 1
        for (let r = 0; r < s.runsPerLine && run < s.runs; r++, run++) {
          const x0 = offset + r * (s.perRun + 2)
          fixtures.push({ kind: 'counter', section: s.id, gx: x0, gy, w: s.perRun, h: 1, label: s.faces })
          for (let k = 0; k < s.perRun; k++) {
            const accessible = floor.number === 0 && l === 0 && r === 0 && k === 0
            seats.push(seat(s, row, n++, x0 + k, gy + 1, { accessible }))
          }
        }
        gy += 2
        if (l < lines - 1) gy++
      }
    } else if (s.kind === 'rooms') {
      const top = ROW_LETTERS[letter++]
      const bottom = ROW_LETTERS[letter++]
      let nTop = 1
      let nBottom = 1
      for (let r = 0; r < s.rooms; r++) {
        const x0 = offset + r * (s.perSide + 3)
        const tableId = `${floor.id}-${s.id}-R${r + 1}`
        fixtures.push({ kind: 'room', section: s.id, gx: x0, gy, w: s.perSide + 2, h: 5, label: `Room ${r + 1}` })
        fixtures.push({ kind: 'table', section: s.id, gx: x0 + 1, gy: gy + 2, w: s.perSide, h: 1, label: '' })
        for (let k = 0; k < s.perSide; k++) {
          seats.push(seat(s, top, nTop++, x0 + 1 + k, gy + 1, { tableId }))
          seats.push(seat(s, bottom, nBottom++, x0 + 1 + k, gy + 3, { tableId }))
        }
      }
      gy += 5
    } else if (s.kind === 'area') {
      fixtures.push({ kind: 'area', section: s.id, gx: offset, gy, w: s.w, h: s.h, label: s.name })
      gy += s.h
    }

    sections.push({
      id: s.id,
      name: s.name,
      zone: s.zone,
      note: s.note,
      bookable: s.kind !== 'area',
      fromRow: start,
      toRow: gy - 1,
    })
    gy++ // gap before the next section
  }

  return {
    id: floor.id,
    number: floor.number,
    name: floor.name,
    short: floor.short,
    landmark: floor.landmark,
    tourScenes: floor.tour,
    gridWidth,
    gridHeight: gy - 1,
    sections,
    fixtures,
    seats,
  }
}

const builtFloors = floors.map(buildFloor)
const total = builtFloors.reduce((n, f) => n + f.seats.length, 0)

const layout = {
  library: 'Nava Nalanda Central Library, Thapar Institute of Engineering and Technology',
  source: 'https://tiet360.in/library/',
  provisional: true,
  note:
    'PROVISIONAL LAYOUT. Floors, areas and furniture types follow the library\'s official 360 walkthrough. ' +
    'Seat counts are estimates from the photos until the on-site survey (WORKPLAN task S0.4).',
  totalSeats: total,
  floors: builtFloors,
}

const json = JSON.stringify(layout, null, 2) + '\n'
const outputs = [
  resolve(here, '../src/shared/layout/provisional-layout.json'),
  resolve(here, '../../docs/layout/provisional-layout.json'),
]
for (const out of outputs) writeFileSync(out, json)
console.log(`Wrote ${total} bookable seats across ${builtFloors.length} floors.`)
for (const f of builtFloors) console.log(`  ${f.id}: ${f.seats.length} seats, grid ${f.gridWidth} x ${f.gridHeight}`)
