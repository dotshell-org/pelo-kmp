#!/usr/bin/env python3
"""Build the app's bundled basemap descriptors from an OpenFreeMap light style.

Emits two files per input: the light descriptor the app bundles, and a dark cut of
it. OpenFreeMap publishes `bright` and `liberty` in light only, so the dark halves
of the Standard and 3D basemaps have to be generated.

Both halves drop the layers listed in DROPPED_LAYERS — hospital and school grounds
get their own tint upstream, and the app wants that land to read as ordinary.

The dark transform is semantic, not a blanket filter: every layer is matched to a
role by its id, and the role decides the colour. Surfaces go to near-black, roads
stay clearly lighter than what they cross so the hierarchy the light style encodes
survives, and water, parks and transit POIs keep a recognisable — if desaturated —
hue. Alpha and zoom-interpolation structure are preserved, so layers that fade in
by zoom still do.

Usage:
    python3 tools/build_map_styles.py <upstream.json> <light-out.json> <dark-out.json>
"""
from __future__ import annotations

import json
import re
import sys

# --- palette ---------------------------------------------------------------
BG = "#141414"           # map background, piers
LANDUSE = "#1b1b1b"      # residential, industrial, commercial blocks
LANDUSE_SOFT = "#1f1f1f"  # schools, hospitals, cemeteries, pitches
PARK = "#16241a"         # parks and grass, readably green
WOOD = "#17301d"         # woodland, a shade stronger than park
WATER = "#16283c"        # lakes, rivers, sea
WATERWAY = "#24405e"     # waterway centrelines, lighter so rivers read
SAND = "#2a2717"
ICE = "#23282a"
BUILDING = "#202020"
BUILDING_EDGE = "#2c2c2c"
AEROWAY_FILL = "#1c1c1c"
AEROWAY_LINE = "#2a2a2a"
PEDESTRIAN_AREA = "#242424"  # plazas and pedestrian polygons

MOTORWAY = "#6b5c40"          # major roads keep a hint of their warm identity
MOTORWAY_CASING = "#3a3226"
TRUNK = "#585045"
TRUNK_CASING = "#332e26"
SECONDARY = "#494540"
SECONDARY_CASING = "#2c2a25"
MINOR = "#3b3b3b"
MINOR_CASING = "#262626"
PATH = "#35332e"
RAILWAY = "#3a3a3a"
FERRY = "#2b3d48"
BOUNDARY = "#4a4a55"

PLACE_LABEL = "#e8e8e8"
PLACE_LABEL_SOFT = "#b4b4b4"
ROAD_LABEL = "#9a9a9a"
POI_LABEL = "#9aa0a6"
POI_TRANSIT_LABEL = "#7ea6c9"
WATER_LABEL = "#6f8fbf"
HALO = "#0d0d0d"

# Layers removed from both halves: these paint hospital and school grounds in their
# own tint, and the app wants that land to look like any other block.
DROPPED_LAYERS = ("hospital", "school")

HEX_RE = re.compile(r"^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
FUNC_RE = re.compile(r"^(rgb|rgba|hsl|hsla)\(", re.IGNORECASE)


def is_color(value: object) -> bool:
    return isinstance(value, str) and bool(HEX_RE.match(value) or FUNC_RE.match(value))


def alpha_of(value: str) -> float | None:
    """Alpha of a colour literal, or None when it is fully opaque."""
    match = re.match(r"^(?:rgba|hsla)\(([^)]*)\)$", value.strip(), re.IGNORECASE)
    if match:
        parts = [p.strip() for p in match.group(1).replace("/", ",").split(",")]
        if len(parts) == 4:
            try:
                a = float(parts[3])
                return a if a < 1 else None
            except ValueError:
                return None
    if HEX_RE.match(value):
        digits = value[1:]
        if len(digits) == 8:
            return int(digits[6:8], 16) / 255
        if len(digits) == 4:
            return int(digits[3] * 2, 16) / 255
    return None


def with_alpha(hex_color: str, alpha: float | None) -> str:
    if alpha is None:
        return hex_color
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (1, 3, 5))
    return f"rgba({r},{g},{b},{round(alpha, 3)})"


def role(layer: dict) -> dict[str, str]:
    """Target colour per paint property for one layer, keyed by paint property name."""
    ident = layer["id"].replace("-", "_").lower()
    kind = layer["type"]

    if kind == "symbol":
        if "water_name" in ident or "waterway" in ident:
            text = WATER_LABEL
        elif ident == "poi_transit":
            text = POI_TRANSIT_LABEL
        elif ident.startswith("poi") or ident == "airport":
            text = POI_LABEL
        elif "highway_name" in ident or "road_shield" in ident or "shield" in ident:
            text = ROAD_LABEL
        elif ident in ("label_other", "label_state"):
            text = PLACE_LABEL_SOFT
        else:
            text = PLACE_LABEL
        return {"text-color": text, "text-halo-color": HALO, "icon-halo-color": HALO}

    if kind == "background":
        return {"background-color": BG}

    if ident in ("road_area_pier", "road_pier"):
        return {"fill-color": BG, "line-color": BG}
    # Matched on the full landcover ids, not a bare "ice": that substring also hides
    # inside "service", which would send every service road to the glacier colour.
    if "glacier" in ident or "landcover_ice" in ident:
        return {"fill-color": ICE}
    if "sand" in ident:
        return {"fill-color": SAND}
    if "wood" in ident or "forest" in ident:
        return {"fill-color": WOOD, "fill-outline-color": WOOD}
    if "park" in ident or "grass" in ident or "wetland" in ident:
        return {"fill-color": PARK, "fill-outline-color": PARK, "line-color": PARK}
    if ident.startswith("landuse"):
        soft = any(w in ident for w in ("cemetery", "pitch", "track"))
        tone = LANDUSE_SOFT if soft else LANDUSE
        return {"fill-color": tone, "fill-outline-color": tone}
    if "waterway" in ident:
        return {"line-color": WATERWAY, "fill-color": WATER}
    if ident.startswith("water"):
        return {"fill-color": WATER, "line-color": WATERWAY}
    if ident.startswith("building"):
        return {
            "fill-color": BUILDING,
            "fill-extrusion-color": BUILDING,
            "fill-outline-color": BUILDING_EDGE,
        }
    if ident.startswith("aeroway"):
        return {"fill-color": AEROWAY_FILL, "line-color": AEROWAY_LINE}
    if ident.startswith("boundary"):
        return {"line-color": BOUNDARY}
    if "ferry" in ident or "cablecar" in ident:
        return {"line-color": FERRY}
    if "rail" in ident:
        return {"line-color": RAILWAY}
    if "path" in ident or "pedestrian" in ident:
        return {"line-color": PATH, "fill-color": PATH}
    if "highway_area" in ident:
        return {"fill-color": LANDUSE, "fill-outline-color": MINOR_CASING}
    if "road_area" in ident:
        return {"fill-color": PEDESTRIAN_AREA}

    casing = ident.endswith("_casing")
    if "motorway" in ident:
        tone = MOTORWAY_CASING if casing else MOTORWAY
    elif "trunk" in ident or "primary" in ident:
        tone = TRUNK_CASING if casing else TRUNK
    elif "secondary" in ident or "tertiary" in ident or "link" in ident:
        tone = SECONDARY_CASING if casing else SECONDARY
    elif any(w in ident for w in ("minor", "street", "service", "track")):
        tone = MINOR_CASING if casing else MINOR
    else:
        return {}
    return {"line-color": tone, "fill-color": tone}


def recolor(value: object, target: str) -> object:
    """Replace colour literals in [value], keeping structure and per-literal alpha."""
    if is_color(value):
        return with_alpha(target, alpha_of(value))
    if isinstance(value, list):
        return [recolor(item, target) for item in value]
    return value


def drop_special_zones(style: dict) -> int:
    """Remove the layers in [DROPPED_LAYERS]. Returns how many were dropped."""
    before = len(style["layers"])
    style["layers"] = [
        layer for layer in style["layers"]
        if not any(word in layer["id"].replace("-", "_").lower() for word in DROPPED_LAYERS)
    ]
    return before - len(style["layers"])


def darken(style: dict) -> int:
    """Recolour [style] in place for dark mode. Returns how many properties changed."""
    touched = 0
    for layer in style["layers"]:
        paint = layer.get("paint")
        if not paint:
            continue
        roles = role(layer)

        # Sprite patterns (pedestrian plazas, wetland hatching) are drawn light-on-transparent
        # for a light basemap and glare on a dark one. There is no dark sprite sheet to point
        # at, so the pattern is dropped and the same role's flat colour drawn instead.
        for pattern_prop, color_prop in (("fill-pattern", "fill-color"), ("line-pattern", "line-color")):
            if pattern_prop in paint and color_prop in roles:
                del paint[pattern_prop]
                paint[color_prop] = roles[color_prop]
                touched += 1

        for prop, target in roles.items():
            if prop in paint:
                paint[prop] = recolor(paint[prop], target)
                touched += 1
    return touched


def write(style: dict, path: str) -> None:
    with open(path, "w") as out:
        json.dump(style, out, separators=(",", ":"), ensure_ascii=False)


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    _, source, light_path, dark_path = argv

    light = json.loads(open(source).read())
    dropped = drop_special_zones(light)
    write(light, light_path)
    print(f"{light_path}: dropped {dropped} special-zone layers, {len(light['layers'])} remain")

    dark = json.loads(json.dumps(light))
    touched = darken(dark)
    write(dark, dark_path)
    print(f"{dark_path}: recoloured {touched} paint properties across {len(dark['layers'])} layers")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
