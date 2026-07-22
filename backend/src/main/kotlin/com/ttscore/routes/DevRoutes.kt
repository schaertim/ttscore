package com.ttscore.routes

import com.ttscore.jobs.MatchPollJob
import com.ttscore.service.SimulationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("DevRoutes")

/**
 * Dev/testing endpoints. Registered only when `dev.toolsEnabled` is true — see [configureRouting].
 * MUST stay disabled in production.
 */
fun Route.devRoutes() {
    route("/dev") {
        // Rewind an entity's most recent finished matches to SCHEDULED so the poll job re-completes
        // them and fires the real notification/standings/ELO pipeline. Optionally kicks the poll job
        // off immediately (fire-and-forget) instead of waiting up to 5 minutes for its next tick.
        //
        //   POST /api/v1/dev/simulate-results?type=player|team|league&id=<uuid>&count=3&trigger=true
        post("/simulate-results") {
            val entityType =
                when (call.parameters["type"]?.lowercase()) {
                    "player" -> SimulationService.EntityType.PLAYER
                    "team" -> SimulationService.EntityType.TEAM
                    "league", "group", "division" -> SimulationService.EntityType.LEAGUE
                    else ->
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "type must be one of: player, team, league",
                        )
                }

            val id =
                call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "id must be a valid UUID")

            val count = (call.parameters["count"]?.toIntOrNull() ?: 3).coerceIn(1, 50)
            val trigger = call.parameters["trigger"]?.toBooleanStrictOrNull() ?: false

            val result = SimulationService.resetMatches(entityType, id, count)

            val pollTriggered = trigger && result.resetCount > 0
            if (pollTriggered) {
                // Fire-and-forget: the poll job scrapes + syncs ELO (with per-player delays) and can
                // take a while, so we don't block the HTTP response on it. Watch the logs / your phone.
                call.application.launch {
                    try {
                        logger.info("dev/simulate-results: triggering MatchPollJob for ${result.resetCount} reset matches")
                        MatchPollJob.create().run()
                    } catch (e: Exception) {
                        logger.error("dev/simulate-results: MatchPollJob failed: ${e.message}", e)
                    }
                }
            }

            call.respond(HttpStatusCode.OK, result.copy(pollTriggered = pollTriggered))
        }
    }
}
