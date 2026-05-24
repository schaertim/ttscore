package com.ttfeed.routes

import com.ttfeed.model.FollowRequest
import com.ttfeed.model.FollowTargetType
import com.ttfeed.service.FollowService
import com.ttfeed.util.toUuidOrNull
import com.ttfeed.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.followRoutes() {
    authenticate("auth-jwt") {
        route("/follows") {
            /** Returns all notification subscriptions for the current user. */
            get {
                call.respond(FollowService.getFollows(call.userId()))
            }

            /**
             * Subscribes to notifications for a target.
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

                val follow =
                    FollowService.follow(call.userId(), targetType, targetId)
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Target not found")

                call.respond(HttpStatusCode.Created, follow)
            }

            /** Unsubscribes. DELETE /follows/{id} */
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
             * Checks notification status for a target.
             * Returns { notifying, notifyId }
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
