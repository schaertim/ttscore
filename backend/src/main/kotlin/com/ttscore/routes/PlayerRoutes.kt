package com.ttscore.routes

import com.ttscore.model.ReasonResponse
import com.ttscore.scraper.clicktt.ClickTTSyncService
import com.ttscore.service.MatchService
import com.ttscore.service.PlayerService
import com.ttscore.service.SeasonService
import com.ttscore.util.isPro
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.playerRoutes() {
    route("/players") {
        get("/search") {
            val name =
                call.request.queryParameters["name"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing name parameter")
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20

            val result =
                PlayerService.search(name, page, size)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Name must be at least 3 characters")

            call.respond(result)
        }

        get("/{id}") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val player =
                PlayerService.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            // Pure read. The scrape is triggered separately by the client via GET /{id}/sync so it can
            // await completion and refresh in place. `isSyncing` advertises whether a sync will actually
            // run — a syncable player that is still within the cooldown window reports false, so the
            // client neither re-triggers nor shows a misleading "updating" indicator.
            val shouldSync =
                player.licenceNr != null && !ClickTTSyncService.isWithinCooldown(UUID.fromString(id))
            call.respond(HttpStatusCode.OK, player.copy(isSyncing = shouldSync))
        }

        // On-demand click-tt sync. Runs synchronously (respecting the per-player cooldown) so the caller
        // can await it and then refetch the freshly-updated profile data — no manual page refresh needed.
        get("/{id}/sync") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")
            val uuid =
                try {
                    UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid player id")
                }

            try {
                val currentSeasonId = SeasonService.getCurrentSeasonId()
                if (currentSeasonId != null) {
                    ClickTTSyncService.syncPlayer(uuid, currentSeasonId)
                }
            } catch (e: Exception) {
                application.environment.log.error("On-demand sync failed for player $id", e)
                return@get call.respond(HttpStatusCode.InternalServerError, "Sync failed")
            }

            call.respond(HttpStatusCode.OK)
        }

        get("/{id}/elo") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val history =
                PlayerService.getEloHistory(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            call.respond(HttpStatusCode.OK, history)
        }

        get("/{id}/matches") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val matches =
                PlayerService.getMatchHistory(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            call.respond(HttpStatusCode.OK, matches)
        }

        get("/{id}/next-match") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val nextMatch = PlayerService.getPlayerNextMatch(id)
            if (nextMatch == null) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.OK, nextMatch)
            }
        }

        get("/{id}/upcoming") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val upcoming =
                PlayerService.getUpcomingMatches(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "No team found for player")

            call.respond(HttpStatusCode.OK, upcoming)
        }

        get("/{id}/stats") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val stats =
                PlayerService.getSeasonStats(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            call.respond(HttpStatusCode.OK, stats)
        }

        // H2H is a Pro feature. Optional auth so anonymous callers resolve to "not Pro"
        // (403) rather than a 401 challenge — the frontend gates the UI ahead of this.
        authenticate("auth-jwt", optional = true) {
            get("/{id}/h2h/{opponentId}") {
                if (!call.isPro()) {
                    return@get call.respond(HttpStatusCode.Forbidden, ReasonResponse("pro_required"))
                }

                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")
                val opponentId =
                    call.parameters["opponentId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing opponent id")

                val h2h =
                    PlayerService.getHeadToHead(id, opponentId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

                call.respond(HttpStatusCode.OK, h2h)
            }

            // Player-focused match preview is a Pro feature — same pattern as the team preview.
            get("/{id}/preview/{matchId}") {
                if (!call.isPro()) {
                    return@get call.respond(HttpStatusCode.Forbidden, ReasonResponse("pro_required"))
                }

                val id =
                    call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")
                val matchId =
                    call.parameters["matchId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing match id")

                val preview =
                    MatchService.getPlayerMatchPreview(id, matchId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Preview not available")

                call.respond(HttpStatusCode.OK, preview)
            }
        }

        // Career tab (season-by-season arc, milestones, rivalries) is free.
        get("/{id}/career") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val career =
                PlayerService.getCareer(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            call.respond(HttpStatusCode.OK, career)
        }

        get("/{id}/class-history") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val history =
                PlayerService.getClassHistory(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Player not found")

            call.respond(HttpStatusCode.OK, history)
        }

        get("/{id}/league-context") {
            val id =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing player id")

            val context =
                PlayerService.getPlayerLeagueContext(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "No league context found")

            call.respond(HttpStatusCode.OK, context)
        }
    }
}
