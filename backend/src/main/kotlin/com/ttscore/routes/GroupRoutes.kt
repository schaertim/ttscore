package com.ttscore.routes

import com.ttscore.service.GroupService
import com.ttscore.service.MatchService
import com.ttscore.service.StandingsService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.groupRoutes() {
    route("/groups") {
        get {
            val league = call.request.queryParameters["league"]
            val season = call.request.queryParameters["season"]
            val groups = GroupService.getAll(league, season)
            call.respond(groups)
        }

        get("/{id}") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing group id")
            val group =
                GroupService.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Group not found")
            call.respond(group)
        }

        get("/{id}/standings") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing group id")
            val standings =
                StandingsService.getForGroup(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Group not found")
            call.respond(standings)
        }

        get("/{id}/teams") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing group id")
            val teams =
                GroupService.getTeams(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Group not found")
            call.respond(teams)
        }

        get("/{id}/matches") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing group id")
            val matches =
                MatchService.getForGroup(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Group not found")
            call.respond(matches)
        }
    }
}
