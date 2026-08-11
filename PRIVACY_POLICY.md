# Privacy Policy for PikaDex

_Last updated: 2026-08-11_

PikaDex is a Pokédex reference app. This policy explains what data the app handles.

## Data collection

PikaDex does not collect, store, or transmit any personal data. It has no user
accounts, no analytics, and no advertising SDKs.

## Network access

The app connects to the internet only to fetch public Pokémon data and images from:

- [PokeAPI](https://pokeapi.co) (pokemon/species/moves/abilities/type data)
- PokeAPI's sprite/artwork hosting on GitHub (`raw.githubusercontent.com`)
- [Smogon](https://www.smogon.com) (competitive tier data)

These requests are anonymous lookups of public game data (e.g. "fetch Pikachu's
stats"). No personal or device-identifying information is sent beyond what's
inherent to any HTTP request (IP address, standard headers), and none of it is
logged or stored by this app.

The app also checks, locally on your device only, whether your active
connection is Wi-Fi or a metered connection (mobile data or a metered
hotspot), so the optional offline-data prefetch can warn before using mobile
data. This check never leaves your device and nothing about your network is
sent anywhere.

## Data stored on your device

Your favorites, your team roster, and your app preferences (e.g. offline
prefetch tiers, the AMOLED display toggle) are saved locally on your device
only (Android SharedPreferences), so they persist between sessions.

The app also keeps a local cache of the public game data, images, and cry
audio it has already downloaded, so that it starts faster and needs less
network on later launches. That cache holds the same Pokémon data anyone can
fetch from PokeAPI; it contains nothing about you or your device, and no
record of what you looked at is sent anywhere.

All of the above stays on your device, and is deleted if you uninstall the app
or clear its storage.

## Third-party services

The app depends on the third-party services listed above to function. Their use
of data is governed by their own policies:

- [PokeAPI's policies](https://pokeapi.co/docs/v2#info)
- [Smogon's policies](https://www.smogon.com/about/privacy)

## Children's privacy

PikaDex does not knowingly collect any data from anyone, including children,
since it collects no data at all.

## Changes to this policy

If this policy changes (e.g. a future version adds a new feature that touches
data handling), this document will be updated and the "Last updated" date above
will change accordingly.

## Contact

For any question about this policy, open an issue on the project's repository: <https://github.com/Mandallaz/pikadex>.
