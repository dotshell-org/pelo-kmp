package eu.dotshell.massilia.generic.data.models.search

sealed class UnifiedSearchResult {
    abstract val sortKey: String
    abstract val itemKey: String

    data class Line(val result: LineSearchResult) : UnifiedSearchResult() {
        override val sortKey: String = result.lineName.uppercase()
        override val itemKey: String = "line_${result.lineName.uppercase()}"
    }

    data class Stop(val result: StationSearchResult) : UnifiedSearchResult() {
        override val sortKey: String = result.stopName.uppercase()
        override val itemKey: String = "stop_${result.stopId ?: result.stopName.uppercase()}"
    }
}
