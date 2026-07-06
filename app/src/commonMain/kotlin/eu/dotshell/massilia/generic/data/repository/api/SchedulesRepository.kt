package eu.dotshell.massilia.generic.data.repository.api

import eu.dotshell.massilia.generic.data.models.search.StationSearchResult
import eu.dotshell.massilia.generic.data.models.search.LineSearchResult

interface SchedulesRepository {
    suspend fun searchStopsByName(query: String): List<StationSearchResult>
    fun searchLinesByName(query: String): List<LineSearchResult>
    fun getAllRouteNames(): List<String>
    fun getHeadsigns(routeName: String): Map<Int, String>
    fun getDesserteForStop(stopName: String): String?
    fun getStopSequences(routeName: String, directionId: Int): List<Pair<String, Int>>
    fun getSchedules(
        lineName: String,
        stopName: String,
        directionId: Int,
        isSchoolHoliday: Boolean,
        isPublicHoliday: Boolean
    ): List<String>
}
