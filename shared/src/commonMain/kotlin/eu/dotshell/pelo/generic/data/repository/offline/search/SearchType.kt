package eu.dotshell.pelo.generic.data.repository.offline.search

import kotlinx.serialization.Serializable

@Serializable
enum class SearchType {
    STOP,
    LINE,
    ADDRESS
}
