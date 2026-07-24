package com.ttscore.service

import com.ttscore.database.Follows
import com.ttscore.database.Games
import com.ttscore.database.Groups
import com.ttscore.database.Matches
import com.ttscore.database.PlayerClassifications
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.database.dbQuery
import com.ttscore.model.FeedEventResponse
import com.ttscore.model.FollowTargetType
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Builds the home-dashboard "recent activity" feed: the most recent events (match results, class
 * changes, upcoming fixtures) across everything a user follows.
 *
 * Each event category below is fetched with its own `ORDER BY <date> DESC LIMIT` query against an
 * already-indexed date column, rather than pulling every followed entity's *entire* history into
 * memory and slicing client-side to 5 items (the previous approach — see `feed.ts`'s old
 * `resolveFeed`). Taking the top-`limit` candidates from each category and merging them is
 * sufficient to guarantee the true global top-`limit`: any event beyond position `limit` in its
 * own category's date ordering cannot be in the global top-`limit` either, since `limit` newer
 * events from that same category already precede it.
 */
object FeedService {
    /**
     * Games are fetched at game (not match) granularity, and several can share one match (e.g. a
     * doubles + singles game on the same day) — over-fetch by this factor before grouping so we
     * still surface `limit` distinct matches after the group-by, without pulling unbounded history.
     */
    private const val PLAYER_GAME_FETCH_MULTIPLIER = 10

    /** Fixtures surface in the feed starting one day before throw-off, same as the old client logic. */
    private val UPCOMING_WINDOW: Duration = Duration.ofHours(24)

    suspend fun getFeedPreview(
        userId: String,
        limit: Int,
    ): List<FeedEventResponse> =
        dbQuery {
            val follows = Follows.selectAll().where { Follows.userId eq userId }.toList()
            if (follows.isEmpty()) return@dbQuery emptyList()

            val playerIds =
                follows.filter { it[Follows.targetType] == FollowTargetType.PLAYER }.map { it[Follows.targetId] }
            val teamIds =
                follows.filter { it[Follows.targetType] == FollowTargetType.TEAM }.map { it[Follows.targetId] }
            val groupIds =
                follows
                    .filter { it[Follows.targetType] == FollowTargetType.DIVISION_GROUP }
                    .map { it[Follows.targetId] }

            val playerNames = namesOf(Players, Players.id, Players.fullName, playerIds)
            val teamNames = namesOf(Teams, Teams.id, Teams.name, teamIds)
            val groupNames = namesOf(Groups, Groups.id, Groups.name, groupIds)

            val events = mutableListOf<FeedEventResponse>()
            events += playerMatchEvents(playerIds, playerNames, limit)
            events += classChangeEvents(playerIds, playerNames)
            events += teamMatchEvents(teamIds, teamNames, limit)
            events += groupMatchEvents(groupIds, groupNames, limit)

            events.sortedByDescending { it.sortKey }.take(limit)
        }

    private fun <T : Table> namesOf(
        table: T,
        idCol: Column<UUID>,
        nameCol: Column<String>,
        ids: List<UUID>,
    ): Map<UUID, String> {
        if (ids.isEmpty()) return emptyMap()
        return table.select(idCol, nameCol).where { idCol inList ids }.associate { it[idCol] to it[nameCol] }
    }

    private data class PlayerGameRow(
        val isHome: Boolean,
        val result: GameResult,
        val playedAt: OffsetDateTime?,
        val homeTeamName: String?,
        val awayTeamName: String?,
    )

    private fun playerMatchEvents(
        playerIds: List<UUID>,
        playerNames: Map<UUID, String>,
        limit: Int,
    ): List<FeedEventResponse> {
        if (playerIds.isEmpty()) return emptyList()
        val playerIdSet = playerIds.toSet()
        val homeTeam = Teams.alias("pme_home_team")
        val awayTeam = Teams.alias("pme_away_team")

        val rows =
            Games
                .join(Matches, JoinType.INNER, Games.matchId, Matches.id)
                .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                .select(
                    Games.matchId,
                    Games.homePlayer1Id,
                    Games.awayPlayer1Id,
                    Games.result,
                    Games.playedAt,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                )
                .where {
                    ((Games.homePlayer1Id inList playerIds) or (Games.awayPlayer1Id inList playerIds)) and
                        (Games.gameType eq GameType.SINGLES) and
                        (Games.result neq GameResult.NOT_PLAYED) and
                        (Matches.status neq MatchStatus.SCHEDULED)
                }
                .orderBy(Games.playedAt to SortOrder.DESC_NULLS_LAST)
                .limit(limit * PLAYER_GAME_FETCH_MULTIPLIER)
                .toList()

        // Group by (matchId, followedPlayerId) — a row matches on either side, or (rarely) both if
        // the user follows both players in the same game.
        val grouped = LinkedHashMap<Pair<UUID, UUID>, MutableList<PlayerGameRow>>()
        for (row in rows) {
            val matchId = row[Games.matchId] ?: continue
            val homeId = row[Games.homePlayer1Id]
            val awayId = row[Games.awayPlayer1Id]
            val homeTeamName = row.getOrNull(homeTeam[Teams.name])
            val awayTeamName = row.getOrNull(awayTeam[Teams.name])
            val playedAt = row[Games.playedAt]
            val result = row[Games.result]
            if (homeId != null && homeId in playerIdSet) {
                grouped.getOrPut(matchId to homeId) { mutableListOf() }
                    .add(PlayerGameRow(true, result, playedAt, homeTeamName, awayTeamName))
            }
            if (awayId != null && awayId in playerIdSet) {
                grouped.getOrPut(matchId to awayId) { mutableListOf() }
                    .add(PlayerGameRow(false, result, playedAt, homeTeamName, awayTeamName))
            }
        }

        return grouped.entries
            .mapNotNull { (key, gameRows) ->
                val (matchId, followedPlayerId) = key
                val name = playerNames[followedPlayerId] ?: return@mapNotNull null
                val myWins =
                    gameRows.count {
                        (it.isHome && it.result == GameResult.HOME) || (!it.isHome && it.result == GameResult.AWAY)
                    }
                val oppWins =
                    gameRows.count {
                        (it.isHome && it.result == GameResult.AWAY) || (!it.isHome && it.result == GameResult.HOME)
                    }
                val result =
                    if (myWins > oppWins) {
                        "WIN"
                    } else if (myWins < oppWins) {
                        "LOSS"
                    } else {
                        "DRAW"
                    }
                val first = gameRows.first()
                val opponentTeam = if (first.isHome) first.awayTeamName else first.homeTeamName
                val playedAt = gameRows.mapNotNull { it.playedAt }.maxOrNull()
                FeedEventResponse(
                    key = "player-$followedPlayerId-match-$matchId",
                    entityType = "player",
                    entityName = name,
                    entityHref = "/matches/$matchId",
                    kind = "player_match",
                    sortKey = playedAt?.toString() ?: "",
                    result = result,
                    opponentTeam = opponentTeam ?: "—",
                    matchScore = "$myWins–$oppWins",
                    playedAt = playedAt?.toString(),
                )
            }
            .sortedByDescending { it.sortKey }
            .take(limit)
    }

    /** [ClassificationService.classificationRank]-style numeric suffix, e.g. "B14" -> 14. */
    private fun classRank(classification: String): Int = classification.drop(1).toIntOrNull() ?: 0

    private fun classChangeEvents(
        playerIds: List<UUID>,
        playerNames: Map<UUID, String>,
    ): List<FeedEventResponse> {
        // player_classification is small per player (one row per season) — no LIMIT needed here,
        // unlike the game/match categories above.
        if (playerIds.isEmpty()) return emptyList()
        val rows =
            (PlayerClassifications innerJoin Seasons)
                .select(
                    PlayerClassifications.playerId,
                    Seasons.name,
                    PlayerClassifications.firstHalfClass,
                    PlayerClassifications.secondHalfClass,
                )
                .where { PlayerClassifications.playerId inList playerIds }
                .orderBy(Seasons.name to SortOrder.DESC)
                .toList()

        val events = mutableListOf<FeedEventResponse>()
        for ((playerId, playerRows) in rows.groupBy { it[PlayerClassifications.playerId] }) {
            val name = playerNames[playerId] ?: continue
            // One entry per season — the latest known half (second falls back to first), newest first
            // (query is already ordered by season desc).
            val entries =
                playerRows.mapNotNull { row ->
                    val secondHalf = row[PlayerClassifications.secondHalfClass]
                    val classification =
                        secondHalf ?: row[PlayerClassifications.firstHalfClass] ?: return@mapNotNull null
                    val half =
                        if (secondHalf != null) ClassificationService.Half.SECOND else ClassificationService.Half.FIRST
                    classification to ClassificationService.effectiveDateOf(row[Seasons.name], half)
                }
            for (i in 0 until entries.size - 1) {
                val (currentClass, currentDate) = entries[i]
                val (previousClass, _) = entries[i + 1]
                if (currentClass == previousClass) continue
                val direction = if (classRank(currentClass) > classRank(previousClass)) "UP" else "DOWN"
                events +=
                    FeedEventResponse(
                        key = "player-$playerId-class-$currentDate",
                        entityType = "player",
                        entityName = name,
                        entityHref = "/players/$playerId",
                        kind = "class_change",
                        sortKey = currentDate.toString(),
                        direction = direction,
                        fromClass = previousClass,
                        toClass = currentClass,
                        effectiveDate = currentDate.toString(),
                    )
            }
        }
        return events
    }

    private fun teamMatchEvents(
        teamIds: List<UUID>,
        teamNames: Map<UUID, String>,
        limit: Int,
    ): List<FeedEventResponse> {
        if (teamIds.isEmpty()) return emptyList()
        val teamIdSet = teamIds.toSet()
        val homeTeam = Teams.alias("tme_home_team")
        val awayTeam = Teams.alias("tme_away_team")

        fun baseQuery() =
            Matches
                .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                .select(
                    Matches.id,
                    Matches.homeTeamId,
                    Matches.awayTeamId,
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.playedAt,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                )
                .where { (Matches.homeTeamId inList teamIds) or (Matches.awayTeamId inList teamIds) }

        val completed =
            baseQuery()
                .andWhere { Matches.status neq MatchStatus.SCHEDULED }
                .orderBy(Matches.playedAt to SortOrder.DESC_NULLS_LAST)
                .limit(limit)
                .toList()

        val now = OffsetDateTime.now()
        val upcoming =
            baseQuery()
                .andWhere {
                    (Matches.status eq MatchStatus.SCHEDULED) and
                        (Matches.playedAt greaterEq now) and
                        (Matches.playedAt less now.plus(UPCOMING_WINDOW))
                }
                .orderBy(Matches.playedAt to SortOrder.ASC_NULLS_LAST)
                .limit(limit)
                .toList()

        val events = mutableListOf<FeedEventResponse>()
        for (row in completed) {
            val matchId = row[Matches.id]
            val homeId = row[Matches.homeTeamId]
            val awayId = row[Matches.awayTeamId]
            val homeScore = row[Matches.homeScore]?.toInt()
            val awayScore = row[Matches.awayScore]?.toInt()
            val playedAt = row[Matches.playedAt]
            val homeName = row.getOrNull(homeTeam[Teams.name])
            val awayName = row.getOrNull(awayTeam[Teams.name])
            for (followedId in followedSides(homeId, awayId, teamIdSet)) {
                val name = teamNames[followedId] ?: continue
                val isHomeSide = followedId == homeId
                val myScore = if (isHomeSide) homeScore else awayScore
                val oppScore = if (isHomeSide) awayScore else homeScore
                val result =
                    when {
                        myScore == null || oppScore == null -> "DRAW"
                        myScore > oppScore -> "WIN"
                        myScore < oppScore -> "LOSS"
                        else -> "DRAW"
                    }
                events +=
                    FeedEventResponse(
                        key = "team-$followedId-match-$matchId",
                        entityType = "team",
                        entityName = name,
                        entityHref = "/matches/$matchId",
                        kind = "team_match",
                        sortKey = playedAt?.toString() ?: "",
                        result = result,
                        opponent = if (isHomeSide) awayName else homeName,
                        score = if (myScore != null && oppScore != null) "$myScore–$oppScore" else "?–?",
                        playedAt = playedAt?.toString(),
                    )
            }
        }
        for (row in upcoming) {
            val matchId = row[Matches.id]
            val homeId = row[Matches.homeTeamId]
            val awayId = row[Matches.awayTeamId]
            val playedAt = row[Matches.playedAt]
            val homeName = row.getOrNull(homeTeam[Teams.name])
            val awayName = row.getOrNull(awayTeam[Teams.name])
            for (followedId in followedSides(homeId, awayId, teamIdSet)) {
                val name = teamNames[followedId] ?: continue
                events +=
                    FeedEventResponse(
                        key = "team-$followedId-upcoming-$matchId",
                        entityType = "team",
                        entityName = name,
                        entityHref = "/matches/$matchId",
                        kind = "upcoming_match",
                        sortKey = playedAt?.toString() ?: "",
                        playedAt = playedAt?.toString(),
                        homeTeam = homeName,
                        awayTeam = awayName,
                    )
            }
        }
        return events
    }

    private fun groupMatchEvents(
        groupIds: List<UUID>,
        groupNames: Map<UUID, String>,
        limit: Int,
    ): List<FeedEventResponse> {
        if (groupIds.isEmpty()) return emptyList()
        val homeTeam = Teams.alias("gme_home_team")
        val awayTeam = Teams.alias("gme_away_team")

        fun baseQuery() =
            Matches
                .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                .select(
                    Matches.id,
                    Matches.groupId,
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.playedAt,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                )
                .where { Matches.groupId inList groupIds }

        val completed =
            baseQuery()
                .andWhere {
                    (Matches.status neq MatchStatus.SCHEDULED) and
                        Matches.homeScore.isNotNull() and
                        Matches.awayScore.isNotNull()
                }
                .orderBy(Matches.playedAt to SortOrder.DESC_NULLS_LAST)
                .limit(limit)
                .toList()

        val now = OffsetDateTime.now()
        val upcoming =
            baseQuery()
                .andWhere {
                    (Matches.status eq MatchStatus.SCHEDULED) and
                        (Matches.playedAt greaterEq now) and
                        (Matches.playedAt less now.plus(UPCOMING_WINDOW))
                }
                .orderBy(Matches.playedAt to SortOrder.ASC_NULLS_LAST)
                .limit(limit)
                .toList()

        val events = mutableListOf<FeedEventResponse>()
        for (row in completed) {
            val groupId = row[Matches.groupId]
            val name = groupNames[groupId] ?: continue
            val matchId = row[Matches.id]
            val playedAt = row[Matches.playedAt]
            events +=
                FeedEventResponse(
                    key = "group-$groupId-match-$matchId",
                    entityType = "division_group",
                    entityName = name,
                    entityHref = "/matches/$matchId",
                    kind = "group_match",
                    sortKey = playedAt?.toString() ?: "",
                    score = "${row[Matches.homeScore]}–${row[Matches.awayScore]}",
                    playedAt = playedAt?.toString(),
                    homeTeam = row.getOrNull(homeTeam[Teams.name]),
                    awayTeam = row.getOrNull(awayTeam[Teams.name]),
                )
        }
        for (row in upcoming) {
            val groupId = row[Matches.groupId]
            val name = groupNames[groupId] ?: continue
            val matchId = row[Matches.id]
            val playedAt = row[Matches.playedAt]
            events +=
                FeedEventResponse(
                    key = "group-$groupId-upcoming-$matchId",
                    entityType = "division_group",
                    entityName = name,
                    entityHref = "/matches/$matchId",
                    kind = "upcoming_match",
                    sortKey = playedAt?.toString() ?: "",
                    playedAt = playedAt?.toString(),
                    homeTeam = row.getOrNull(homeTeam[Teams.name]),
                    awayTeam = row.getOrNull(awayTeam[Teams.name]),
                )
        }
        return events
    }

    /** The followed team id(s) among a match's two sides — usually one, rarely both. */
    private fun followedSides(
        homeId: UUID,
        awayId: UUID,
        followedIds: Set<UUID>,
    ): List<UUID> =
        listOfNotNull(
            homeId.takeIf { it in followedIds },
            awayId.takeIf { it in followedIds && it != homeId },
        )
}
