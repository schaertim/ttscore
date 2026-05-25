package com.ttscore.routes

import com.ttscore.model.FavoriteRequest
import com.ttscore.model.FollowTargetType
import com.ttscore.service.FavoriteService
import com.ttscore.util.toUuidOrNull
import com.ttscore.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.favoriteRoutes() {
    authenticate("auth-jwt") {
        route("/favorites") {
            /** Returns all starred bookmarks for the current user. */
            get {
                call.respond(FavoriteService.getFavorites(call.userId()))
            }

            /**
             * Returns full player details for all favorited players.
             * GET /favorites/players
             */
            get("/players") {
                call.respond(FavoriteService.getFavoritePlayers(call.userId()))
            }

            /**
             * Stars a target.
             * Body: { "targetType": "player"|"team"|"division_group", "targetId": "<uuid>" }
             */
            post {
                val body = call.receive<FavoriteRequest>()
                val targetType =
                    parseTargetType(body.targetType)
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetType")
                val targetId =
                    body.targetId.toUuidOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetId")

                val favorite =
                    FavoriteService.favorite(call.userId(), targetType, targetId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Target not found")

                call.respond(HttpStatusCode.Created, favorite)
            }

            /** Removes a star. DELETE /favorites/{id} */
            delete("/{id}") {
                val favoriteId =
                    call.parameters["id"]?.toUuidOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid favorite ID")

                if (FavoriteService.unfavorite(call.userId(), favoriteId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Favorite not found")
                }
            }

            /**
             * Checks whether the user has starred a target.
             * Returns { favorited, favoriteId }
             * GET /favorites/check?targetType=player&targetId=<uuid>
             */
            get("/check") {
                val targetType =
                    call.request.queryParameters["targetType"]
                        ?.let { parseTargetType(it) }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid targetType")
                val targetId =
                    call.request.queryParameters["targetId"]?.toUuidOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid targetId")

                call.respond(FavoriteService.check(call.userId(), targetType, targetId))
            }
        }
    }
}

private fun parseTargetType(raw: String): FollowTargetType? =
    when (raw.lowercase()) {
        "player" -> FollowTargetType.PLAYER
        "team" -> FollowTargetType.TEAM
        "division_group" -> FollowTargetType.DIVISION_GROUP
        else -> null
    }
