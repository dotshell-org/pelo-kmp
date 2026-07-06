#!/usr/bin/env python3
"""Generate every Massilia app icon asset from one vector definition.

Design (approved): white silhouette of Notre-Dame de la Garde on a black
square — dome and lantern on the left, bell tower with its dark window,
tapered crown, pedestal and statue on the right, all sitting on the hill.

Outputs:
- massilia-icon.png (1024, repo root, README)
- app/src/commonMain/composeResources/drawable/ic_launcher_foreground.png (1536)
- app/src/androidMain/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi}/ic_launcher_foreground.png
- app/src/androidMain/res/mipmap-*/ic_launcher.png (rounded square) and
  ic_launcher_round.png (circle), alpha-masked like the originals
- app/src/androidMain/res/drawable/ic_launcher_monochrome.xml (themed icon)
- iosApp/Assets.xcassets/AppIcon.appiconset/appicon-1024.png

Usage: python tools/generate_app_icon.py   (run from the repo root)
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
CANVAS = 1024.0  # design space
SUPERSAMPLE = 4

WHITE = 255
BLACK = 0


# ─── Geometry helpers (design space → pixels) ────────────────────────────────


def quad_points(p0, c, p1, steps=48):
    """Flatten a quadratic bezier into points."""
    return [
        (
            (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t**2 * p1[0],
            (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t**2 * p1[1],
        )
        for t in (i / steps for i in range(steps + 1))
    ]


def ellipse_arc_points(cx, cy, rx, ry, deg0, deg1, steps=64):
    import math

    return [
        (cx + rx * math.cos(math.radians(a)), cy + ry * math.sin(math.radians(a)))
        for a in (deg0 + (deg1 - deg0) * i / steps for i in range(steps + 1))
    ]


class Painter:
    """Draws the design-space shapes onto a PIL canvas of size `px`."""

    def __init__(self, draw: ImageDraw.ImageDraw, px: float):
        self.d = draw
        self.k = px / CANVAS

    def _pt(self, p):
        return (p[0] * self.k, p[1] * self.k)

    def polygon(self, points, fill):
        self.d.polygon([self._pt(p) for p in points], fill=fill)

    def rect(self, x, y, w, h, fill, radius=0.0):
        box = [x * self.k, y * self.k, (x + w) * self.k, (y + h) * self.k]
        if radius > 0:
            self.d.rounded_rectangle(box, radius=radius * self.k, fill=fill)
        else:
            self.d.rectangle(box, fill=fill)

    def ellipse(self, cx, cy, rx, ry, fill):
        self.d.ellipse(
            [(cx - rx) * self.k, (cy - ry) * self.k, (cx + rx) * self.k, (cy + ry) * self.k],
            fill=fill,
        )


def draw_silhouette(p: Painter):
    """The Notre-Dame de la Garde silhouette (white), then the dark window."""
    # Hill: lens between a quadratic curve and its chord
    p.polygon(quad_points((175, 865), (512, 730), (849, 865)), WHITE)

    # Left block: nave, drum, dome, lantern
    p.rect(285, 640, 190, 170, WHITE)
    p.rect(310, 585, 140, 70, WHITE)
    p.polygon(
        ellipse_arc_points(380, 598, 82, 98, 180, 360) + [(462, 606), (298, 606)],
        WHITE,
    )
    p.rect(366, 465, 28, 50, WHITE)
    p.ellipse(380, 455, 11, 11, WHITE)

    # Tower: shaft, belfry, cornice, tapered crown and its cap
    p.rect(520, 425, 160, 385, WHITE)
    p.rect(502, 295, 196, 145, WHITE, radius=10)
    p.rect(494, 280, 212, 24, WHITE, radius=8)
    p.polygon([(548, 282), (652, 282), (636, 200), (564, 200)], WHITE)
    p.ellipse(600, 201, 37, 13, WHITE)

    # Pedestal and statue (Vierge à l'Enfant, simplified)
    p.rect(576, 172, 48, 32, WHITE, radius=6)
    p.polygon(
        quad_points((581, 178), (577, 140), (594, 112))
        + quad_points((594, 112), (600, 103), (606, 112))
        + quad_points((606, 112), (623, 140), (619, 178)),
        WHITE,
    )
    p.ellipse(600, 93, 17, 17, WHITE)

    # Belfry window (dark cutout)
    p.polygon(
        [(572, 425), (572, 352)]
        + quad_points((572, 352), (572, 323), (600, 323))
        + quad_points((600, 323), (628, 323), (628, 352))
        + [(628, 425)],
        BLACK,
    )


def render_square(px: int) -> Image.Image:
    """Full-bleed black square with the white silhouette, grayscale."""
    big = px * SUPERSAMPLE
    im = Image.new("L", (big, big), BLACK)
    draw_silhouette(Painter(ImageDraw.Draw(im), big))
    return im.resize((px, px), Image.LANCZOS)


def render_masked(px: int, round_mask: bool) -> Image.Image:
    """Legacy launcher icon: 6.25% inset, rounded-square or circular alpha mask."""
    big = px * SUPERSAMPLE
    art = Image.new("L", (big, big), BLACK)
    draw_silhouette(Painter(ImageDraw.Draw(art), big))

    inset = round(big * 0.0625)
    mask = Image.new("L", (big, big), 0)
    md = ImageDraw.Draw(mask)
    if round_mask:
        md.ellipse([inset, inset, big - inset, big - inset], fill=255)
    else:
        md.rounded_rectangle([inset, inset, big - inset, big - inset], radius=big * 0.1, fill=255)

    out = Image.merge("LA", (art, mask))
    return out.resize((px, px), Image.LANCZOS)


MONOCHROME_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <group
        android:pivotX="512"
        android:pivotY="512"
        android:scaleX="0.72"
        android:scaleY="0.72"
        android:translateY="30">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M175,865 Q512,730 849,865 Z
                M285,640 h190 v170 h-190 Z
                M310,585 h140 v70 h-140 Z
                M298,606 v-8 A82,98 0 0 1 462,598 v8 Z
                M366,465 h28 v50 h-28 Z
                M380,444 a11,11 0 1 1 -0.1,0 Z
                M520,425 h160 v385 h-160 Z
                M502,295 h196 v145 h-196 Z
                M494,280 h212 v24 h-212 Z
                M548,282 L652,282 L636,200 L564,200 Z
                M563,201 a37,13 0 1 1 74,0 a37,13 0 1 1 -74,0 Z
                M576,172 h48 v32 h-48 Z
                M581,178 Q577,140 594,112 Q600,103 606,112 Q623,140 619,178 Z
                M583,93 a17,17 0 1 1 34,0 a17,17 0 1 1 -34,0 Z" />
        <path
            android:fillColor="#FF000000"
            android:pathData="M572,425 L572,352 Q572,323 600,323 Q628,323 628,352 L628,425 Z" />
    </group>
</vector>
"""


def main() -> None:
    res = ROOT / "app/src/androidMain/res"

    # README / repo icon
    render_square(1024).convert("RGB").save(ROOT / "massilia-icon.png")

    # Compose resources foreground (used in-app: settings & consent screens)
    render_square(1536).save(
        ROOT / "app/src/commonMain/composeResources/drawable/ic_launcher_foreground.png"
    )

    # Adaptive-icon foreground per density (full-bleed, background is black too)
    for density, px in [("mdpi", 512), ("hdpi", 768), ("xhdpi", 1024), ("xxhdpi", 1536)]:
        render_square(px).save(res / f"drawable-{density}/ic_launcher_foreground.png")

    # Legacy mipmaps (baked masks, LA like the originals)
    for density, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        render_masked(px, round_mask=False).save(res / f"mipmap-{density}/ic_launcher.png")
        render_masked(px, round_mask=True).save(res / f"mipmap-{density}/ic_launcher_round.png")

    # Themed (monochrome) icon
    (res / "drawable/ic_launcher_monochrome.xml").write_text(MONOCHROME_XML, encoding="utf-8")

    # iOS app icon (opaque, no alpha recommended by Apple — RGB)
    render_square(1024).convert("RGB").save(
        ROOT / "iosApp/Assets.xcassets/AppIcon.appiconset/appicon-1024.png"
    )

    print("App icon assets generated.")


if __name__ == "__main__":
    main()
