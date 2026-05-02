package com.ttfeed.service

import com.ttfeed.database.*
import com.ttfeed.model.GameResponse
import com.ttfeed.model.MatchDetailResponse
import com.ttfeed.model.MatchResponse
import com.ttfeed.model.SetResponse
import com.ttfeed.util.toUuidOrNull
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and

object MatchService {
    private val homeTeam = Teams.alias("home_team")
    private val awayTeam = Teams.alias("away_team")
    private val homePlayer = Players.alias("home_player")
    private val homePlayer2 = Players.alias("home_player2")
    private val awayPlayer = Players.alias("away_player")
    private val awayPlayer2 = Players.alias("away_player2")

    suspend fun getForGroup(groupId: String): List<MatchResponse>? {
        val uuid = groupId.toUuidOrNull() ?: return null
        return dbQuery {
            Matches
                .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
                .select(
                    Matches.id,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.round,
                    Matches.playedAt,
                    Matches.status,
                )
                .where { Matches.groupId eq uuid }
                .map { it.toMatchResponse() }
        }
    }

    suspend fun getById(matchId: String): MatchDetailResponse? {
        val uuid = matchId.toUuidOrNull() ?: return null
        return dbQuery {
            // Step 1 — fetch the match with team names + season (via group)
            val matchRow =
                Matches
                    .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
                    .join(Groups, JoinType.INNER, Matches.groupId, Groups.id)
                    .select(
                        Matches.id,
                        homeTeam[Teams.name],
                        awayTeam[Teams.name],
                        Matches.homeScore,
                        Matches.awayScore,
                        Matches.round,
                        Matches.playedAt,
                        Matches.status,
                        Groups.seasonId,
                    )
                    .where { Matches.id eq uuid }
                    .firstOrNull() ?: return@dbQuery null

            val seasonId = matchRow[Groups.seasonId]

            // Step 2 — fetch all games with player names
            val gameRows =
                Games
                    .join(homePlayer, JoinType.LEFT, Games.homePlayer1Id, homePlayer[Players.id])
                    .join(homePlayer2, JoinType.LEFT, Games.homePlayer2Id, homePlayer2[Players.id])
                    .join(awayPlayer, JoinType.LEFT, Games.awayPlayer1Id, awayPlayer[Players.id])
                    .join(awayPlayer2, JoinType.LEFT, Games.awayPlayer2Id, awayPlayer2[Players.id])
                    .select(
                        Games.id,
                        Games.orderInMatch,
                        Games.competitionName,
                        Games.gameType,
                        Games.homeSets,
                        Games.awaySets,
                        Games.result,
                        Games.homePlayer1Id,
                        Games.homePlayer2Id,
                        Games.awayPlayer1Id,
                        Games.awayPlayer2Id,
                        homePlayer[Players.fullName],
                        homePlayer2[Players.fullName],
                        awayPlayer[Players.fullName],
                        awayPlayer2[Players.fullName],
                        Games.homePlayer1EloDelta,
                        Games.awayPlayer1EloDelta,
                    )
                    .where { Games.matchId eq uuid }
                    .orderBy(Games.orderInMatch to SortOrder.ASC)

            // Step 3 — batch-fetch klass for all players in these games
            val playerIds =
                gameRows.flatMap { row ->
                    listOfNotNull(
                        row.getOrNull(Games.homePlayer1Id),
                        row.getOrNull(Games.homePlayer2Id),
                        row.getOrNull(Games.awayPlayer1Id),
                        row.getOrNull(Games.awayPlayer2Id),
                    )
                }.distinct()
            val klassMap =
                if (playerIds.isEmpty()) {
                    emptyMap()
                } else {
                    PlayerSeasons
                        .select(PlayerSeasons.playerId, PlayerSeasons.klass)
                        .where { (PlayerSeasons.playerId inList playerIds) and (PlayerSeasons.seasonId eq seasonId) }
                        .associate { it[PlayerSeasons.playerId] to it[PlayerSeasons.klass] }
                }

            // Step 4 — fetch all sets for all games in one query
            val gameIds = gameRows.map { it[Games.id] }
            val setsByGame =
                if (gameIds.isEmpty()) {
                    emptyMap()
                } else {
                    GameSets
                        .select(GameSets.gameId, GameSets.setNumber, GameSets.homePoints, GameSets.awayPoints)
                        .where { GameSets.gameId inList gameIds }
                        .orderBy(GameSets.setNumber to SortOrder.ASC)
                        .groupBy { it[GameSets.gameId] }
                }

            // Step 5 — assemble nested response
            val games =
                gameRows.map { gameRow ->
                    val gameId = gameRow[Games.id]
                    val sets =
                        setsByGame[gameId]?.map { setRow ->
                            SetResponse(
                                setNumber = setRow[GameSets.setNumber].toInt(),
                                homePoints = setRow[GameSets.homePoints].toInt(),
                                awayPoints = setRow[GameSets.awayPoints].toInt(),
                            )
                        } ?: emptyList()

                    GameResponse(
                        id = gameId.toString(),
                        orderInMatch = gameRow[Games.orderInMatch]?.toInt(),
                        competitionName = gameRow[Games.competitionName],
                        gameType = gameRow[Games.gameType],
                        homePlayerId = gameRow.getOrNull(Games.homePlayer1Id)?.toString(),
                        homePlayer2Id = gameRow.getOrNull(Games.homePlayer2Id)?.toString(),
                        awayPlayerId = gameRow.getOrNull(Games.awayPlayer1Id)?.toString(),
                        awayPlayer2Id = gameRow.getOrNull(Games.awayPlayer2Id)?.toString(),
                        homePlayerName = gameRow[homePlayer[Players.fullName]],
                        homePlayer2Name = gameRow.getOrNull(homePlayer2[Players.fullName]),
                        awayPlayerName = gameRow[awayPlayer[Players.fullName]],
                        awayPlayer2Name = gameRow.getOrNull(awayPlayer2[Players.fullName]),
                        homePlayerKlass = gameRow.getOrNull(Games.homePlayer1Id)?.let { klassMap[it] },
                        homePlayer2Klass = gameRow.getOrNull(Games.homePlayer2Id)?.let { klassMap[it] },
                        awayPlayerKlass = gameRow.getOrNull(Games.awayPlayer1Id)?.let { klassMap[it] },
                        awayPlayer2Klass = gameRow.getOrNull(Games.awayPlayer2Id)?.let { klassMap[it] },
                        homeSets = gameRow[Games.homeSets]?.toInt(),
                        awaySets = gameRow[Games.awaySets]?.toInt(),
                        result = gameRow[Games.result],
                        homePlayer1EloDelta = gameRow[Games.homePlayer1EloDelta],
                        awayPlayer1EloDelta = gameRow[Games.awayPlayer1EloDelta],
                        sets = sets,
                    )
                }

            MatchDetailResponse(
                id = matchRow[Matches.id].toString(),
                homeTeam = matchRow[homeTeam[Teams.name]],
                awayTeam = matchRow[awayTeam[Teams.name]],
                homeScore = matchRow[Matches.homeScore]?.toInt(),
                awayScore = matchRow[Matches.awayScore]?.toInt(),
                round = matchRow[Matches.round],
                playedAt = matchRow[Matches.playedAt]?.toString(),
                status = matchRow[Matches.status],
                games = games,
            )
        }
    }

    private fun ResultRow.toMatchResponse() =
        MatchResponse(
            id = this[Matches.id].toString(),
            homeTeam = this[homeTeam[Teams.name]],
            awayTeam = this[awayTeam[Teams.name]],
            homeScore = this[Matches.homeScore]?.toInt(),
            awayScore = this[Matches.awayScore]?.toInt(),
            round = this[Matches.round],
            playedAt = this[Matches.playedAt]?.toString(),
            status = this[Matches.status],
        )
}
