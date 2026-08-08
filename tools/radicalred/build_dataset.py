#!/usr/bin/env python3
"""Converts the Radical Red ROM hack's Pokedex data into a JSON fixture PikaDex bundles as an
asset (app/src/main/assets/radicalred/), shaped close to PikaDex's existing PokeAPI DTOs
(app/src/main/java/com/mandallaz/pikadex/data/remote/dto/) so it can later back a
RadicalRedDataSource without a second data model.

Source: https://github.com/JwowSquared/Radical-Red-Pokedex, specifically its root data.js — a
~4.6MB JS object literal (single-quoted keys, \\uXXXX-escaped text, not valid JSON as-is) covering
species, moves, abilities, items, types, natures and trainers. This is the same repo
dex.radicalred.net itself is generated from.

Usage:
    python3 build_dataset.py [--data-js PATH] [--out PATH] [--skip-pokeapi]

Without --data-js, downloads the latest data.js from GitHub. Without --out, writes to
app/src/main/assets/radicalred/radicalred_pokedex.json relative to the repo root.

Sprites are NOT handled by this script — see fetch_sprites.sh in this same directory.
"""

import argparse
import json
import re
import subprocess
import unicodedata
from pathlib import Path

DATA_JS_URL = "https://raw.githubusercontent.com/JwowSquared/Radical-Red-Pokedex/master/data.js"
POKEAPI_GRAPHQL_URL = "https://graphql.pokeapi.co/v1beta2"

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = REPO_ROOT / "app/src/main/assets/radicalred/radicalred_pokedex.json"

STAT_NAMES = ["hp", "attack", "defense", "speed", "special-attack", "special-defense"]

# Reimplements the template-string lookup table found at data.js's own top-level 'evolutions'
# key (e.g. 4: 'at Level ${evo[1]}', 254: combined move-or-item trigger). Every trigger code
# actually used across the dex (verified by enumeration, not guessed) is covered.
NATURE_GROUP_30 = "Adamant, Brave, Docile, Hardy, Hasty, Impish, Jolly, Lax, Naive, Naughty, Rash, Quirky, or Sassy"
NATURE_GROUP_31 = "Bashful, Bold, Calm, Careful, Gentle, Lonely, Mild, Modest, Quiet, Relaxed, Serious, or Timid"


def unescape_js(text):
    """Decodes \\uXXXX escapes left as literal text by naive regex extraction (e.g. Farfetch\\u2019d)."""
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), text)


def slugify(name):
    """Matches PokeAPI's own slug convention closely enough for exact-name lookups to hit."""
    name = re.sub(r"[’']", "", name)
    name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def block(content, start_key, end_key):
    start = content.find(f"'{start_key}':{{")
    end = content.find(f",'{end_key}':{{")
    return content[start:end]


def parse_list_field(entry, field):
    m = re.search(rf"'{field}':\[([^\]]*)\]", entry)
    if not m or not m.group(1).strip():
        return []
    return [int(x) for x in m.group(1).split(",")]


def http_post_json(url, payload):
    """Uses curl rather than urllib: this repo's local Python has been seen missing a working
    root CA bundle for TLS (SSLCertVerificationError), while curl uses the system trust store
    and just works. Keeps the fetch scripts usable without fiddling with certifi/openssl."""
    result = subprocess.run(
        ["curl", "-sS", "-X", "POST", url, "-H", "Content-Type: application/json", "-d", json.dumps(payload)],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def http_get(url):
    result = subprocess.run(["curl", "-sS", url], capture_output=True, text=True, check=True)
    return result.stdout


def load_data_js(path_or_none):
    if path_or_none:
        text = Path(path_or_none).read_text()
    else:
        text = http_get(DATA_JS_URL)
    return unescape_js(text)


def build_lookup_tables(content):
    types_block = block(content, "types", "abilities")
    type_map = {int(k): v for k, v in re.findall(r"(\d+):\{'ID':\d+,'name':'([^']+)'", types_block)}

    abilities_block = block(content, "abilities", "items")
    ability_map = {0: "None"}
    for m in re.finditer(r"(\d+):\{'ID':\d+,'names':\[([^\]]+)\]", abilities_block):
        names = re.findall(r"'([^']+)'", m.group(2))
        ability_map[int(m.group(1))] = names[0]

    items_block = block(content, "items", "trainers")
    item_map = {0: "None"}
    for m in re.finditer(r"(\d+):\{'ID':\d+,'name':'([^']+)'", items_block):
        item_map[int(m.group(1))] = m.group(2)

    moves_end = content.find(",'tmMoves':{")
    moves_block = content[content.find("'moves':{"):moves_end]
    move_map = {0: "None"}
    for m in re.finditer(r"(\d+):\{'ID':\d+,'name':'([^']+)'", moves_block):
        move_map[int(m.group(1))] = m.group(2)

    return type_map, ability_map, item_map, move_map


def make_evolution_describer(type_map, item_map, move_map, id_to_name):
    def describe(trigger, p1, p3):
        item1 = item_map.get(p1, f"item#{p1}")
        if trigger == 1:
            return "on Level Up with Friendship"
        if trigger == 2:
            return "on Level Up with Friendship (Day)"
        if trigger == 3:
            return "on Level Up with Friendship (Night)"
        if trigger == 4:
            return f"at Level {p1}"
        if trigger == 7:
            suffix = ""
            if p1 == 101:
                suffix = " (Female)" if p3 == 254 else " (Male)"
            return f"with a {item1}{suffix}"
        if trigger == 8:
            return f"at Level {p1} when Attack > Defense"
        if trigger == 9:
            return f"at Level {p1} when Attack = Defense"
        if trigger == 10:
            return f"at Level {p1} when Attack < Defense"
        if trigger in (11, 12):
            return f"at Level {p1}, with a 50% chance"
        if trigger == 13:
            return f"at Level {p1}"
        if trigger == 14:
            return "when evolving to Ninjask with Open Party Slot & Poke Ball"
        if trigger == 16:
            return f"at Level {p1} with Overworld Rain"
        if trigger == 17:
            return f"on Level Up with Friendship and knowing a {type_map.get(p1, f'type#{p1}')} Type move"
        if trigger == 18:
            return f"at Level {p1} with {type_map.get(p3, f'type#{p3}')} Type in Party"
        if trigger == 20:
            return f"at Level {p1} (Male)"
        if trigger == 21:
            return f"at Level {p1} (Female)"
        if trigger == 22:
            return f"at Level {p1} (Night)"
        if trigger == 23:
            return f"at Level {p1} (Day)"
        if trigger == 26:
            return f"on Level Up with the move {move_map.get(p1, f'move#{p1}')}"
        if trigger == 27:
            return f"on Level Up with {id_to_name.get(p1, f'species#{p1}')} in Party"
        if trigger == 28:
            tod = "(Day)" if p3 == 1041 else "(Night)" if p3 == 5144 else "(Dusk)"
            return f"at Level {p1} {tod}"
        if trigger == 30:
            return f"at Level {p1} with {NATURE_GROUP_30} Nature"
        if trigger == 31:
            return f"at Level {p1} with {NATURE_GROUP_31} Nature"
        if trigger == 254:
            if p3 == 2:
                return f"with the move {move_map.get(p1, f'move#{p1}')}"
            return f"with the {item_map.get(p1, f'item#{p1}')}"
        return f"via unknown trigger #{trigger} (param={p1}, extra={p3})"

    return describe


def parse_species(content, type_map, ability_map, item_map, move_map):
    species_end = content.find(",'moves':{")
    species_block_str = content[:species_end]
    raw_entries = re.split(r",(?=\d+:\{'ID':)", species_block_str)
    raw_entries[0] = raw_entries[0][len("'species':{"):]

    id_to_name = {}
    for e in raw_entries:
        idm = re.search(r"'ID':(\d+)", e)
        namem = re.search(r"'name':'([^']+)'", e)
        if idm and namem:
            id_to_name[int(idm.group(1))] = namem.group(1)

    describe_evolution = make_evolution_describer(type_map, item_map, move_map, id_to_name)

    pokemon = []
    for e in raw_entries:
        idm = re.search(r"'ID':(\d+)", e)
        if not idm:
            continue
        pid = int(idm.group(1))
        namem = re.search(r"'name':'([^']+)'", e)
        keym = re.search(r"'key':'([^']+)'", e)
        dexm = re.search(r"'dexID':(\d+)", e)
        changesm = re.search(r"'changes':'(\w+)'", e)
        name = namem.group(1) if namem else None
        key = keym.group(1) if keym else name

        stats = parse_list_field(e, "stats")
        types_ids = parse_list_field(e, "type")
        types = [type_map.get(t, f"unknown-{t}").lower() for t in types_ids]

        abilities_raw = re.search(r"'abilities':\[(.*?)\],'eggGroup'", e)
        abilities = []
        if abilities_raw:
            for slot, (aid, hidden) in enumerate(re.findall(r"\[(\d+),(\d+)\]", abilities_raw.group(1)), start=1):
                aid = int(aid)
                if aid == 0:
                    continue
                abilities.append({
                    "ability": {"name": slugify(ability_map.get(aid, f"unknown-{aid}"))},
                    "is_hidden": hidden == "1",
                    "slot": slot,
                })

        held_items_raw = parse_list_field(e, "items")
        held_items = [item_map.get(i) for i in held_items_raw if i != 0]

        eggm = re.search(r"'eggGroup':\[([^\]]+)\]", e)
        egg_group_ids = [int(x) for x in eggm.group(1).split(",")] if eggm and eggm.group(1).strip() else []

        moves = []
        levelup_raw = re.search(r"'levelupMoves':\[(.*?)\],'evolutions'", e)
        if levelup_raw and levelup_raw.group(1).strip():
            for mv, lvl in re.findall(r"\[(\d+),(\d+)\]", levelup_raw.group(1)):
                moves.append({
                    "move": {"name": slugify(move_map.get(int(mv), f"unknown-{mv}"))},
                    "version_group_details": [{
                        "level_learned_at": int(lvl),
                        "move_learn_method": {"name": "level-up"},
                    }],
                })
        for mv in parse_list_field(e, "tmMoves"):
            moves.append({
                "move": {"name": slugify(move_map.get(mv, f"unknown-{mv}"))},
                "version_group_details": [{"level_learned_at": 0, "move_learn_method": {"name": "machine"}}],
            })
        for mv in parse_list_field(e, "tutorMoves"):
            if mv == 0:
                continue
            moves.append({
                "move": {"name": slugify(move_map.get(mv, f"unknown-{mv}"))},
                "version_group_details": [{"level_learned_at": 0, "move_learn_method": {"name": "tutor"}}],
            })
        for mv in parse_list_field(e, "eggMoves"):
            moves.append({
                "move": {"name": slugify(move_map.get(mv, f"unknown-{mv}"))},
                "version_group_details": [{"level_learned_at": 0, "move_learn_method": {"name": "egg"}}],
            })

        evolutions = []
        evolutions_raw = re.search(r"'evolutions':\[(.*?)\],'tmMoves'", e)
        if evolutions_raw and evolutions_raw.group(1).strip():
            for m in re.finditer(r"\[(-?\d+),(-?\d+),(-?\d+),(-?\d+)\]", evolutions_raw.group(1)):
                trigger, p1, target, p3 = (int(x) for x in m.groups())
                evolutions.append({
                    "target_species_id": target,
                    "description": describe_evolution(trigger, p1, p3),
                    "trigger_raw": trigger,
                    "param_raw": p1,
                    "extra_raw": p3,
                })

        stats_out = (
            [{"base_stat": v, "stat": {"name": n}} for v, n in zip(stats, STAT_NAMES)]
            if len(stats) == 6 else []
        )

        pokemon.append({
            "id": pid,
            "dex_id": int(dexm.group(1)) if dexm else None,
            "name": slugify(key),
            "display_name": name,
            "key": key,
            "is_radical_red_exclusive": changesm.group(1) == "new" if changesm else False,
            "types": [{"slot": i + 1, "type": {"name": t}} for i, t in enumerate(types)],
            "stats": stats_out,
            "abilities": abilities,
            "held_items": held_items,
            "egg_group_ids": egg_group_ids,
            "moves": moves,
            "evolutions": evolutions,
            "sprites": {
                "front_default": f"radicalred/sprites/front/{pid}.png",
                "front_shiny": f"radicalred/sprites/front_shiny/{pid}.png",
            },
        })

    pokemon.sort(key=lambda x: x["id"])
    return pokemon


def fetch_pokeapi_basics():
    """One bulk GraphQL call (same endpoint/shape as PokeApiGraphQLDataSource.kt) instead of
    ~2700 individual REST calls for height/weight/species metadata."""
    query = """
        query {
          pokemon(limit: 2000) {
            name
            is_default
            height
            weight
            base_experience
            pokemonspecy {
              is_legendary
              is_mythical
              generation_id
              pokemoncolor { name }
              pokemonegggroups { egggroup { name } }
              pokemonspeciesnames(where: {language_id: {_eq: 9}}) { genus }
            }
          }
        }
    """
    payload = http_post_json(POKEAPI_GRAPHQL_URL, {"query": query})
    return payload["data"]["pokemon"]


def enrich_with_pokeapi(pokemon_list):
    """Fills height/weight/base_experience/legendary/mythical/generation/color/egg_groups/genus
    from PokeAPI, matched by slug in three tiers (recorded per-entry as pokeapi_metadata.source):
      - exact: RR's key slug is itself a real PokeAPI name (covers canonical forms and Primals)
      - base-species-fallback: RR-exclusive reskins (Sevii forms, custom Megas) fall back to the
        base species' metadata — describes the base Pokemon, not the RR variant, by nature
      - base-species-default-variety: species whose PokeAPI entry only exists under a named form
        (e.g. deoxys -> deoxys-normal), resolved via each family's is_default flag
    Entries with no match at all (e.g. Chillet, a genuine fakemon) get pokeapi_metadata = None.
    """
    basics_raw = fetch_pokeapi_basics()
    pokeapi_map = {}
    defaults_by_prefix = {}
    for p in basics_raw:
        specy = p.get("pokemonspecy") or {}
        names = specy.get("pokemonspeciesnames") or []
        meta = {
            "height": p.get("height"),
            "weight": p.get("weight"),
            "base_experience": p.get("base_experience"),
            "is_legendary": specy.get("is_legendary", False),
            "is_mythical": specy.get("is_mythical", False),
            "generation_id": specy.get("generation_id"),
            "color": (specy.get("pokemoncolor") or {}).get("name"),
            "egg_groups": [g["egggroup"]["name"] for g in (specy.get("pokemonegggroups") or [])],
            "genus": names[0]["genus"] if names else None,
        }
        pokeapi_map[p["name"]] = meta
        if p.get("is_default"):
            defaults_by_prefix[p["name"].split("-")[0]] = meta

    for entry in pokemon_list:
        key_slug = slugify(entry["key"])
        base_slug = slugify(entry["display_name"])
        entry["name"] = key_slug

        meta, source = pokeapi_map.get(key_slug), "exact"
        if meta is None:
            meta, source = pokeapi_map.get(base_slug), "base-species-fallback"
        if meta is None:
            meta, source = defaults_by_prefix.get(base_slug), "base-species-default-variety"

        entry["pokeapi_metadata"] = {**meta, "source": source} if meta else None

    return pokemon_list


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-js", help="Local path to data.js (skips the download)")
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="Output JSON path")
    parser.add_argument("--skip-pokeapi", action="store_true", help="Skip the PokeAPI enrichment pass")
    args = parser.parse_args()

    print("Loading data.js...")
    content = load_data_js(args.data_js)

    print("Building lookup tables (types, abilities, items, moves)...")
    type_map, ability_map, item_map, move_map = build_lookup_tables(content)
    print(f"  types={len(type_map)} abilities={len(ability_map)} items={len(item_map)} moves={len(move_map)}")

    print("Parsing species...")
    pokemon = parse_species(content, type_map, ability_map, item_map, move_map)
    print(f"  {len(pokemon)} species parsed, {sum(p['is_radical_red_exclusive'] for p in pokemon)} exclusive to Radical Red")

    if not args.skip_pokeapi:
        print("Enriching with PokeAPI metadata (one bulk GraphQL call)...")
        pokemon = enrich_with_pokeapi(pokemon)
        unmatched = [p["key"] for p in pokemon if p["pokeapi_metadata"] is None]
        print(f"  {len(pokemon) - len(unmatched)}/{len(pokemon)} matched, unmatched: {unmatched}")

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps({"pokemon": pokemon}, indent=1, ensure_ascii=False))
    print(f"Wrote {out_path} ({out_path.stat().st_size / 1_000_000:.1f} MB)")


if __name__ == "__main__":
    main()
