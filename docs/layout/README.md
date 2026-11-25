# Library layout

`provisional-layout.json` is a **provisional** layout of the Nava Nalanda Central Library. It is used by the frontend and will seed the database (WORKPLAN S0.5) until the real survey is done.

## What is real and what is assumed

Known from the library's public description (Thapar admissions site, Nava Nalanda Central Library page):
- 5 floors
- designed for "over eight hundred" seats
- group discussion rooms and an 80-seat seminar hall
- RFID-based security and self check-in

No floor-by-floor plan is published. Everything else in the file is an assumption that matches those totals:

| Floor | Sections | Seats |
|---|---|---|
| Ground (GF) | Reading hall, 6 rows of 16 | 96 |
| 1F to 4F (each) | Window carrels 2 x 18, reading hall 5 x 20, group tables 10 x 4 | 176 |
| **Total** | | **800** |

## Replacing it with the real layout (S0.4)

1. Walk each floor. Count rows and seats per row, note aisles, tables, charging points, windows, quiet and discussion zones, and where the stairs and lift are.
2. Edit the `floors` list in `frontend/scripts/generate-layout.mjs` to match.
3. Run `npm run layout` in `frontend/`. It rewrites both copies of the JSON (this folder and `frontend/src/shared/layout/`).
4. Change `provisional` to `false` in the script once the layout is checked on site.

## Seat codes

`<floor>-<row><number>`, for example `2F-C7`. Row letters skip I and O so they are never confused with 1 and 0.
