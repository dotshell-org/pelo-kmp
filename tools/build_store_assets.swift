// Builds every image the App Store and Play Store listings need, apart from screenshots.
//
// Two sources, deliberately different:
//
//   • The app mark comes from the in-app master (pelo_mark_dark.png), the same file the
//     launcher icons are cut from, so a store icon can never drift from the shipped one.
//   • The full logotype — mark plus wordmark plus the signal arc — only exists in the brand
//     book. It is lifted straight out of the PDF's vector art rather than rebuilt from a
//     font, because rebuilding it would be a redrawn logo, which the book forbids outright.
//
// Store rules worth knowing, since getting them wrong means a rejected submission:
//   • App Store icon: 1024x1024, and it must carry NO alpha channel. Apple rejects it
//     otherwise, which is why it is written as 3-samples-per-pixel here.
//   • Play icon: 512x512, 32-bit with alpha. Google applies its own mask, so the art is
//     full-bleed square and must not be pre-rounded.
//   • Play feature graphic: exactly 1024x500, no alpha, no transparency.
//
// Usage (macOS), from the pelo-kmp root, after extracting the brand book page:
//   swift tools/build_store_assets.swift <page-png> <output-root>

import Foundation
import AppKit

typealias RGB = (r: Double, g: Double, b: Double)

let BELLECOUR: RGB = (0xB2 / 255.0, 0x66 / 255.0, 0x33 / 255.0)
let FACADE: RGB = (0xF7 / 255.0, 0xF1 / 255.0, 0xDC / 255.0)

let MARK = "shared/src/commonMain/composeResources/drawable/pelo_mark_dark.png"

guard CommandLine.arguments.count >= 3 else {
    fatalError("usage: build_store_assets.swift <page-png> <output-root>")
}
let PAGE = CommandLine.arguments[1]
let ROOT = CommandLine.arguments[2]

// MARK: - Loading

/// Ink coverage of an image, plus its size. `invert` reads dark-on-light art.
func loadCoverage(_ path: String, invert: Bool) -> (a: [Double], w: Int, h: Int) {
    guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
          let rep = NSBitmapImageRep(data: data), let bytes = rep.bitmapData else {
        fatalError("cannot read \(path)")
    }
    let w = rep.pixelsWide, h = rep.pixelsHigh
    let spp = rep.samplesPerPixel, row = rep.bytesPerRow
    var cov = [Double](repeating: 0, count: w * h)
    for y in 0..<h {
        for x in 0..<w {
            let p = bytes + y * row + x * spp
            let luma: Double
            switch spp {
            case 1, 2: luma = Double(p[0]) / 255
            default: luma = (0.2126 * Double(p[0]) + 0.7152 * Double(p[1]) + 0.0722 * Double(p[2])) / 255
            }
            let alpha = (spp == 2 || spp == 4) ? Double(p[spp - 1]) / 255 : 1
            cov[y * w + x] = (invert ? 1 - luma : luma) * alpha
        }
    }
    return (cov, w, h)
}

/// Trims to the ink's bounding box, searched only inside `region` so that the page's own
/// headings and footer do not drag the box open.
func trim(_ src: (a: [Double], w: Int, h: Int),
          region: (x0: Double, y0: Double, x1: Double, y1: Double)) -> (a: [Double], w: Int, h: Int) {
    let rx0 = Int(Double(src.w) * region.x0), rx1 = Int(Double(src.w) * region.x1)
    let ry0 = Int(Double(src.h) * region.y0), ry1 = Int(Double(src.h) * region.y1)
    var minX = rx1, minY = ry1, maxX = rx0, maxY = ry0
    for y in ry0..<ry1 {
        for x in rx0..<rx1 where src.a[y * src.w + x] > 0.5 {
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
        }
    }
    guard maxX > minX, maxY > minY else { fatalError("no ink found in region") }
    let bw = maxX - minX + 1, bh = maxY - minY + 1
    var out = [Double](repeating: 0, count: bw * bh)
    for y in 0..<bh {
        for x in 0..<bw { out[y * bw + x] = src.a[(minY + y) * src.w + minX + x] }
    }
    print("  ink \(bw)x\(bh) at (\(minX),\(minY))")
    return (out, bw, bh)
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

// MARK: - Writing

/// Writes opaque RGB. Every store asset here is alpha-free on purpose; see the header.
func writeOpaque(_ px: [RGB], w: Int, h: Int, to path: String) {
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h,
        bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: w * 3, bitsPerPixel: 24),
        let out = rep.bitmapData else { fatalError("alloc \(w)x\(h)") }
    for i in 0..<(w * h) {
        out[i * 3 + 0] = UInt8((min(max(px[i].r, 0), 1) * 255).rounded())
        out[i * 3 + 1] = UInt8((min(max(px[i].g, 0), 1) * 255).rounded())
        out[i * 3 + 2] = UInt8((min(max(px[i].b, 0), 1) * 255).rounded())
    }
    let url = URL(fileURLWithPath: path)
    try! FileManager.default.createDirectory(
        at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("  \(path)")
}

func mix(_ a: RGB, _ b: RGB, _ t: Double) -> RGB {
    (a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
}

// MARK: - Icons

let markInk = trim(loadCoverage(MARK, invert: false), region: (0, 0, 1, 1))

/// Same 0.72 tile ratio as the launcher icons, so the store listing and the home screen agree.
func icon(size: Int, to path: String) {
    let gw = Int(Double(size) * 0.72)
    let gh = Int((Double(gw) * Double(markInk.h) / Double(markInk.w)).rounded())
    let glyph = resample(markInk.a, markInk.w, markInk.h, gw, gh)
    let ox = (size - gw) / 2, oy = (size - gh) / 2
    var px = [RGB](repeating: BELLECOUR, count: size * size)
    for y in 0..<gh {
        for x in 0..<gw {
            let a = glyph[y * gw + x]
            let i = (oy + y) * size + ox + x
            px[i] = mix(BELLECOUR, FACADE, a)
        }
    }
    writeOpaque(px, w: size, h: size, to: path)
}

// MARK: - Feature graphic

/// 1024x500 on Bellecour, logotype centred at a size that survives Play's own downscaling
/// in the search results, where this graphic is shown far smaller than it is authored.
func featureGraphic(logo: (a: [Double], w: Int, h: Int), to path: String) {
    let w = 1024, h = 500
    var px = [RGB](repeating: BELLECOUR, count: w * h)

    let target = Int(Double(h) * 0.62)
    let gh = target
    let gw = Int((Double(gh) * Double(logo.w) / Double(logo.h)).rounded())
    let glyph = resample(logo.a, logo.w, logo.h, gw, gh)
    let ox = (w - gw) / 2, oy = (h - gh) / 2
    for y in 0..<gh {
        for x in 0..<gw {
            let a = glyph[y * gw + x]
            px[(oy + y) * w + ox + x] = mix(BELLECOUR, FACADE, a)
        }
    }
    writeOpaque(px, w: w, h: h, to: path)
}

// MARK: - Run

print("icons")
icon(size: 1024, to: "\(ROOT)/App Store (iOS-iPadOS)/Icone/app-store-icon-1024.png")
icon(size: 512, to: "\(ROOT)/Play Store (Android)/Icone/play-store-icon-512.png")

print("logotype from the brand book")
// The logotype sits in the right half of the page; the left holds the section heading and
// the bottom-left the copyright line, both of which would otherwise widen the bounding box.
let logo = trim(loadCoverage(PAGE, invert: true), region: (0.42, 0.08, 0.92, 0.88))

print("feature graphic")
featureGraphic(logo: logo, to: "\(ROOT)/Play Store (Android)/Image mise en avant/feature-graphic-1024x500.png")
