package com.ttfeed.routes

import com.ttfeed.service.SeasonService
import com.ttfeed.service.StatsService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statsRoutes() {
    route("/stats") {
        get {
            val seasonName = call.request.queryParameters["season"]
                ?: SeasonService.getCurrentSeason()?.second
                ?: return@get call.respond(HttpStatusCode.BadRequest, "No seasons available")
            call.respond(StatsService.getStats(seasonName))
        }
    }
}
