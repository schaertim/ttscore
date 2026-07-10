package com.ttscore.routes

import com.ttscore.model.ReasonResponse
import com.ttscore.service.MatchService
import com.ttscore.util.isPro
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.matchRoutes() {
    route("/matches") {
        get("/{id}") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing match id")
            val matches =
                MatchService.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Match not found")
            call.respond(matches)
        }

        // Match preview is a Pro feature. Optional auth so anonymous callers resolve to "not Pro"
        // (403) rather than a 401 challenge — the frontend gates the UI ahead of this.
        authenticate("auth-jwt", optional = true) {
            get("/{id}/preview") {
                if (!call.isPro()) {
                    return@get call.respond(HttpStatusCode.Forbidden, ReasonResponse("pro_required"))
                }

                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing match id")

                val preview =
                    MatchService.getMatchPreview(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Match not found")

                call.respond(preview)
            }
        }
    }
}
