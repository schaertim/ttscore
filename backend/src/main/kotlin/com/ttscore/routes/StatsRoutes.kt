package com.ttscore.routes

import com.ttscore.service.SeasonService
import com.ttscore.service.StatsService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statsRoutes() {
    route("/stats") {
        get {
            val seasonName =
                call.request.queryParameters["season"]
                    ?: SeasonService.getCurrentSeason()?.second
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "No seasons available")
            call.respond(StatsService.getStats(seasonName))
        }
    }
}
