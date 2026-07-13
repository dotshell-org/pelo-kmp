"""Fetch the SYTRAL WFS line layers and write lines.bin in the RLN2 format.

Same data source and same philosophy as the original LYL1 generator (every WFS
trace variant is kept, Rhonexpress is synthesized by hand because it lives in
its own layer without the SYTRAL attributes), but the output is byte-compatible
with the RLN2 format produced by `raptor-gtfs-pipeline --traces`, so the app
parses one single format across the three cities.

Differences with --traces (deliberate):
- ALL trace variants are kept as their own path (RLN2 allows any number of
  paths per line, each tagged with a direction_id), instead of one longest
  shape per direction;
- the WFS "couleur" ("R G B" decimal triplet) is converted to the GTFS-style
  RRGGBB hex that RLN2 stores.

Usage: python tools/build_lines_bin.py   (run from the repo root)
"""

import json
import os
import ssl
import struct
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "https://data.grandlyon.com/geoserver/sytral/ows"
TYPES = [
    "sytral:tcl_sytral.tcllignemf_2_0_0",
    "sytral:tcl_sytral.tcllignetram_2_0_0",
    "sytral:tcl_sytral.tcllignebus_2_0_0",
    "sytral:tcl_sytral.tcllignefluv",
    "sytral:rx_rhonexpress.rxligne_2_0_0"
]

# RLN2 constants — must match raptor-gtfs-pipeline (LinesWriter / LinesSerializer).
MAGIC = b"RLN2"
SCHEMA_VERSION = 2
COORD_SCALE = 1_000_000

# WFS famille_transport -> raw GTFS route_type, as stored by RLN2.
ROUTE_TYPES = {
    "MET": 1,    # metro
    "TRA": 0,    # tram
    "TRAM": 0,   # Rhonexpress (synthesized props use the long form)
    "FUN": 7,    # funicular
    "BAT": 4,    # navette fluviale (ferry)
}
DEFAULT_ROUTE_TYPE = 3  # bus


def fetch_typename(typename):
    params = {
        "SERVICE": "WFS",
        "VERSION": "2.0.0",
        "request": "GetFeature",
        "outputFormat": "application/json",
        "SRSNAME": "EPSG:4326",
        "count": 10000,
        "typename": typename
    }
    url = f"{BASE_URL}?{urllib.parse.urlencode(params)}"
    print(f"Fetching {typename}...")
    req = urllib.request.Request(url)
    try:
        with urllib.request.urlopen(req) as response:
            return json.loads(response.read().decode('utf-8'))
    except (urllib.error.URLError, ssl.SSLError):
        # Corporate SSL interception breaks certificate validation on this
        # machine; retry without verification (public open data anyway).
        context = ssl._create_unverified_context()
        with urllib.request.urlopen(req, context=context) as response:
            return json.loads(response.read().decode('utf-8'))


def parse_multi_line_string(coords):
    if not coords or not isinstance(coords, list):
        return []
    if isinstance(coords[0], list) and isinstance(coords[0][0], (float, int)):
        return [coords]
    if isinstance(coords[0], list) and isinstance(coords[0][0], list):
        if isinstance(coords[0][0][0], (float, int)):
            return coords
        if isinstance(coords[0][0][0], list):
            res = []
            for c in coords:
                res.extend(parse_multi_line_string(c))
            return res
    return []


def color_to_hex(raw):
    """WFS 'R G B' decimal triplet (or '#RRGGBB') -> GTFS-style RRGGBB hex."""
    if not raw:
        return ""
    value = str(raw).strip()
    if value.startswith("#"):
        return value[1:].upper() if len(value) in (7, 9) else ""
    parts = [p for p in value.replace(",", " ").split() if p]
    if len(parts) != 3:
        return ""
    try:
        channels = [max(0, min(255, int(p))) for p in parts]
    except ValueError:
        return ""
    return "".join(f"{c:02X}" for c in channels)


def direction_id(sens):
    """WFS 'sens' ('Aller'/'Retour', case varies, often absent) -> 0/1."""
    return 1 if (sens or "").strip().upper().startswith("R") else 0


def main():
    # line name -> {"route_type", "color", "paths": [(direction_id, points)]}
    lines = {}

    for t in TYPES:
        data = fetch_typename(t)
        raw_features = data.get("features", [])
        if not raw_features:
            raw_features = data.get("featureMember", []) or data.get("featureMembers", [])

        for rf in raw_features:
            geom = rf.get("geometry") or rf.get("the_geom") or rf.get("geom")
            if not geom or geom.get("type") not in ("LineString", "MultiLineString"):
                continue

            coords = geom.get("coordinates")
            paths = parse_multi_line_string(coords) if geom["type"] == "MultiLineString" else [coords]
            paths = [p for p in paths if p and len(p) >= 2]
            if not paths:
                continue

            orig_props = rf.get("properties", {})
            if "rx_" in t:
                # Rhonexpress: its layer has none of the SYTRAL attributes, so the
                # identity is synthesized (same values as the historical generator).
                name = "RX"
                transport = "TRAM"
                color = "#E30613"
                sens = "ALLER"
            else:
                name = orig_props.get("ligne", "")
                transport = orig_props.get("famille_transport", "")
                color = orig_props.get("couleur")
                sens = orig_props.get("sens")

            if not name:
                continue

            entry = lines.setdefault(name, {
                "route_type": ROUTE_TYPES.get(transport, DEFAULT_ROUTE_TYPE),
                "color": color_to_hex(color),
                "paths": [],
            })
            if not entry["color"]:
                entry["color"] = color_to_hex(color)
            entry["paths"].extend((direction_id(sens), path) for path in paths)

    total_paths = sum(len(entry["paths"]) for entry in lines.values())
    total_points = sum(len(p) for entry in lines.values() for _, p in entry["paths"])
    print(f"Collected {len(lines)} lines, {total_paths} trace variants, {total_points} points")

    out_dir = "app/src/commonMain/composeResources/files/lyon"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "lines.bin")

    def write_string(f, value):
        encoded = value.encode("utf-8")
        f.write(struct.pack("<H", len(encoded)))
        f.write(encoded)

    with open(out_path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<H", SCHEMA_VERSION))
        f.write(struct.pack("<I", COORD_SCALE))
        f.write(struct.pack("<I", len(lines)))

        for internal_id, (name, entry) in enumerate(lines.items()):
            f.write(struct.pack("<I", internal_id))
            write_string(f, name)
            write_string(f, entry["color"])
            write_string(f, "FFFFFF")  # text color: white, like the network's own badges
            f.write(struct.pack("<H", entry["route_type"]))
            f.write(struct.pack("<H", len(entry["paths"])))

            for direction, path in entry["paths"]:
                f.write(struct.pack("<H", direction))
                f.write(struct.pack("<I", len(path)))

                # Columnar, delta-encoded, first value absolute (RLN2 layout).
                previous = 0
                for pt in path:
                    scaled = round(pt[0] * COORD_SCALE)
                    f.write(struct.pack("<i", scaled - previous))
                    previous = scaled
                previous = 0
                for pt in path:
                    scaled = round(pt[1] * COORD_SCALE)
                    f.write(struct.pack("<i", scaled - previous))
                    previous = scaled

    print(f"Generated {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == "__main__":
    main()
