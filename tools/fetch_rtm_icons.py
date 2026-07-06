#!/usr/bin/env python3
"""Fetch the official RTM line pictograms from Wikimedia Commons and convert
them to Android/Compose vector drawables.

- Enumerates "Category:Line numbers of buses in Marseille" plus the explicit
  metro/tram/boat pictograms (all public domain, extracted from the official
  RTM network map).
- Keeps only pictograms matching a route_short_name of the RTM GTFS feed.
- Converts each SVG to a vector drawable by flattening every group/path
  transform into absolute path coordinates (the sources are Inkscape path-only
  files: no <text>, and their clipPaths cover the whole page so they are
  ignored).
- Names files per LineIconResolver.getDrawableNameForLineName: lowercase,
  underscore-prefixed when the name starts with a digit ("M1" -> m1.xml,
  "35T" -> _35t.xml).

Usage:
    python tools/fetch_rtm_icons.py --gtfs Z:/Android/Projects/GTFS_RTM \
        --out app/src/commonMain/composeResources/drawable [--cache DIR]
"""

import argparse
import json
import math
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from xml.etree import ElementTree as ET

USER_AGENT = "Massilia-IconFetcher/1.0 (contact@dotshell.eu)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
FILEPATH_URL = "https://commons.wikimedia.org/wiki/Special:FilePath/"

BUS_CATEGORY = "Category:Line numbers of buses in Marseille"

# Explicit pictograms outside the bus category. Boat pictograms are shared by
# several GTFS lines, hence the list of target line names.
EXPLICIT_FILES = {
    "Icône Métro M1 Marseille.svg": ["M1"],
    "Icône Métro M2 Marseille.svg": ["M2"],
    "Icône Tramway T1 Marseille.svg": ["T1"],
    "Icône Tramway T2 Marseille.svg": ["T2"],
    "Icône Tramway T3 Marseille.svg": ["T3"],
    "Icône Bateau lanavette Marseille.svg": ["NAV1", "NAV2", "NAV3"],
    "Icône Bateau leferryboat Marseille.svg": ["FERRY"],
}

BUS_FILE_RE = re.compile(r"^Icône Bus (?P<token>[0-9A-Za-z]+) Marseille\.svg$")

SVG_NS = "{http://www.w3.org/2000/svg}"

# ─── Wikimedia helpers ────────────────────────────────────────────────────────


def api_get(params: dict) -> dict:
    query = urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(f"{COMMONS_API}?{query}", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def list_category_files(category: str) -> list[str]:
    titles, cont = [], {}
    while True:
        data = api_get({
            "action": "query",
            "list": "categorymembers",
            "cmtitle": category,
            "cmtype": "file",
            "cmlimit": "500",
            **cont,
        })
        titles += [m["title"].removeprefix("File:") for m in data["query"]["categorymembers"]]
        cont = data.get("continue")
        if not cont:
            return titles


def download(filename: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 0:
        return
    url = FILEPATH_URL + urllib.parse.quote(filename)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        dest.write_bytes(resp.read())


# ─── Affine transforms ────────────────────────────────────────────────────────

IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)  # (a, b, c, d, e, f) column-major SVG matrix


def mat_mul(m1, m2):
    a1, b1, c1, d1, e1, f1 = m1
    a2, b2, c2, d2, e2, f2 = m2
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


def mat_apply(m, x, y):
    a, b, c, d, e, f = m
    return a * x + c * y + e, b * x + d * y + f


TRANSFORM_RE = re.compile(r"(matrix|translate|scale|rotate)\s*\(([^)]*)\)")


def parse_transform(text: str):
    matrix = IDENTITY
    if not text:
        return matrix
    for kind, args in TRANSFORM_RE.findall(text):
        values = [float(v) for v in re.split(r"[\s,]+", args.strip()) if v]
        if kind == "matrix":
            m = tuple(values)
        elif kind == "translate":
            tx = values[0]
            ty = values[1] if len(values) > 1 else 0.0
            m = (1.0, 0.0, 0.0, 1.0, tx, ty)
        elif kind == "scale":
            sx = values[0]
            sy = values[1] if len(values) > 1 else sx
            m = (sx, 0.0, 0.0, sy, 0.0, 0.0)
        elif kind == "rotate":
            angle = math.radians(values[0])
            cos_a, sin_a = math.cos(angle), math.sin(angle)
            m = (cos_a, sin_a, -sin_a, cos_a, 0.0, 0.0)
            if len(values) == 3:
                cx, cy = values[1], values[2]
                m = mat_mul(mat_mul((1, 0, 0, 1, cx, cy), m), (1, 0, 0, 1, -cx, -cy))
        else:
            continue
        matrix = mat_mul(matrix, m)
    return matrix


# ─── SVG path parsing and transformation ─────────────────────────────────────

NUMBER_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
COMMAND_RE = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|(" + NUMBER_RE.pattern + ")")

ARG_COUNTS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def tokenize_path(d: str):
    for cmd, num in COMMAND_RE.findall(d):
        if cmd:
            yield ("cmd", cmd)
        else:
            yield ("num", float(num))


def fmt(v: float) -> str:
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def transform_path(d: str, matrix) -> tuple[str, list[tuple[float, float]]]:
    """Convert an SVG path to absolute commands with `matrix` applied.

    Returns the new path string and every transformed on-curve/control point
    (used for bounding-box sanity checks).
    """
    a, b, c, dd, _, _ = matrix
    det = a * dd - b * c
    uniform_scale = math.sqrt(abs(det))

    tokens = list(tokenize_path(d))
    out: list[str] = []
    points: list[tuple[float, float]] = []
    i = 0
    cx = cy = 0.0          # current point (untransformed space)
    sx_ = sy_ = 0.0        # subpath start

    def emit_point(x, y):
        tx, ty = mat_apply(matrix, x, y)
        points.append((tx, ty))
        return f"{fmt(tx)},{fmt(ty)}"

    while i < len(tokens):
        kind, value = tokens[i]
        if kind != "cmd":
            raise ValueError(f"Expected command, got number {value}")
        cmd = value
        i += 1
        upper = cmd.upper()
        relative = cmd.islower()
        first_pair = True

        if upper == "Z":
            out.append("Z")
            cx, cy = sx_, sy_
            continue

        # Consume repeated argument groups for this command
        while i < len(tokens) and tokens[i][0] == "num":
            args = []
            for _ in range(ARG_COUNTS[upper]):
                if i >= len(tokens) or tokens[i][0] != "num":
                    raise ValueError(f"Path args underflow for {cmd}")
                args.append(tokens[i][1])
                i += 1

            if upper == "H":
                x = cx + args[0] if relative else args[0]
                out.append("L" + emit_point(x, cy))
                cx = x
            elif upper == "V":
                y = cy + args[0] if relative else args[0]
                out.append("L" + emit_point(cx, y))
                cy = y
            elif upper in ("M", "L", "T"):
                x, y = args
                if relative:
                    x, y = cx + x, cy + y
                letter = upper if not (upper == "M" and not first_pair) else "L"
                out.append(letter + emit_point(x, y))
                cx, cy = x, y
                if upper == "M" and first_pair:
                    sx_, sy_ = x, y
            elif upper in ("C", "S", "Q"):
                pairs = [(args[j], args[j + 1]) for j in range(0, len(args), 2)]
                if relative:
                    pairs = [(cx + px, cy + py) for px, py in pairs]
                out.append(upper + " ".join(emit_point(px, py) for px, py in pairs))
                cx, cy = pairs[-1]
            elif upper == "A":
                rx, ry, xrot, large_arc, sweep, x, y = args
                if relative:
                    x, y = cx + x, cy + y
                if abs(abs(a) - abs(dd)) > 1e-6 or abs(b) > 1e-9 or abs(c) > 1e-9:
                    raise ValueError("Arc with non-uniform/rotating transform not supported")
                new_sweep = int(sweep) if det > 0 else 1 - int(sweep)
                end = emit_point(x, y)
                out.append(
                    f"A{fmt(rx * uniform_scale)},{fmt(ry * uniform_scale)} "
                    f"{fmt(xrot)} {int(large_arc)} {new_sweep} {end}"
                )
                cx, cy = x, y
            first_pair = False

    return " ".join(out), points


# ─── SVG document → vector drawable ──────────────────────────────────────────

FILL_RE = re.compile(r"fill\s*:\s*([^;]+)")
FILL_OPACITY_RE = re.compile(r"fill-opacity\s*:\s*([^;]+)")
FILL_RULE_RE = re.compile(r"fill-rule\s*:\s*([^;]+)")


def path_fill(el) -> tuple[str | None, float, str]:
    style = el.get("style", "")
    fill = el.get("fill")
    m = FILL_RE.search(style)
    if m:
        fill = m.group(1).strip()
    opacity = 1.0
    m = FILL_OPACITY_RE.search(style)
    if m:
        opacity = float(m.group(1))
    elif el.get("fill-opacity"):
        opacity = float(el.get("fill-opacity"))
    rule = el.get("fill-rule") or (FILL_RULE_RE.search(style).group(1).strip() if FILL_RULE_RE.search(style) else "nonzero")
    return fill, opacity, rule


def to_argb(fill: str, opacity: float) -> str:
    fill = fill.strip().lower()
    if not fill.startswith("#"):
        named = {"white": "#ffffff", "black": "#000000"}
        fill = named.get(fill, fill)
    hex_part = fill.lstrip("#")
    if len(hex_part) == 3:
        hex_part = "".join(ch * 2 for ch in hex_part)
    alpha = round(max(0.0, min(1.0, opacity)) * 255)
    return f"#{alpha:02X}{hex_part.upper()}"


def convert_svg(svg_path: Path) -> str:
    root = ET.parse(svg_path).getroot()
    view_box = root.get("viewBox")
    if not view_box:
        raise ValueError("Missing viewBox")
    min_x, min_y, vb_w, vb_h = (float(v) for v in view_box.replace(",", " ").split())

    paths_out = []
    all_points: list[tuple[float, float]] = []

    def walk(el, matrix):
        tag = el.tag.split("}")[-1]
        if tag in ("defs", "namedview", "metadata", "clipPath"):
            return
        matrix = mat_mul(matrix, parse_transform(el.get("transform", "")))
        if tag == "path":
            fill, opacity, rule = path_fill(el)
            if fill is None or fill == "none":
                return
            data, points = transform_path(el.get("d", ""), matrix)
            if not data:
                return
            all_points.extend(points)
            fill_type = ' android:fillType="evenOdd"' if rule == "evenodd" else ""
            paths_out.append(
                f'    <path\n        android:pathData="{data}"\n'
                f'        android:fillColor="{to_argb(fill, opacity)}"{fill_type} />'
            )
            return
        for child in el:
            walk(child, matrix)

    # Normalize a non-zero viewBox origin into the root transform.
    walk(root, (1.0, 0.0, 0.0, 1.0, -min_x, -min_y))

    if not paths_out:
        raise ValueError("No visible paths")

    # Sanity check: flattened geometry must stay within the viewport (5% slack).
    slack_x, slack_y = vb_w * 0.05, vb_h * 0.05
    for px, py in all_points:
        if not (-slack_x <= px <= vb_w + slack_x and -slack_y <= py <= vb_h + slack_y):
            raise ValueError(f"Point ({px:.3f}, {py:.3f}) outside viewport {vb_w}x{vb_h}")

    body = "\n".join(paths_out)
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="28dp"\n'
        '    android:height="28dp"\n'
        f'    android:viewportWidth="{fmt(vb_w)}"\n'
        f'    android:viewportHeight="{fmt(vb_h)}">\n'
        f"{body}\n"
        "</vector>\n"
    )


# ─── Naming (mirrors LineIconResolver.getDrawableNameForLineName) ───────────


def drawable_name(line: str) -> str:
    lower = line.lower()
    return f"_{lower}" if lower[0].isdigit() else lower


# ─── Main ─────────────────────────────────────────────────────────────────────


def gtfs_route_names(gtfs_dir: Path) -> set[str]:
    import csv

    with open(gtfs_dir / "routes.txt", encoding="utf-8-sig") as f:
        return {row["route_short_name"].strip() for row in csv.DictReader(f)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gtfs", required=True, type=Path, help="RTM GTFS directory (routes.txt)")
    parser.add_argument("--out", required=True, type=Path, help="composeResources/drawable directory")
    parser.add_argument("--cache", type=Path, default=Path("build/rtm_icons_cache"), help="SVG download cache")
    args = parser.parse_args()

    routes = gtfs_route_names(args.gtfs)
    args.cache.mkdir(parents=True, exist_ok=True)
    args.out.mkdir(parents=True, exist_ok=True)

    # filename -> list of GTFS line names it provides
    wanted: dict[str, list[str]] = {}
    for filename, lines in EXPLICIT_FILES.items():
        targets = [line for line in lines if line in routes]
        if targets:
            wanted[filename] = targets

    print("Enumerating bus pictograms on Wikimedia Commons…")
    for filename in list_category_files(BUS_CATEGORY):
        m = BUS_FILE_RE.match(filename)
        if m and m.group("token").upper() in routes:
            wanted.setdefault(filename, []).append(m.group("token").upper())

    print(f"{len(wanted)} pictogram files to fetch for {len(routes)} GTFS lines")

    converted: dict[str, str] = {}  # line -> drawable file
    failures: list[tuple[str, str]] = []
    for filename, lines in sorted(wanted.items()):
        svg_file = args.cache / filename
        try:
            download(filename, svg_file)
            xml = convert_svg(svg_file)
        except Exception as exc:  # noqa: BLE001 — report and continue
            failures.append((filename, str(exc)))
            continue
        for line in lines:
            out_name = drawable_name(line) + ".xml"
            (args.out / out_name).write_text(xml, encoding="utf-8")
            converted[line] = out_name

    missing = sorted(routes - set(converted))
    print(f"\nConverted {len(converted)} line icons.")
    if failures:
        print(f"{len(failures)} conversion failure(s):")
        for name, err in failures:
            print(f"  - {name}: {err}")
    if missing:
        print(f"{len(missing)} line(s) without pictogram (colored-badge fallback will be used):")
        print("  " + ", ".join(missing))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
