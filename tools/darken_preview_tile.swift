// Derives a dark basemap preview tile from its light counterpart.
//
// The picker tiles (composeResources/drawable/visu_*.png) are flat illustrations of one
// scene. Rather than draw the dark ones by hand, each pixel is classified — water, park,
// land, or road/building surface — and repainted from the same palette the dark styles use
// (tools/build_map_styles.py), so the tile matches what the style actually renders.
//
// Land and roads are both near-white in the light tiles and are told apart by tint: the land
// background carries a colour cast (cool in Bright, warm in Liberty) while roads and buildings
// are neutral.
//
// Usage (macOS, needs Xcode's swift):
//   swift tools/darken_preview_tile.swift <light.png> <dark.png>

import Foundation
import AppKit

// Dark-mode palette, matching tools/build_map_styles.py
let BG: (Int, Int, Int) = (0x14, 0x14, 0x14)
let SURFACE: (Int, Int, Int) = (0x3b, 0x3b, 0x3b)   // roads, buildings — the light neutrals
let PARK: (Int, Int, Int) = (0x1e, 0x33, 0x24)
let WATER: (Int, Int, Int) = (0x1b, 0x35, 0x50)

func hueOf(_ r: Double, _ g: Double, _ b: Double, _ mx: Double, _ mn: Double) -> Double {
    let d = mx - mn
    if d == 0 { return 0 }
    var h: Double
    if mx == r { h = 60 * (((g - b) / d).truncatingRemainder(dividingBy: 6)) }
    else if mx == g { h = 60 * (((b - r) / d) + 2) }
    else { h = 60 * (((r - g) / d) + 4) }
    if h < 0 { h += 360 }
    return h
}

let args = CommandLine.arguments
let img = NSImage(contentsOfFile: args[1])!
let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
let w = rep.pixelsWide, h = rep.pixelsHigh

let out = NSBitmapImageRep(
    bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h,
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: w * 4, bitsPerPixel: 32)!

for y in 0..<h {
    for x in 0..<w {
        guard let c = rep.colorAt(x: x, y: y)?.usingColorSpace(.deviceRGB) else { continue }
        let r = c.redComponent, g = c.greenComponent, b = c.blueComponent
        let mx = max(r, max(g, b)), mn = min(r, min(g, b))
        let chroma = mx - mn, light = (mx + mn) / 2
        let hue = hueOf(r, g, b, mx, mn)

        var t: (Int, Int, Int)
        if chroma > 0.11 && hue >= 150 && hue < 255 {
            t = WATER
        } else if chroma > 0.09 && hue >= 60 && hue < 150 {
            t = PARK
        } else if light > 0.90 {
            // Tinted near-whites are the land background; neutral near-whites are the
            // road and building surfaces drawn on top of it.
            let tinted = abs(r - b) >= 0.02
            t = tinted ? BG : SURFACE
        } else {
            // Mid greys (casings, outlines) ride a short ramp between the two.
            let k = min(max(light / 0.90, 0), 1)
            let v = Int(26 + k * 24)
            t = (v, v, v)
        }
        out.setColor(
            NSColor(deviceRed: CGFloat(t.0) / 255, green: CGFloat(t.1) / 255,
                    blue: CGFloat(t.2) / 255, alpha: c.alphaComponent),
            atX: x, y: y)
    }
}
try! out.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
print("wrote \(args[2]) \(w)x\(h)")
