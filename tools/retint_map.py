"""Re-tint the bundled positron basemap onto the charte's sand ramp.

The four anchors come straight from the guide:
    rgb(194, 200, 202) -> #d7c59b      (water)
    rgb(220,224,220)   -> #e8ddbb      (wood / landcover)
    rgb(234, 234, 234) -> #f7f1dc      (roads, paths)
    rgb(242,243,240)   -> #fcfaf1      (background)

Every other neutral in the style is moved onto the same ramp at its own
lightness, so roads, buildings and railways stay in the hierarchy Positron
designed instead of sitting grey on a beige page. Label text follows the
charte's "grey goes black" rule; the blue water labels and the white halos
and casings are left alone.
"""
import json
import pathlib
import sys

STYLE = pathlib.Path("app/src/commonMain/composeResources/files/positron.json")

REPLACEMENTS = [
    # --- the guide's four anchors ---------------------------------------
    ('"rgb(242,243,240)"', '"#fcfaf1"'),    # background, piers
    ('"rgb(194, 200, 202)"', '"#d7c59b"'),  # water
    ('"rgb(220,224,220)"', '"#e8ddbb"'),    # landcover wood
    ('"rgb(234,234,234)"', '"#f7f1dc"'),    # tunnel motorway inner
    ('"rgb(234, 234, 234)"', '"#f7f1dc"'),  # highway path

    # --- remaining fills, placed on the ramp by lightness ---------------
    ('"rgb(230, 233, 229)"', '"#f2ead0"'),  # park, between background and wood
    ('"rgb(234, 234, 230)"', '"#f7f1dc"'),  # landuse residential
    ('"rgb(234, 234, 229)"', '"#f7f1dc"'),  # building fill
    ('"rgb(219, 219, 218)"', '"#e8ddbb"'),  # building outline
    ('"hsl(0,0%,98%)"', '"#fdfbf4"'),       # ice shelf, glacier
    ('"hsl(195,17%,78%)"', '"#d7c59b"'),    # waterway, matches water

    # --- roads, rails and boundaries ------------------------------------
    ('"rgb(213, 213, 213)"', '"#ddd0ae"'),  # motorway / major casing
    ('"hsl(0,0%,88%)"', '"#ece2c6"'),       # minor roads, taxiway, runway casing
    ('"hsla(0,0%,85%,0.69)"', '"rgba(226,214,182,0.69)"'),  # major subtle
    ('"hsla(0,0%,85%,0.53)"', '"rgba(226,214,182,0.53)"'),  # motorway subtle
    ('"#dddddd"', '"#e5d9b7"'),             # railways
    ('"#fafafa"', '"#fdfbf4"'),             # railway dashlines
    ('"hsl(0,0%,70%)"', '"#c0b294"'),       # administrative boundaries

    # --- label text: the charte renders grey type in black ---------------
    ('"hsl(0,0%,66%)"', '"#000"'),          # waterway line labels
    ('"hsl(30,0%,62%)"', '"#000"'),         # path names
    ('"#666"', '"#000"'),                   # street names, airports
    ('"#333"', '"#000"'),                   # other places, states
    ('"#f8f4f0"', '"#fcfaf1"'),             # path-name halo
]


def main() -> int:
    text = STYLE.read_text()
    before = json.loads(text)

    missing = [old for old, _ in REPLACEMENTS if old not in text]
    if missing:
        print("these colours are no longer in the style:", missing, file=sys.stderr)
        return 1

    for old, new in REPLACEMENTS:
        text = text.replace(old, new)

    after = json.loads(text)  # fails loudly if a replacement broke the JSON
    if len(before["layers"]) != len(after["layers"]):
        print("layer count changed", file=sys.stderr)
        return 1

    STYLE.write_text(text)
    print(f"re-tinted {len(REPLACEMENTS)} colours across {len(after['layers'])} layers")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
