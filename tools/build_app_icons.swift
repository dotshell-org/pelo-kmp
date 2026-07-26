// Regenerates every launcher icon from the brand mark.
//
// Source of truth is the in-app mark (composeResources/drawable/pelo_mark_dark.png):
// white ink on black. Its ink is lifted into an alpha mask, trimmed to the mark's
// bounding box, then redrawn centered on the brand dark (#1D1D1B) for each target.
//
// Sizing — the whole point of this script. Every launcher shows the icon inside a tile,
// and the ink is scaled to TILE_RATIO of that tile's width everywhere, so the mark reads
// the same on iOS, on a pre-26 Android launcher and behind an adaptive mask. The catch is
// the adaptive layers: they are 108dp but only the centre 72dp survives the mask, so their
// ink is TILE_RATIO * 72/108 of the layer. Getting that ratio wrong is what made the icon
// look tiny — with the ink at 42% of a layer that is then inset another 20%, only launchers
// that renormalize small icons (Samsung, MIUI) showed it at a sensible size.
//
// Usage (macOS, needs Xcode's swift), from the repo root:
//   swift tools/build_app_icons.swift

import Foundation
import AppKit

/// Ink width as a fraction of the visible tile, i.e. ~14% margin on each side.
let TILE_RATIO = 0.72
/// An adaptive layer is 108dp, of which only the centre 72dp is guaranteed to be visible.
let ADAPTIVE_RATIO = TILE_RATIO * 72.0 / 108.0

let BRAND_DARK = (r: 0x1D / 255.0, g: 0x1D / 255.0, b: 0x1B / 255.0)
let WHITE = (r: 1.0, g: 1.0, b: 1.0)
let BLACK = (r: 0.0, g: 0.0, b: 0.0)

let MASTER = "app/src/commonMain/composeResources/drawable/pelo_mark_dark.png"
let RES = "app/src/androidMain/res"
let DENSITIES = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]

typealias RGB = (r: Double, g: Double, b: Double)

// MARK: - Ink mask

/// Reads the master and returns its ink coverage per pixel, trimmed to the ink's bounds.
func loadInk(_ path: String) -> (a: [Double], w: Int, h: Int) {
    guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
          let rep = NSBitmapImageRep(data: data), let bytes = rep.bitmapData else {
        fatalError("cannot read \(path)")
    }
    let w = rep.pixelsWide, h = rep.pixelsHigh
    let spp = rep.samplesPerPixel, row = rep.bytesPerRow

    // The mark is white-on-black, so luminance is the ink coverage.
    var ink = [Double](repeating: 0, count: w * h)
    for y in 0..<h {
        for x in 0..<w {
            let p = bytes + y * row + x * spp
            let luma: Double
            switch spp {
            case 1, 2: luma = Double(p[0]) / 255
            default: luma = (0.2126 * Double(p[0]) + 0.7152 * Double(p[1]) + 0.0722 * Double(p[2])) / 255
            }
            let alpha = (spp == 2 || spp == 4) ? Double(p[spp - 1]) / 255 : 1
            ink[y * w + x] = luma * alpha
        }
    }

    var minX = w, minY = h, maxX = -1, maxY = -1
    for y in 0..<h {
        for x in 0..<w where ink[y * w + x] > 0.5 {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
    }
    let bw = maxX - minX + 1, bh = maxY - minY + 1
    var trimmed = [Double](repeating: 0, count: bw * bh)
    for y in 0..<bh {
        for x in 0..<bw { trimmed[y * bw + x] = ink[(minY + y) * w + minX + x] }
    }
    print("master \(w)x\(h), ink \(bw)x\(bh)")
    return (trimmed, bw, bh)
}

/// Area-average resample. Every target is a downscale of the 800px master, so a box
/// filter is both the right filter and the cheap one.
func resample(_ src: [Double], _ sw: Int, _ sh: Int, _ dw: Int, _ dh: Int) -> [Double] {
    var dst = [Double](repeating: 0, count: dw * dh)
    let sx = Double(sw) / Double(dw), sy = Double(sh) / Double(dh)
    for dy in 0..<dh {
        let y0 = Double(dy) * sy, y1 = Double(dy + 1) * sy
        for dx in 0..<dw {
            let x0 = Double(dx) * sx, x1 = Double(dx + 1) * sx
            var sum = 0.0, weight = 0.0
            for py in Int(y0)...min(sh - 1, Int(ceil(y1)) - 1) {
                let wy = min(y1, Double(py + 1)) - max(y0, Double(py))
                if wy <= 0 { continue }
                for px in Int(x0)...min(sw - 1, Int(ceil(x1)) - 1) {
                    let wx = min(x1, Double(px + 1)) - max(x0, Double(px))
                    if wx <= 0 { continue }
                    sum += src[py * sw + px] * wx * wy
                    weight += wx * wy
                }
            }
            dst[dy * dw + dx] = weight > 0 ? sum / weight : 0
        }
    }
    return dst
}

// MARK: - Rendering

enum Tile {
    case square(RGB)   // opaque, edge to edge
    case circle(RGB)   // opaque disc, transparent corners
    case bare          // ink only, for the adaptive layers
}

let ink = loadInk(MASTER)

/// Draws the mark at `ratio` of `size`, centered, on `tile`.
func render(to path: String, size: Int, ratio: Double, tile: Tile, color: RGB) {
    let gw = Int((Double(size) * ratio).rounded())
    let gh = Int((Double(gw) * Double(ink.h) / Double(ink.w)).rounded())
    let glyph = resample(ink.a, ink.w, ink.h, gw, gh)
    let ox = (size - gw) / 2, oy = (size - gh) / 2

    let opaque: Bool
    switch tile {
    case .square: opaque = true
    default: opaque = false
    }
    let spp = opaque ? 3 : 4
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
        bitsPerSample: 8, samplesPerPixel: spp, hasAlpha: !opaque, isPlanar: false,
        colorSpaceName: .deviceRGB,
        bitmapFormat: opaque ? [] : .alphaNonpremultiplied,
        bytesPerRow: size * spp, bitsPerPixel: spp * 8), let out = rep.bitmapData else {
        fatalError("cannot allocate \(size)x\(size)")
    }

    let radius = Double(size) / 2
    for y in 0..<size {
        for x in 0..<size {
            let gx = x - ox, gy = y - oy
            let a = (gx >= 0 && gx < gw && gy >= 0 && gy < gh) ? glyph[gy * gw + gx] : 0

            // How much of this pixel the tile covers — antialiases the round icon's rim.
            var cover = 1.0
            var base = color
            switch tile {
            case .square(let bg): base = bg
            case .circle(let bg):
                base = bg
                let dx = Double(x) + 0.5 - radius, dy = Double(y) + 0.5 - radius
                cover = min(max(radius + 0.5 - (dx * dx + dy * dy).squareRoot(), 0), 1)
            case .bare:
                cover = a
            }

            let p = out + (y * size + x) * spp
            switch tile {
            case .bare:
                p[0] = UInt8((color.r * 255).rounded())
                p[1] = UInt8((color.g * 255).rounded())
                p[2] = UInt8((color.b * 255).rounded())
            default:
                p[0] = UInt8(((color.r * a + base.r * (1 - a)) * 255).rounded())
                p[1] = UInt8(((color.g * a + base.g * (1 - a)) * 255).rounded())
                p[2] = UInt8(((color.b * a + base.b * (1 - a)) * 255).rounded())
            }
            if !opaque { p[3] = UInt8((cover * 255).rounded()) }
        }
    }

    let url = URL(fileURLWithPath: path)
    try! FileManager.default.createDirectory(
        at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("  \(path)  \(size)px, ink \(gw)x\(gh)")
}

// MARK: - Targets

// iOS ships one 1024 master; it must stay opaque and alpha-free.
render(to: "iosApp/Assets.xcassets/AppIcon.appiconset/appicon-1024.png",
       size: 1024, ratio: TILE_RATIO, tile: .square(BRAND_DARK), color: WHITE)

for (density, scale) in DENSITIES {
    // Legacy launcher icons, 48dp, used below API 26 (minSdk is 24).
    let legacy = Int(48 * scale)
    render(to: "\(RES)/mipmap-\(density)/ic_launcher.png",
           size: legacy, ratio: TILE_RATIO, tile: .square(BRAND_DARK), color: WHITE)
    render(to: "\(RES)/mipmap-\(density)/ic_launcher_round.png",
           size: legacy, ratio: TILE_RATIO, tile: .circle(BRAND_DARK), color: WHITE)

    // Adaptive layers, 108dp. The background is a solid colour drawable, so only the
    // foreground and the themed-icon monochrome are rasterized.
    let adaptive = Int(108 * scale)
    render(to: "\(RES)/drawable-\(density)/ic_launcher_foreground.png",
           size: adaptive, ratio: ADAPTIVE_RATIO, tile: .bare, color: WHITE)
    render(to: "\(RES)/drawable-\(density)/ic_launcher_monochrome.png",
           size: adaptive, ratio: ADAPTIVE_RATIO, tile: .bare, color: BLACK)
}
