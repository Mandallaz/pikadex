#!/usr/bin/env bash
# Fetches front sprites (normal + shiny) for every Radical Red species and copies them into
# app/src/main/assets/radicalred/sprites/, alongside the JSON build_dataset.py produces.
#
# Uses a blobless sparse clone (--filter=blob:none --sparse) of
# JwowSquared/Radical-Red-Pokedex, checking out only graphics/species/ instead of the whole
# repo — that folder alone still holds front, front shiny, back and back shiny sets, but only
# front/front shiny are copied out; PikaDex doesn't use back sprites.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEST="$REPO_ROOT/app/src/main/assets/radicalred/sprites"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Cloning graphics/species/ from JwowSquared/Radical-Red-Pokedex..."
git clone --depth 1 --filter=blob:none --sparse \
  https://github.com/JwowSquared/Radical-Red-Pokedex.git "$WORKDIR/repo"
(cd "$WORKDIR/repo" && git sparse-checkout set graphics/species)

mkdir -p "$DEST/front" "$DEST/front_shiny"
cp "$WORKDIR/repo/graphics/species/front/"*.png "$DEST/front/"
cp "$WORKDIR/repo/graphics/species/front shiny/"*.png "$DEST/front_shiny/"

count=$(find "$DEST" -name '*.png' | wc -l | tr -d ' ')
echo "Copied $count sprites into $DEST"
