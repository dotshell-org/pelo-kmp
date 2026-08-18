"""Convert the network's line pictograms (SVG) into Compose vector drawables.

The badges TCL/SYTRAL publishes are plain SVG; Compose Multiplatform resources
want Android `<vector>` XML, so every pictogram is transcoded once here and
committed under `composeResources/drawable`. File names follow the rule
`LineIconResolver.getDrawableNameForLineName` applies at runtime: lowercase the
line name, and prefix an underscore when it starts with a digit ("104A" ->
`_104a.xml`, "C20EX" -> `c20ex.xml`).

The source SVGs only ever use a small subset of the format — `<g>` (optionally
with a `translate()`), `<path>`, `<rect>` and `<polygon>`, flat fills, no
strokes or gradients — so the conversion is a direct structural rewrite rather
than a rasterisation. Path data is copied verbatim: Compose's `PathParser`
takes the same lenient SVG grammar the browser does.

Two details worth knowing:
- SVG defaults an unset `fill` to black, whereas a `<path>` with no
  `android:fillColor` is simply not drawn by Compose. Shapes that inherit no
  fill are therefore written out as explicit `#FF000000`.
- `fill="none"` shapes are invisible bounding boxes and are dropped.

Usage: python tools/build_line_badges.py <svg-dir>   (run from the repo root)
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
# The badges live in the shared module; `app` only carries the Android entry point. This path
# followed the module when the code moved, and the tool did not.
OUT_DIR = os.path.join("shared", "src", "commonMain", "composeResources", "drawable")

# Compose resource file names are Kotlin-accessor safe: lowercase alphanumerics
# and underscores only, never leading with a digit.
VALID_NAME = re.compile(r"[a-z_][a-z0-9_]*")

NAMED_COLORS = {"white": "FFFFFF", "black": "000000", "red": "FF0000"}


def drawable_name(line_name):
    """Mirror of LineIconResolver.getDrawableNameForLineName."""
    lower = line_name.lower()
    return ("_" + lower) if lower[:1].isdigit() else lower


def parse_color(value):
    """SVG paint -> #AARRGGBB, or None for 'none'."""
    raw = value.strip()
    if raw == "none":
        return None

    rgb = re.fullmatch(r"rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)", raw)
    if rgb:
        return "#FF" + "".join(f"{int(c):02X}" for c in rgb.groups())

    if raw.startswith("#"):
        digits = raw[1:]
        if len(digits) == 3:  # #fff -> #ffffff
            digits = "".join(c * 2 for c in digits)
        if len(digits) == 6:
            return "#FF" + digits.upper()
        raise ValueError(f"unsupported hex colour {raw!r}")

    if raw.lower() in NAMED_COLORS:
        return "#FF" + NAMED_COLORS[raw.lower()]

    raise ValueError(f"unsupported colour {raw!r}")


def num(value):
    """Trim a float to a compact literal ('9.0' -> '9', '26.4450' -> '26.445')."""
    return f"{round(float(value), 4):g}"


def rect_to_path(el):
    x, y = num(el.get("x", 0)), num(el.get("y", 0))
    w, h = num(el.get("width", 0)), num(el.get("height", 0))
    return f"M{x} {y}h{w}v{h}h-{w}z"


def polygon_to_path(el):
    coords = [c for c in re.split(r"[\s,]+", el.get("points", "").strip()) if c]
    pairs = [f"{coords[i]} {coords[i + 1]}" for i in range(0, len(coords) - 1, 2)]
    return "M" + "L".join(pairs) + "Z"


def parse_translate(transform):
    match = re.fullmatch(
        r"translate\(\s*(-?[\d.]+)\s*(?:[,\s]\s*(-?[\d.]+)\s*)?\)", transform.strip()
    )
    if not match:
        raise ValueError(f"unsupported transform {transform!r}")
    return float(match.group(1)), float(match.group(2) or 0)


def tag_of(el):
    return el.tag.replace(SVG_NS, "")


def collect(el, inherited_fill, out):
    """Flatten the SVG tree into (fillColor, pathData, translate) tuples."""
    for child in el:
        name = tag_of(child)
        fill = child.get("fill", inherited_fill)

        if name == "g":
            translate = child.get("transform")
            offset = parse_translate(translate) if translate else (0.0, 0.0)
            nested = []
            collect(child, fill, nested)
            if offset == (0.0, 0.0):
                out.extend(nested)
            else:
                # Non-trivial translate: keep it as a real group so the path
                # data stays untouched.
                out.append(("group", offset, nested))
            continue

        if name == "path":
            data = child.get("d", "").strip()
        elif name == "rect":
            data = rect_to_path(child)
        elif name == "polygon":
            data = polygon_to_path(child)
        else:
            raise ValueError(f"unsupported element <{name}>")

        # An unset fill is black in SVG, but "don't draw" in Compose.
        color = parse_color(fill) if fill is not None else "#FF000000"
        if color is None or not data:
            continue
        out.append(("path", color, data))


def render(nodes, indent):
    lines = []
    pad = " " * indent
    for node in nodes:
        if node[0] == "group":
            (dx, dy), children = node[1], node[2]
            lines.append(f"{pad}<group")
            lines.append(f'{pad}    android:translateX="{num(dx)}"')
            lines.append(f'{pad}    android:translateY="{num(dy)}">')
            lines.extend(render(children, indent + 4))
            lines.append(f"{pad}</group>")
        else:
            _, color, data = node
            lines.append(f"{pad}<path")
            lines.append(f'{pad}    android:fillColor="{color}"')
            lines.append(f'{pad}    android:pathData="{data}"/>')
    return lines


def convert(svg_path):
    root = ET.parse(svg_path).getroot()

    view_box = [float(v) for v in re.split(r"[\s,]+", root.get("viewBox").strip())]
    if view_box[0] or view_box[1]:
        raise ValueError(f"viewBox origin must be 0 0, got {view_box}")
    width, height = num(view_box[2]), num(view_box[3])

    nodes = []
    collect(root, root.get("fill"), nodes)
    if not nodes:
        raise ValueError("no drawable shape")

    out = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{width}dp"',
        f'    android:height="{height}dp"',
        f'    android:viewportWidth="{width}"',
        f'    android:viewportHeight="{height}">',
    ]
    out.extend(render(nodes, 4))
    out.append("</vector>")
    return "\n".join(out) + "\n"


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: python tools/build_line_badges.py <svg-dir>")
    src = sys.argv[1]
    if not os.path.isdir(OUT_DIR):
        sys.exit(f"run from the repo root: {OUT_DIR} not found")

    existing = {f[:-4] for f in os.listdir(OUT_DIR) if f.endswith(".xml")}
    written, added, skipped, failed = [], [], [], []

    for file_name in sorted(os.listdir(src)):
        if not file_name.endswith(".svg"):
            continue
        name = drawable_name(file_name[:-4])
        if not VALID_NAME.fullmatch(name):
            skipped.append((file_name, "not a valid resource name"))
            continue
        try:
            xml = convert(os.path.join(src, file_name))
        except Exception as error:  # noqa: BLE001 - report and keep going
            failed.append((file_name, str(error)))
            continue

        with open(os.path.join(OUT_DIR, name + ".xml"), "w", encoding="utf-8", newline="\n") as handle:
            handle.write(xml)
        (written if name in existing else added).append(name)

    print(f"replaced {len(written)} badge(s), added {len(added)}")
    if added:
        print("  added:", ", ".join(sorted(added)))
    for file_name, reason in skipped:
        print(f"  skipped {file_name}: {reason}")
    for file_name, reason in failed:
        print(f"  FAILED  {file_name}: {reason}")

    stale = sorted(existing - set(written) - set(added))
    if stale:
        print(f"\n{len(stale)} drawable(s) with no matching SVG (left untouched):")
        print("  " + ", ".join(stale))

    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
