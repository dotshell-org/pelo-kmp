package eu.dotshell.pelo.generic.data.network.transport

/**
 * Query object for retrieving transport line geometries.
 */
sealed interface TransportLinesQuery {
    /**
     * Returns all default (non-bus-by-pagination) strong line geometries that should appear
     * on the map without loading individual line datasets on demand.
     */
    data object StrongLines : TransportLinesQuery

    /**
     * Returns the geometry for a single line by its display name.
     * Implementations must handle any special aliasing (e.g. airport shuttle names).
     */
    data class LineByName(val lineName: String) : TransportLinesQuery

    /**
     * Returns a page of bus-like line geometries.
     * Used for offline downloads (pagination/OOM protection).
     */
    data class BusPage(val startIndex: Int, val count: Int) : TransportLinesQuery
}
