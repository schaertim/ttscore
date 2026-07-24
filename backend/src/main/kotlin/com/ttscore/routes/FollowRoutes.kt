package com.ttscore.routes

import com.ttscore.model.FollowNotifyRequest
import com.ttscore.model.FollowRequest
import com.ttscore.model.FollowTargetType
import com.ttscore.model.ReasonResponse
import com.ttscore.service.FeedService
import com.ttscore.service.FollowResult
import com.ttscore.service.FollowService
import com.ttscore.service.UserProfileService
import com.ttscore.util.isPro
import com.ttscore.util.toUuidOrNull
import com.ttscore.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.followRoutes() {
    authenticate("auth-jwt") {
        route("/follows") {
            /** Returns everything the current user follows (each with its notify flag). */
            get {
                call.respond(FollowService.getFollows(call.userId()))
            }

            /**
             * Returns full player details for all followed players.
             * GET /follows/players
             */
            get("/players") {
                call.respond(FollowService.getFollowedPlayers(call.userId()))
            }

            /**
             * Follows a target (notify defaults off).
             * Body: { "targetType": "player"|"team"|"division_group", "targetId": "<uuid>" }
             */
            post {
                val body = call.receive<FollowRequest>()
                val targetType =
                    parseTargetType(body.targetType)
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetType")
                val targetId =
                    body.targetId.toUuidOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetId")

                val userId = call.userId()
                val homePlayerId = UserProfileService.getHomePlayerId(userId)
                when (val result = FollowService.follow(userId, targetType, targetId, call.isPro(), homePlayerId)) {
                    is FollowResult.Ok -> call.respond(HttpStatusCode.Created, result.follow)
                    FollowResult.TargetNotFound ->
                        call.respond(HttpStatusCode.NotFound, "Target not found")
                    FollowResult.LimitReached ->
                        call.respond(HttpStatusCode.Forbidden, ReasonResponse("follow_limit"))
                }
            }

            /** Unfollows. DELETE /follows/{id} */
            delete("/{id}") {
                val followId =
                    call.parameters["id"]?.toUuidOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid follow ID")

                if (FollowService.unfollow(call.userId(), followId)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Follow not found")
                }
            }

            /**
             * Toggles the notify (bell) flag on a follow.
             * Body: { "notify": true|false }
             * PATCH /follows/{id}
             */
            patch("/{id}") {
                val followId =
                    call.parameters["id"]?.toUuidOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid follow ID")
                val body = call.receive<FollowNotifyRequest>()
                val userId = call.userId()
                val homePlayerId = UserProfileService.getHomePlayerId(userId)

                when (FollowService.setNotify(userId, followId, body.notify, call.isPro(), homePlayerId)) {
                    FollowService.SetNotifyResult.OK -> call.respond(HttpStatusCode.NoContent)
                    FollowService.SetNotifyResult.NOT_FOUND ->
                        call.respond(HttpStatusCode.NotFound, "Follow not found")
                    FollowService.SetNotifyResult.PRO_REQUIRED ->
                        call.respond(HttpStatusCode.Forbidden, ReasonResponse("notify_pro"))
                }
            }

            /**
             * Returns the most recent events (match results, class changes, upcoming fixtures)
             * across everything the user follows, newest first — used for the home dashboard's
             * preview feed. GET /follows/feed?limit=5
             */
            get("/feed") {
                val limit =
                    call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5
                call.respond(FeedService.getFeedPreview(call.userId(), limit))
            }

            /**
             * Checks follow status for a target.
             * Returns { following, followId, notify }
             * GET /follows/check?targetType=player&targetId=<uuid>
             */
            get("/check") {
                val targetType =
                    call.request.queryParameters["targetType"]
                        ?.let { parseTargetType(it) }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid targetType")
                val targetId =
                    call.request.queryParameters["targetId"]?.toUuidOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid targetId")

                call.respond(FollowService.check(call.userId(), targetType, targetId))
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
