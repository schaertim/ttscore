package com.ttscore.routes

import com.ttscore.service.SeasonService
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.seasonRoutes() {
    route("/seasons") {
        get {
            call.respond(SeasonService.getAll())
        }
    }
}
