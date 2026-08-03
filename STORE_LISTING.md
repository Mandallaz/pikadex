# Play Store Listing Draft — PikaDex

Draft copy to paste into Play Console. Edit freely.

## App name
PikaDex

## Short description (max 80 chars)
A fast Pokédex: stats, type matchups, triangles, and team builder.

## Full description (max 4000 chars)
PikaDex is a Pokédex reference app covering every Pokémon, with tools built for
competitive and casual players alike:

- Full national dex: search by name or number, filter by type, move, ability, or
  competitive tier, and sort by any base stat.
- Detailed Pokémon pages: base stats (ranked against every other Pokémon), type
  matchups, abilities, evolution lines, and full move lists (level-up, TM/HM,
  breeding, tutor).
- Type Triangles: a reference for every three-way type "beats" cycle in the type
  chart (like Fire/Water/Grass), including which typings counter each one.
- Team Builder: assemble a team of up to 6 Pokémon and see the combined type
  matchup matrix and shared weaknesses at a glance.
- Favorites, offline-friendly caching, and a clean Material You interface with
  full dark mode support.

Data provided by PokeAPI (pokeapi.co) and Smogon.

## Category
Reference (or: Entertainment)

## Content rating
Answer the Play Console questionnaire — this app has no violence, ads, or user
-generated content, so it should land in the lowest tier (e.g. PEGI 3 / Everyone).

## Data safety form
No data is collected or shared. See PRIVACY_POLICY.md — the app has no accounts,
no analytics, no ads, and stores favorites/team locally on-device only.

## Privacy policy URL
Host PRIVACY_POLICY.md somewhere public (GitHub Pages, a Gist raw link, Google
Sites, etc.) and paste that URL into Play Console's "Privacy policy" field —
it's required even for apps that collect nothing.

## Screenshots / feature graphic
Not included here — capture these from the running app:
- Phone screenshots: at least 2, recommended 4-8 (List, Detail, Team, Type
  Triangles all make good candidates).
- Feature graphic: 1024x500 banner image for the store listing header.
- App icon: 512x512 PNG for the store listing (separate from the in-app
  adaptive icon).

## Known open item
The in-app launcher icon (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`)
is still the default Android Studio template icon, not a custom PikaDex icon —
replace it before shipping if you want a real brand identity in the launcher
and store listing.
