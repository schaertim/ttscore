package com.ttscore.routes

import com.ttscore.model.SetHomePlayerRequest
import com.ttscore.service.UserProfileService
import com.ttscore.util.toUuidOrNull
import com.ttscore.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userProfileRoutes() {
    authenticate("auth-jwt") {
        route("/users/me") {
            /** Returns the current user's profile (home player id + name). */
            get {
                val profile = UserProfileService.getProfile(call.userId())
                call.respond(profile)
            }

            /** Sets the home player. Body: { "playerId": "<uuid>" } */
            put("/home-player") {
                val body = call.receive<SetHomePlayerRequest>()
                val playerId =
                    body.playerId.toUuidOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid player ID")
                UserProfileService.setHomePlayer(call.userId(), playerId)
                call.respond(HttpStatusCode.NoContent)
            }

            /** Clears the home player. */
            delete("/home-player") {
                UserProfileService.removeHomePlayer(call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
