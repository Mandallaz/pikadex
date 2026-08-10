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
  matchups, abilities, evolution lines including Mega Evolutions, and full move
  lists with type, power and accuracy (level-up, TM/HM, breeding, tutor).
- Competitive links: open a Pokémon's Smogon strategy dex page for the
  generations it actually appears in, without leaving the app.
- Type Triangles: a reference for every three-way type "beats" cycle in the type
  chart (like Fire/Water/Grass), including which typings counter each one.
- Team Builder: assemble a team of up to 6 Pokémon and see the combined type
  matchup matrix and shared weaknesses at a glance — or load any of 81 gym
  leader and champion teams from the main series, from Red/Blue to
  Scarlet/Violet.
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
no analytics and no ads. The only things it writes are on-device and stay there:
favorites/team in SharedPreferences, plus a cache of the public game data and
images already downloaded.

## Privacy policy URL
Host PRIVACY_POLICY.md somewhere public (GitHub Pages, a Gist raw link, Google
Sites, etc.) and paste that URL into Play Console's "Privacy policy" field —
it's required even for apps that collect nothing.

## Screenshots / feature graphic
Phone screenshots (List, Detail, Type Triangles, Team, Settings, Compare) are captured in
`store/screenshots/` — see that folder's own README for capture settings. Still missing:
- Feature graphic: 1024x500 banner image for the store listing header.
- App icon: 512x512 PNG for the store listing (separate from the in-app
  adaptive icon).

## Known open items
The launcher icon is done — a custom Poké Ball adaptive icon, not the Android
Studio template. What is still outstanding before submitting:

- The feature graphic and 512x512 app icon (see the section above).
- Hosting PRIVACY_POLICY.md at a public URL for the Play Console field.
