package com.ttscore.service

import com.ttscore.database.Games
import com.ttscore.database.Groups
import com.ttscore.database.Matches
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.database.dbQuery
import com.ttscore.model.MatchStatus
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Dev-only helper that "rewinds" already-played matches so the notification/update pipeline
 * can be exercised out of season. It flips a handful of an entity's COMPLETED matches back to
 * SCHEDULED (leaving score, meeting id, played-at and games intact). The next [com.ttscore.jobs.MatchPollJob]
 * run then re-scrapes the still-live click-tt page, sees them completed again, flips them
 * SCHEDULED → COMPLETED, and fires the real push notifications + standings + ELO sync.
 *
 * Only matches that are re-completable are eligible: they must have a click-tt meeting id and sit
 * in a click-tt group (so the poll job can actually re-fetch them). Selection is restricted to the
 * most recent season in which the entity has such matches — "the last season with results".
 *
 * Guarded behind the `dev.toolsEnabled` flag at the route layer — never enable in production.
 */
object SimulationService {
    enum class EntityType { PLAYER, TEAM, LEAGUE }

    @Serializable
    data class SimMatch(
        val id: String,
        val label: String,
        val score: String,
        val playedAt: String?,
    )

    @Serializable
    data class ResetResult(
        val entityType: String,
        val entityId: String,
        val season: String?,
        val requestedCount: Int,
        val resetCount: Int,
        val pollTriggered: Boolean = false,
        val matches: List<SimMatch>,
    )

    private data class Candidate(
        val id: UUID,
        val homeTeamId: UUID,
        val awayTeamId: UUID,
        val homeScore: Short?,
        val awayScore: Short?,
        val playedAt: OffsetDateTime?,
        val seasonName: String,
    )

    suspend fun resetMatches(
        type: EntityType,
        id: UUID,
        count: Int,
    ): ResetResult =
        dbQuery {
            val candidateIds = candidateMatchIds(type, id)

            // Only matches the poll job can actually re-complete: a click-tt meeting id (used to
            // re-detect the result) inside a click-tt group (used to re-fetch the page).
            val rows =
                if (candidateIds.isEmpty()) {
                    emptyList()
                } else {
                    (Matches innerJoin Groups innerJoin Seasons)
                        .select(
                            Matches.id,
                            Matches.homeTeamId,
                            Matches.awayTeamId,
                            Matches.homeScore,
                            Matches.awayScore,
                            Matches.playedAt,
                            Seasons.name,
                        )
                        .where {
                            (Matches.id inList candidateIds) and
                                (Matches.status eq MatchStatus.COMPLETED) and
                                Matches.clickttMatchId.isNotNull() and
                                Groups.clickttId.isNotNull()
                        }
                        .map {
                            Candidate(
                                id = it[Matches.id],
                                homeTeamId = it[Matches.homeTeamId],
                                awayTeamId = it[Matches.awayTeamId],
                                homeScore = it[Matches.homeScore],
                                awayScore = it[Matches.awayScore],
                                playedAt = it[Matches.playedAt],
                                seasonName = it[Seasons.name],
                            )
                        }
                }

            // "Last season with results" — the most recent season name present among the candidates.
            val latestSeason = rows.map { it.seasonName }.maxOrNull()

            val chosen =
                rows.filter { it.seasonName == latestSeason }
                    .sortedByDescending { it.playedAt }
                    .take(count)

            if (chosen.isNotEmpty()) {
                val chosenIds = chosen.map { it.id }
                Matches.update({ Matches.id inList chosenIds }) {
                    it[Matches.status] = MatchStatus.SCHEDULED
                }
            }

            val teamIds = chosen.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
            val teamNames =
                if (teamIds.isEmpty()) {
                    emptyMap()
                } else {
                    Teams.select(Teams.id, Teams.name)
                        .where { Teams.id inList teamIds }
                        .associate { it[Teams.id] to it[Teams.name] }
                }

            ResetResult(
                entityType = type.name,
                entityId = id.toString(),
                season = latestSeason,
                requestedCount = count,
                resetCount = chosen.size,
                matches =
                    chosen.map { m ->
                        val home = teamNames[m.homeTeamId] ?: "?"
                        val away = teamNames[m.awayTeamId] ?: "?"
                        SimMatch(
                            id = m.id.toString(),
                            label = "$home vs $away",
                            score = "${m.homeScore ?: "—"}:${m.awayScore ?: "—"}",
                            playedAt = m.playedAt?.toString(),
                        )
                    },
            )
        }

    /** All match ids the entity took part in, before the completable/season filtering. */
    private fun candidateMatchIds(
        type: EntityType,
        id: UUID,
    ): Set<UUID> =
        when (type) {
            EntityType.TEAM ->
                Matches.select(Matches.id)
                    .where { (Matches.homeTeamId eq id) or (Matches.awayTeamId eq id) }
                    .map { it[Matches.id] }
                    .toSet()

            EntityType.LEAGUE ->
                Matches.select(Matches.id)
                    .where { Matches.groupId eq id }
                    .map { it[Matches.id] }
                    .toSet()

            EntityType.PLAYER ->
                Games.select(Games.matchId)
                    .where {
                        (Games.homePlayer1Id eq id) or (Games.awayPlayer1Id eq id) or
                            (Games.homePlayer2Id eq id) or (Games.awayPlayer2Id eq id)
                    }
                    .mapNotNull { it[Games.matchId] }
                    .toSet()
        }
}
