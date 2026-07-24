package com.ttscore.service

import com.ttscore.database.*
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchResponse
import com.ttscore.model.TeamPlayerResponse
import com.ttscore.model.TeamSummaryResponse
import com.ttscore.util.toUuidOrNull
import org.jetbrains.exposed.sql.*
import java.util.UUID

object TeamService {
    private val homeTeam = Teams.alias("home_team")
    private val awayTeam = Teams.alias("away_team")

    suspend fun getTeamSummary(teamId: String): TeamSummaryResponse? {
        val uuid = teamId.toUuidOrNull() ?: return null
        return dbQuery {
            val teamRow =
                Teams.select(Teams.id, Teams.name, Teams.groupId).where { Teams.id eq uuid }.singleOrNull()
                    ?: return@dbQuery null

            val groupName =
                Groups.select(Groups.name)
                    .where { Groups.id eq teamRow[Teams.groupId] }
                    .singleOrNull()?.get(Groups.name) ?: ""

            val standing =
                Standings
                    .select(Standings.won, Standings.drawn, Standings.lost, Standings.points, Standings.position)
                    .where { Standings.teamId eq uuid }
                    .singleOrNull()
            val record =
                "${standing?.get(Standings.won) ?: 0}-${standing?.get(Standings.drawn) ?: 0}" +
                    "-${standing?.get(Standings.lost) ?: 0}"
            val points = standing?.get(Standings.points)?.toInt() ?: 0
            val position = standing?.get(Standings.position)?.toInt() ?: 0

            val lastResults =
                Matches.select(Matches.homeTeamId, Matches.homeScore, Matches.awayScore)
                    .where { (Matches.homeTeamId eq uuid) or (Matches.awayTeamId eq uuid) }
                    .orderBy(Matches.playedAt to SortOrder.DESC)
                    .limit(5)
                    .map { it.toMatchResult(uuid) }

            TeamSummaryResponse(
                id = teamRow[Teams.id].toString(),
                name = teamRow[Teams.name],
                groupId = teamRow[Teams.groupId].toString(),
                groupName = groupName,
                position = position,
                record = record,
                points = points,
                lastResults = lastResults,
            )
        }
    }

    suspend fun getTeamRoster(teamId: String): List<TeamPlayerResponse>? {
        val uuid = teamId.toUuidOrNull() ?: return null
        return dbQuery {
            if (Teams.select(Teams.id).where { Teams.id eq uuid }.empty()) return@dbQuery null

            val playerRows =
                (Players innerJoin PlayerSeasons)
                    .select(Players.id, Players.fullName, Players.licenceNr)
                    .where { PlayerSeasons.teamId eq uuid }
                    .toList()

            if (playerRows.isEmpty()) return@dbQuery emptyList()

            val playerIds = playerRows.map { it[Players.id] }.toSet()

            // Fetch match IDs for this team in one query
            val teamMatchIds =
                Matches
                    .select(Matches.id)
                    .where { (Matches.homeTeamId eq uuid) or (Matches.awayTeamId eq uuid) }
                    .map { it[Matches.id] }
                    .toSet()

            // Fetch all relevant singles games within those matches in one query
            val wins = mutableMapOf<UUID, Int>()
            val losses = mutableMapOf<UUID, Int>()

            if (teamMatchIds.isNotEmpty()) {
                Games
                    .select(Games.homePlayer1Id, Games.awayPlayer1Id, Games.result)
                    .where {
                        (Games.matchId inList teamMatchIds) and
                            (Games.gameType eq GameType.SINGLES) and
                            ((Games.homePlayer1Id inList playerIds) or (Games.awayPlayer1Id inList playerIds))
                    }
                    .forEach { row ->
                        val homeId = row[Games.homePlayer1Id]
                        val awayId = row[Games.awayPlayer1Id]
                        when (row[Games.result]) {
                            GameResult.HOME -> {
                                if (homeId in playerIds) wins[homeId!!] = (wins[homeId] ?: 0) + 1
                                if (awayId in playerIds) losses[awayId!!] = (losses[awayId] ?: 0) + 1
                            }
                            GameResult.AWAY -> {
                                if (awayId in playerIds) wins[awayId!!] = (wins[awayId] ?: 0) + 1
                                if (homeId in playerIds) losses[homeId!!] = (losses[homeId] ?: 0) + 1
                            }
                            else -> Unit
                        }
                    }
            }

            val classByPlayer = ClassificationService.currentClasses(playerIds)

            playerRows
                .sortedByDescending { (wins[it[Players.id]] ?: 0) + (losses[it[Players.id]] ?: 0) }
                .map { playerRow ->
                    val pId = playerRow[Players.id]
                    TeamPlayerResponse(
                        id = pId.toString(),
                        fullName = playerRow[Players.fullName],
                        licenceNr = playerRow[Players.licenceNr],
                        classification = classByPlayer[pId],
                        wins = wins[pId] ?: 0,
                        losses = losses[pId] ?: 0,
                    )
                }
        }
    }

    suspend fun getTeamMatches(teamId: String): List<MatchResponse>? {
        val uuid = teamId.toUuidOrNull() ?: return null
        return dbQuery {
            if (Teams.select(Teams.id).where { Teams.id eq uuid }.empty()) return@dbQuery null

            Matches
                .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
                .select(
                    Matches.id,
                    Matches.homeTeamId,
                    Matches.awayTeamId,
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.round,
                    Matches.playedAt,
                    Matches.status,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                )
                .where { (Matches.homeTeamId eq uuid) or (Matches.awayTeamId eq uuid) }
                .orderBy(Matches.playedAt to SortOrder.DESC)
                .map { it.toMatchResponse() }
        }
    }

    private fun calculateStreak(results: List<String>): String {
        if (results.isEmpty()) return "-"
        val first = results.first()
        val count = results.takeWhile { it == first }.size
        return "$count$first"
    }

    private fun ResultRow.toMatchResult(teamId: UUID): String {
        val home = this[Matches.homeScore] ?: 0
        val away = this[Matches.awayScore] ?: 0
        return if (this[Matches.homeTeamId] == teamId) {
            if (home > away) {
                "W"
            } else if (home < away) {
                "L"
            } else {
                "D"
            }
        } else {
            if (away > home) {
                "W"
            } else if (away < home) {
                "L"
            } else {
                "D"
            }
        }
    }

    private fun ResultRow.toMatchResponse() =
        MatchResponse(
            id = this[Matches.id].toString(),
            homeTeamId = this[Matches.homeTeamId].toString(),
            awayTeamId = this[Matches.awayTeamId].toString(),
            homeTeam = this[homeTeam[Teams.name]],
            awayTeam = this[awayTeam[Teams.name]],
            homeScore = this[Matches.homeScore]?.toInt(),
            awayScore = this[Matches.awayScore]?.toInt(),
            round = this[Matches.round],
            playedAt = this[Matches.playedAt]?.toString(),
            status = this[Matches.status],
        )
}
