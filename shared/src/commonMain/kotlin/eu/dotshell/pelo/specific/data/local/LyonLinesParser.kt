package eu.dotshell.pelo.specific.data.local

import io.raptor.data.BinaryReader

/**
 * A single line trace, points as [lon, lat] pairs ready for GeoJSON
 * MultiLineString coordinates. Unlike the GTFS-based cities (one trace per
 * direction), the Lyon file keeps EVERY WFS trace variant as its own path,
 * so a direction id can appear many times for the same line.
 */
data class LyonLinePath(
    val directionId: Int,
    val points: List<List<Double>>
)

/**
 * A transit line as stored in lines.bin: display name, colors and one
 * geometry path per trace variant.
 */
data class LyonLine(
    val idInternal: Int,
    val name: String,
    val colorHex: String,      // RRGGBB without '#', may be empty
    val textColorHex: String,  // RRGGBB without '#', may be empty
    val gtfsRouteType: Int,    // raw GTFS route_type: 0 tram, 1 metro, 3 bus, 4 ferry, 7 funicular
    val paths: List<LyonLinePath>
)

/**
 * Parser for the RLN2 binary format shared with the other city apps —
 * produced by `tools/build_lines_bin.py` (WFS source) here, and by
 * `raptor-gtfs-pipeline --traces` for the GTFS-based cities.
 *
 * Layout (little-endian, strings = u16 byte length + UTF-8):
 * ```
 * magic "RLN2" | u16 schemaVersion | u32 coordScale | u32 lineCount
 * per line: u32 idInternal | str name | str color | str textColor
 *           | u16 transportType | u16 pathCount
 * per path: u16 directionId | u32 pointCount
 *           | pointCount × i32 delta-encoded lon×scale (first absolute)
 *           | pointCount × i32 delta-encoded lat×scale (first absolute)
 * ```
 */
object LyonLinesParser {

    fun parse(bytes: ByteArray): List<LyonLine> {
        val reader = BinaryReader(bytes)
        reader.readMagic("RLN2")
        reader.readUInt16() // schema version
        val coordScale = reader.readUInt32().toDouble()
        val lineCount = reader.readUInt32()

        return List(lineCount) {
            val idInternal = reader.readUInt32()
            val name = readString(reader)
            val color = readString(reader)
            val textColor = readString(reader)
            val transportType = reader.readUInt16()
            val pathCount = reader.readUInt16()

            val paths = List(pathCount) {
                val directionId = reader.readUInt16()
                val pointCount = reader.readUInt32()
                val xs = readDeltaInts(reader, pointCount)
                val ys = readDeltaInts(reader, pointCount)
                LyonLinePath(
                    directionId = directionId,
                    points = List(pointCount) { i ->
                        listOf(xs[i] / coordScale, ys[i] / coordScale)
                    }
                )
            }

            LyonLine(idInternal, name, color, textColor, transportType, paths)
        }
    }

    private fun readString(reader: BinaryReader): String {
        val length = reader.readUInt16()
        return reader.readUTF8(length)
    }

    private fun readDeltaInts(reader: BinaryReader, count: Int): IntArray {
        val out = IntArray(count)
        var current = 0
        for (i in 0 until count) {
            current += reader.readInt32()
            out[i] = current
        }
        return out
    }
}
