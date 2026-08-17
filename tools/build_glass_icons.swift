// Renders the Liquid Glass app-icon proposals, and the flat layers Icon Composer needs.
//
// Two different jobs, deliberately in one file because they must agree pixel for pixel:
//
//   1. Layers. Icon Composer wants flat, full-bleed 1024 artwork and applies the glass
//      material itself. Those files are the deliverable — `layers/`.
//   2. Previews. The system's material cannot be seen until the icon is built, which makes
//      choosing a direction impossible. `preview/` approximates what iOS 26 will do to each
//      layer set, so the variants can be judged side by side before anyone opens Xcode.
//
// The previews are an approximation and nothing else: real Liquid Glass refracts the
// wallpaper behind it and reacts to motion, neither of which a static PNG can show. They are
// for picking a direction, never for sign-off.
//
// Usage (macOS), from the pelo-kmp root:
//   swift tools/build_glass_icons.swift

import Foundation
import AppKit

typealias RGB = (r: Double, g: Double, b: Double)

let BELLECOUR: RGB = (0xB2 / 255.0, 0x66 / 255.0, 0x33 / 255.0)
let FACADE: RGB = (0xF7 / 255.0, 0xF1 / 255.0, 0xDC / 255.0)
let NOIR: RGB = (0, 0, 0)
let BLANC: RGB = (1, 1, 1)

let MASTER = "shared/src/commonMain/composeResources/drawable/pelo_mark_dark.png"
let OUT = "tools/app-icon-2026"

/// Ink width as a fraction of the tile. Matches build_app_icons.swift so the glass icon and
/// the flat one read at the same size when a user has both platforms in front of them.
let TILE_RATIO = 0.72

/// Superellipse exponent that approximates the iOS continuous-corner squircle.
let SQUIRCLE_N = 5.0

// MARK: - Ink

func loadInk(_ path: String) -> (a: [Double], w: Int, h: Int) {
    guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
          let rep = NSBitmapImageRep(data: data), let bytes = rep.bitmapData else {
        fatalError("cannot read \(path)")
    }
    let w = rep.pixelsWide, h = rep.pixelsHigh
    let spp = rep.samplesPerPixel, row = rep.bytesPerRow
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
    return (trimmed, bw, bh)
}

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

let ink = loadInk(MASTER)

/// The mark placed on a `size` canvas at TILE_RATIO, as an alpha mask.
func markMask(size: Int) -> [Double] {
    let gw = Int((Double(size) * TILE_RATIO).rounded())
    let gh = Int((Double(gw) * Double(ink.h) / Double(ink.w)).rounded())
    let glyph = resample(ink.a, ink.w, ink.h, gw, gh)
    let ox = (size - gw) / 2, oy = (size - gh) / 2
    var mask = [Double](repeating: 0, count: size * size)
    for y in 0..<gh {
        for x in 0..<gw { mask[(oy + y) * size + ox + x] = glyph[y * gw + x] }
    }
    return mask
}

// MARK: - Geometry helpers

/// Squircle coverage with 4x4 supersampling, so the rim antialiases cleanly.
func squircleCoverage(_ x: Int, _ y: Int, _ size: Int) -> Double {
    var hits = 0.0
    let s = Double(size)
    for sy in 0..<4 {
        for sx in 0..<4 {
            let u = (Double(x) + (Double(sx) + 0.5) / 4) / s * 2 - 1
            let v = (Double(y) + (Double(sy) + 0.5) / 4) / s * 2 - 1
            if pow(abs(u), SQUIRCLE_N) + pow(abs(v), SQUIRCLE_N) <= 1 { hits += 1 }
        }
    }
    return hits / 16
}

/// How deep inside the squircle a point sits, 0 at the rim and 1 at the centre.
func inwardDepth(_ x: Int, _ y: Int, _ size: Int) -> Double {
    let s = Double(size)
    let u = (Double(x) + 0.5) / s * 2 - 1
    let v = (Double(y) + 0.5) / s * 2 - 1
    let f = pow(pow(abs(u), SQUIRCLE_N) + pow(abs(v), SQUIRCLE_N), 1.0 / SQUIRCLE_N)
    return max(0, 1 - f)
}

func mix(_ a: RGB, _ b: RGB, _ t: Double) -> RGB {
    (a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
}

func clamp01(_ v: Double) -> Double { min(max(v, 0), 1) }

func smoothstep(_ e0: Double, _ e1: Double, _ x: Double) -> Double {
    let t = clamp01((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)
}

// MARK: - Writing

func write(_ pixels: [(RGB, Double)], size: Int, to path: String) {
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
        bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
        colorSpaceName: .deviceRGB, bitmapFormat: .alphaNonpremultiplied,
        bytesPerRow: size * 4, bitsPerPixel: 32), let out = rep.bitmapData else {
        fatalError("cannot allocate \(size)")
    }
    for i in 0..<(size * size) {
        let (c, a) = pixels[i]
        out[i * 4 + 0] = UInt8((clamp01(c.r) * 255).rounded())
        out[i * 4 + 1] = UInt8((clamp01(c.g) * 255).rounded())
        out[i * 4 + 2] = UInt8((clamp01(c.b) * 255).rounded())
        out[i * 4 + 3] = UInt8((clamp01(a) * 255).rounded())
    }
    let url = URL(fileURLWithPath: path)
    try! FileManager.default.createDirectory(
        at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("  \(path)")
}

// MARK: - Layers for Icon Composer

/// Flat, full-bleed, no glass, no squircle: exactly what Icon Composer expects to be handed.
func writeLayers(size: Int) {
    let mask = markMask(size: size)

    var bg = [(RGB, Double)](repeating: (BELLECOUR, 1), count: size * size)
    write(bg, size: size, to: "\(OUT)/layers/background-bellecour.png")

    bg = [(RGB, Double)](repeating: (NOIR, 1), count: size * size)
    write(bg, size: size, to: "\(OUT)/layers/background-noir.png")

    // Foreground carries the mark in its alpha; Icon Composer applies the material to it.
    var fg = [(RGB, Double)](repeating: (FACADE, 0), count: size * size)
    for i in 0..<(size * size) { fg[i] = (FACADE, mask[i]) }
    write(fg, size: size, to: "\(OUT)/layers/foreground-facade.png")

    // Monochrome twin for the Clear and Tinted appearances, where iOS supplies the colour.
    var mono = [(RGB, Double)](repeating: (BLANC, 0), count: size * size)
    for i in 0..<(size * size) { mono[i] = (BLANC, mask[i]) }
    write(mono, size: size, to: "\(OUT)/layers/foreground-mono.png")

    // Reversed polarity: page 10 of the brand book sanctions the mark in terracotta on a
    // Façade ground just as it does the other way round, so both are ours to choose between.
    bg = [(RGB, Double)](repeating: (FACADE, 1), count: size * size)
    write(bg, size: size, to: "\(OUT)/layers/background-facade.png")

    var fgInv = [(RGB, Double)](repeating: (BELLECOUR, 0), count: size * size)
    for i in 0..<(size * size) { fgInv[i] = (BELLECOUR, mask[i]) }
    write(fgInv, size: size, to: "\(OUT)/layers/foreground-bellecour.png")
}

// MARK: - Previews

enum MarkStyle {
    /// Opaque ink. The brand book's "interdits" rule out recolouring or shading the mark,
    /// so this is the only style that is compliant without an amendment.
    case solid(RGB)
    /// The mark itself becomes the glass: part of the ground shows through it and its upper
    /// contours catch a highlight. Handsome, and a departure from the book.
    case glass(RGB)
}

/// Approximates what iOS 26 does to a layer set. See the file header on why this is a
/// direction-finding tool and not a proof.
func preview(name: String, size: Int, ground: RGB, mark: MarkStyle, sheen: Double) {
    let mask = markMask(size: size)
    var pixels = [(RGB, Double)](repeating: (ground, 0), count: size * size)

    // Top-edge detector: where ink appears as you walk downward, the glass catches light.
    let k = max(1, size / 110)
    var topEdge = [Double](repeating: 0, count: size * size)
    for y in 0..<size {
        for x in 0..<size {
            let above = y >= k ? mask[(y - k) * size + x] : 0
            topEdge[y * size + x] = max(0, mask[y * size + x] - above)
        }
    }

    for y in 0..<size {
        for x in 0..<size {
            let cover = squircleCoverage(x, y, size)
            if cover <= 0 { continue }
            let depth = inwardDepth(x, y, size)
            let u = (Double(x) + 0.5) / Double(size)
            let v = (Double(y) + 0.5) / Double(size)

            var c = ground

            // Vertical fall, kept shallow. The material is a flat slab of glass, not a
            // pillow: overdo this and the icon reads as a 2010 bevelled button.
            c = mix(c, BLANC, 0.07 * sheen * smoothstep(0.50, 0.0, v))
            c = mix(c, NOIR, 0.07 * smoothstep(0.65, 1.0, v))

            // Specular sweep, broad and weak — it should be felt rather than seen.
            let dx = (u - 0.32) / 0.62, dy = (v - 0.14) / 0.40
            let spec = exp(-(dx * dx + dy * dy))
            c = mix(c, BLANC, 0.11 * sheen * spec)

            // The rim does the real work. A thin, crisp lip of light along the top edge and
            // a fainter one wrapping the bottom is what reads as a glass edge; a wide soft
            // gradient there reads as a shadow instead.
            let lip = smoothstep(0.038, 0.0, depth)
            c = mix(c, BLANC, 0.50 * sheen * lip * smoothstep(0.60, 0.0, v))
            c = mix(c, BLANC, 0.16 * sheen * lip * smoothstep(0.72, 1.0, v))
            c = mix(c, NOIR, 0.10 * smoothstep(0.10, 0.02, depth) * smoothstep(0.45, 0.95, v))

            // The mark.
            let a = mask[y * size + x]
            if a > 0 {
                switch mark {
                case .solid(let ic):
                    c = mix(c, ic, a)
                case .glass(let ic):
                    // Translucent so the ground reads through, then its top contours lit.
                    c = mix(c, mix(c, ic, 0.72), a)
                    c = mix(c, BLANC, 0.55 * topEdge[y * size + x])
                }
            }

            pixels[y * size + x] = (c, cover)
        }
    }
    write(pixels, size: size, to: "\(OUT)/preview/\(name).png")
}

// MARK: - Contact sheet

/// The variants at home-screen size on a neutral ground, which is the only way to judge
/// whether a treatment still reads once it is 120px on a wallpaper.
func contactSheet(_ names: [String], tile: Int, gap: Int, path: String) {
    let n = names.count
    let w = n * tile + (n + 1) * gap
    let h = tile + 2 * gap
    var out = [(RGB, Double)](repeating: ((0.62, 0.62, 0.64), 1), count: w * h)

    for (i, name) in names.enumerated() {
        let file = "\(OUT)/preview/\(name).png"
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: file)),
              let rep = NSBitmapImageRep(data: data), let bytes = rep.bitmapData else { continue }
        let sw = rep.pixelsWide, spp = rep.samplesPerPixel, row = rep.bytesPerRow

        var r = [Double](repeating: 0, count: sw * sw)
        var g = r, b = r, a = r
        for y in 0..<sw {
            for x in 0..<sw {
                let p = bytes + y * row + x * spp
                r[y * sw + x] = Double(p[0]) / 255
                g[y * sw + x] = Double(p[1]) / 255
                b[y * sw + x] = Double(p[2]) / 255
                a[y * sw + x] = spp == 4 ? Double(p[3]) / 255 : 1
            }
        }
        let rr = resample(r, sw, sw, tile, tile), gg = resample(g, sw, sw, tile, tile)
        let bb = resample(b, sw, sw, tile, tile), aa = resample(a, sw, sw, tile, tile)

        let ox = gap + i * (tile + gap), oy = gap
        for y in 0..<tile {
            for x in 0..<tile {
                let idx = y * tile + x
                let dst = (oy + y) * w + ox + x
                let (bc, _) = out[dst]
                let al = aa[idx]
                out[dst] = (mix(bc, (rr[idx], gg[idx], bb[idx]), al), 1)
            }
        }
    }

    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h,
        bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: w * 3, bitsPerPixel: 24),
        let px = rep.bitmapData else { fatalError("sheet") }
    for i in 0..<(w * h) {
        let (c, _) = out[i]
        px[i * 3 + 0] = UInt8((clamp01(c.r) * 255).rounded())
        px[i * 3 + 1] = UInt8((clamp01(c.g) * 255).rounded())
        px[i * 3 + 2] = UInt8((clamp01(c.b) * 255).rounded())
    }
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: path))
    print("  \(path)")
}

// MARK: - Run

print("layers (flat, for Icon Composer)")
writeLayers(size: 1024)

print("previews (approximation of the iOS 26 material)")
preview(name: "A-defaut", size: 1024, ground: BELLECOUR, mark: .solid(FACADE), sheen: 1.0)
preview(name: "B-marque-verre", size: 1024, ground: BELLECOUR, mark: .glass(FACADE), sheen: 1.0)
preview(name: "C-sombre", size: 1024, ground: NOIR, mark: .solid(FACADE), sheen: 0.75)
preview(name: "D-teinte", size: 1024, ground: (0.16, 0.16, 0.17), mark: .solid(BLANC), sheen: 0.6)
preview(name: "E-inverse", size: 1024, ground: FACADE, mark: .solid(BELLECOUR), sheen: 0.85)

print("contact sheet")
contactSheet(["A-defaut", "E-inverse", "C-sombre", "D-teinte"],
             tile: 240, gap: 28, path: "\(OUT)/variantes.png")
contactSheet(["A-defaut", "E-inverse"], tile: 300, gap: 32, path: "\(OUT)/polarite.png")
