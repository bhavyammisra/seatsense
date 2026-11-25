# Library layout

`provisional-layout.json` is a **provisional** layout of the Nava Nalanda Central Library. The frontend uses it, and it will seed the database (WORKPLAN S0.5) until the on-site survey is done.

## Source: the library's 360 walkthrough

Floors, areas and furniture types follow the official 360 tour at https://tiet360.in/library/. The tour's floor buttons and links put its scenes on four floors:

| Floor | Tour scenes | What the tour shows | Modelled as |
|---|---|---|---|
| Ground (GF) | LIBRARY, L1, L2, L3 | Entrance and security room, entry gates, circulation counter, main stepped staircase, lift core, periodicals, red sofa lounge, self-check kiosk, desks along the timber wall, long tables under the atrium | Atrium reading hall (8 tables, 6 a side), timber wall desks (2 runs of 12), lounge (not bookable) |
| First (1F) | L5, L6, L7 | Long double-sided tables with a centre divider and charging slots, counters along the atrium rail, lounge chairs, stacks | Reading tables (10 tables, 8 a side), atrium edge counters (2 runs of 12) |
| Second (2F) | L8, L9, L10, L11 | Mostly book stacks, faculty cabins (round timber pods), atrium rail, desks near the windows | Atrium edge (2 x 12), tables among the stacks (6 tables, 6 a side), window desks (2 x 12), stacks and cabins (not bookable) |
| Third (3F) | L12 to L18 | "Quiet Zone" signage, counters along the atrium rail, tables among stacks, desks along the slanted glass facade, glass group discussion rooms with a screen, 80-seat seminar hall, bound volumes | Quiet atrium edge (2 x 12), quiet tables (6 tables, 6 a side), glass facade desks (2 x 12), 3 discussion rooms (8 seats each), seminar hall (not bookable) |

Bookable seats in the provisional layout: **568** (GF 120, 1F 184, 2F 120, 3F 144). The library's public description says the building is designed for "over eight hundred" seats. That figure likely includes the seminar hall, lounges and areas the tour does not show.

**What is real:** the floors, what is on each floor, and the furniture types.
**What is estimated:** every count (tables, seats per side, desks per run) and every position. Replace them with the survey.

## Replacing it with the real layout (S0.4)

1. Walk each floor with the tour open. For every area, count tables and seats per side, or desks per run, and note what each counter faces.
2. Edit the `floors` list in `frontend/scripts/generate-layout.mjs`. Section kinds: `tables`, `counter`, `rooms`, `area` (not bookable).
3. Run `npm run layout` in `frontend/`. It rewrites both copies of the JSON (this folder and `frontend/src/shared/layout/`).
4. Set `provisional: false` in the script once the layout is checked on site.

## Seat codes

`<floor>-<row><number>`, for example `1F-C7`. Row letters skip I and O so they are never confused with 1 and 0. Seats at the same table or in the same room share a `tableId`, which "Sit together" uses to keep a group at one table.
