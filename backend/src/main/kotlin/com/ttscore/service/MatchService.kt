package com.ttscore.service

import com.ttscore.database.*
import com.ttscore.model.GameResponse
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchDetailResponse
import com.ttscore.model.MatchPreviewResponse
import com.ttscore.model.MatchResponse
import com.ttscore.model.MatchStatus
import com.ttscore.model.PlayerMatchPreviewResponse
import com.ttscore.model.PreviewMatchupResponse
import com.ttscore.model.PreviewPlayerResponse
import com.ttscore.model.PreviewPriorMeetingResponse
import com.ttscore.model.PreviewTeamResponse
import com.ttscore.model.SetResponse
import com.ttscore.model.TeamPlayerResponse
import com.ttscore.util.toUuidOrNull
import org.jetbrains.exposed.sql.*
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.abs

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
                    Matches.homeTeamId,
                    Matches.awayTeamId,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.round,
                    Matches.playedAt,
                    Matches.status,
                )
                .where { Matches.groupId eq uuid }
                .orderBy(Matches.playedAt to SortOrder.DESC)
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
                        Matches.homeTeamId,
                        Matches.awayTeamId,
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
            // Class as it was at match time (the half the match date falls in).
            val matchLocalDate = matchRow[Matches.playedAt]?.let { ClassificationService.localDateOf(it) }
            val classMap =
                if (playerIds.isEmpty() || matchLocalDate == null) {
                    emptyMap()
                } else {
                    ClassificationService.classesForSeasonHalf(
                        playerIds,
                        seasonId,
                        ClassificationService.halfOf(matchLocalDate),
                    )
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
                        homePlayerClassification = gameRow.getOrNull(Games.homePlayer1Id)?.let { classMap[it] },
                        homePlayer2Classification = gameRow.getOrNull(Games.homePlayer2Id)?.let { classMap[it] },
                        awayPlayerClassification = gameRow.getOrNull(Games.awayPlayer1Id)?.let { classMap[it] },
                        awayPlayer2Classification = gameRow.getOrNull(Games.awayPlayer2Id)?.let { classMap[it] },
                        homeSets = gameRow[Games.homeSets]?.toInt(),
                        awaySets = gameRow[Games.awaySets]?.toInt(),
                        result = gameRow[Games.result],
                        sets = sets,
                    )
                }

            MatchDetailResponse(
                id = matchRow[Matches.id].toString(),
                homeTeamId = matchRow[Matches.homeTeamId].toString(),
                awayTeamId = matchRow[Matches.awayTeamId].toString(),
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

    // ── Match preview ───────────────────────────────────────────────────────────────
    // Neutral, roster-based look-ahead at a fixture (typically SCHEDULED): both teams' standing
    // and form, the first-leg result if already played, and the most interesting projected
    // singles duels across the two rosters. A Pro feature; the route enforces the gate.

    private const val MAX_MATCHUPS = 3
    private const val RIVALRY_CAP = 6

    suspend fun getMatchPreview(matchId: String): MatchPreviewResponse? {
        val uuid = matchId.toUuidOrNull() ?: return null

        // 1. Base fixture — teams, group, status (own transaction).
        val base =
            dbQuery {
                Matches
                    .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
                    .join(Groups, JoinType.INNER, Matches.groupId, Groups.id)
                    .select(
                        Matches.homeTeamId,
                        Matches.awayTeamId,
                        homeTeam[Teams.name],
                        awayTeam[Teams.name],
                        Matches.groupId,
                        Groups.name,
                        Matches.round,
                        Matches.playedAt,
                        Matches.status,
                    )
                    .where { Matches.id eq uuid }
                    .firstOrNull()
            } ?: return null

        val homeTeamId = base[Matches.homeTeamId]
        val awayTeamId = base[Matches.awayTeamId]
        val groupId = base[Matches.groupId]

        // 2. Rosters (classification + season W/L) — reuse the team service.
        val homeRosterBase = TeamService.getTeamRoster(homeTeamId.toString()).orEmpty()
        val awayRosterBase = TeamService.getTeamRoster(awayTeamId.toString()).orEmpty()

        return dbQuery {
            // Standings rows for both teams — the "tale of the tape".
            val standingsByTeam =
                Standings
                    .selectAll()
                    .where { Standings.teamId inList listOf(homeTeamId, awayTeamId) }
                    .associateBy { it[Standings.teamId] }

            // ELO for the whole roster union in one query.
            val homeIds = homeRosterBase.map { UUID.fromString(it.id) }.toSet()
            val awayIds = awayRosterBase.map { UUID.fromString(it.id) }.toSet()
            val elos = LiveEloService.baseElos(homeIds + awayIds)

            val homePlayers = toPreviewPlayers(homeRosterBase, elos)
            val awayPlayers = toPreviewPlayers(awayRosterBase, elos)

            val pairStats = crossPairStats(homeIds, awayIds)

            MatchPreviewResponse(
                matchId = uuid.toString(),
                groupId = groupId.toString(),
                groupName = base[Groups.name],
                round = base[Matches.round],
                playedAt = base[Matches.playedAt]?.toString(),
                status = base[Matches.status],
                home = buildTeam(homeTeamId, base[homeTeam[Teams.name]], standingsByTeam[homeTeamId], teamForm(homeTeamId), homePlayers),
                away = buildTeam(awayTeamId, base[awayTeam[Teams.name]], standingsByTeam[awayTeamId], teamForm(awayTeamId), awayPlayers),
                previousMeeting = findPreviousMeeting(uuid, groupId, homeTeamId, awayTeamId),
                keyMatchups = rankMatchups(homePlayers, awayPlayers, pairStats),
            )
        }
    }

    /**
     * Player-centric preview of one fixture: the same team header data as [getMatchPreview]
     * (rosters omitted) plus the focus player's form, aggregate record vs the opposing lineup,
     * and a duel entry per opponent-roster player. Null when the player is in neither roster.
     */
    suspend fun getPlayerMatchPreview(
        playerId: String,
        matchId: String,
    ): PlayerMatchPreviewResponse? {
        val playerUuid = playerId.toUuidOrNull() ?: return null
        val uuid = matchId.toUuidOrNull() ?: return null

        val base =
            dbQuery {
                Matches
                    .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
                    .join(Groups, JoinType.INNER, Matches.groupId, Groups.id)
                    .select(
                        Matches.homeTeamId,
                        Matches.awayTeamId,
                        homeTeam[Teams.name],
                        awayTeam[Teams.name],
                        Matches.groupId,
                        Groups.name,
                        Matches.round,
                        Matches.playedAt,
                        Matches.status,
                    )
                    .where { Matches.id eq uuid }
                    .firstOrNull()
            } ?: return null

        val homeTeamId = base[Matches.homeTeamId]
        val awayTeamId = base[Matches.awayTeamId]
        val groupId = base[Matches.groupId]

        val homeRosterBase = TeamService.getTeamRoster(homeTeamId.toString()).orEmpty()
        val awayRosterBase = TeamService.getTeamRoster(awayTeamId.toString()).orEmpty()

        val isHome =
            when {
                homeRosterBase.any { it.id == playerId } -> true
                awayRosterBase.any { it.id == playerId } -> false
                else -> return null
            }

        return dbQuery {
            val standingsByTeam =
                Standings
                    .selectAll()
                    .where { Standings.teamId inList listOf(homeTeamId, awayTeamId) }
                    .associateBy { it[Standings.teamId] }

            val homeIds = homeRosterBase.map { UUID.fromString(it.id) }.toSet()
            val awayIds = awayRosterBase.map { UUID.fromString(it.id) }.toSet()
            val elos = LiveEloService.baseElos(homeIds + awayIds)

            val playerBase = (if (isHome) homeRosterBase else awayRosterBase).first { it.id == playerId }
            val player =
                PreviewPlayerResponse(
                    id = playerBase.id,
                    fullName = playerBase.fullName,
                    classification = playerBase.classification,
                    elo = elos[playerUuid],
                    wins = playerBase.wins,
                    losses = playerBase.losses,
                )
            val opponents = toPreviewPlayers(if (isHome) awayRosterBase else homeRosterBase, elos)

            // Direct singles records vs every opponent-roster player, oriented (player, opponent).
            val opponentIds = (if (isHome) awayIds else homeIds) - playerUuid
            val pairStats = crossPairStats(setOf(playerUuid), opponentIds)

            val duels =
                opponents.map { opp ->
                    val stat = pairStats[playerUuid to UUID.fromString(opp.id)]
                    PreviewMatchupResponse(
                        homePlayer = player,
                        awayPlayer = opp,
                        homeWins = stat?.homeWins ?: 0,
                        awayWins = stat?.awayWins ?: 0,
                        meetings = stat?.meetings ?: 0,
                        lastPlayedAt = stat?.lastPlayedAt?.toString(),
                        homeWinProbability =
                            if (player.elo != null && opp.elo != null) {
                                EloCalculationService.winProbability(player.elo, opp.elo)
                            } else {
                                null
                            },
                        results = stat?.results.orEmpty(),
                    )
                }

            PlayerMatchPreviewResponse(
                matchId = uuid.toString(),
                groupId = groupId.toString(),
                groupName = base[Groups.name],
                round = base[Matches.round],
                playedAt = base[Matches.playedAt]?.toString(),
                status = base[Matches.status],
                home = buildTeam(homeTeamId, base[homeTeam[Teams.name]], standingsByTeam[homeTeamId], teamForm(homeTeamId), emptyList()),
                away = buildTeam(awayTeamId, base[awayTeam[Teams.name]], standingsByTeam[awayTeamId], teamForm(awayTeamId), emptyList()),
                isHome = isHome,
                player = player,
                duels = duels,
            )
        }
    }

    /** Enriches a roster with ELO and sorts strongest-first (ELO, then classification rank). */
    private fun toPreviewPlayers(
        roster: List<TeamPlayerResponse>,
        elos: Map<UUID, Int>,
    ): List<PreviewPlayerResponse> =
        roster
            .map { p ->
                PreviewPlayerResponse(
                    id = p.id,
                    fullName = p.fullName,
                    classification = p.classification,
                    elo = elos[UUID.fromString(p.id)],
                    wins = p.wins,
                    losses = p.losses,
                )
            }
            .sortedWith(
                compareByDescending<PreviewPlayerResponse> { it.elo ?: Int.MIN_VALUE }
                    .thenByDescending { classRank(it.classification) },
            )

    private fun buildTeam(
        teamId: UUID,
        teamName: String,
        standing: ResultRow?,
        form: List<String>,
        roster: List<PreviewPlayerResponse>,
    ): PreviewTeamResponse {
        val won = standing?.get(Standings.won)?.toInt() ?: 0
        val drawn = standing?.get(Standings.drawn)?.toInt() ?: 0
        val lost = standing?.get(Standings.lost)?.toInt() ?: 0
        val gamesFor = standing?.get(Standings.gamesFor)?.toInt() ?: 0
        val gamesAgainst = standing?.get(Standings.gamesAgainst)?.toInt() ?: 0
        return PreviewTeamResponse(
            teamId = teamId.toString(),
            teamName = teamName,
            position = standing?.get(Standings.position)?.toInt() ?: 0,
            played = standing?.get(Standings.played)?.toInt() ?: 0,
            points = standing?.get(Standings.points)?.toInt() ?: 0,
            gamesDiff = gamesFor - gamesAgainst,
            record = "$won-$drawn-$lost",
            form = form,
            roster = roster,
        )
    }

    /** Last five decided matches for a team, newest first, as "W"/"L"/"D". Assumes an open tx. */
    private fun teamForm(teamId: UUID): List<String> =
        Matches
            .select(Matches.homeTeamId, Matches.homeScore, Matches.awayScore)
            .where {
                ((Matches.homeTeamId eq teamId) or (Matches.awayTeamId eq teamId)) and
                    (Matches.status eq MatchStatus.COMPLETED)
            }
            .orderBy(Matches.playedAt to SortOrder.DESC_NULLS_LAST)
            .limit(5)
            .map { row ->
                val home = row[Matches.homeScore] ?: 0
                val away = row[Matches.awayScore] ?: 0
                val isHome = row[Matches.homeTeamId] == teamId
                val mine = if (isHome) home else away
                val theirs = if (isHome) away else home
                if (mine > theirs) "W" else if (mine < theirs) "L" else "D"
            }

    /** The most recent completed meeting between the two teams in the group, if any. Assumes an open tx. */
    private fun findPreviousMeeting(
        currentMatchId: UUID,
        groupId: UUID,
        teamA: UUID,
        teamB: UUID,
    ): PreviewPriorMeetingResponse? =
        Matches
            .join(homeTeam, JoinType.INNER, Matches.homeTeamId, homeTeam[Teams.id])
            .join(awayTeam, JoinType.INNER, Matches.awayTeamId, awayTeam[Teams.id])
            .select(
                Matches.id,
                homeTeam[Teams.name],
                awayTeam[Teams.name],
                Matches.homeScore,
                Matches.awayScore,
                Matches.playedAt,
                Matches.round,
            )
            .where {
                (Matches.groupId eq groupId) and
                    (Matches.id neq currentMatchId) and
                    (Matches.status eq MatchStatus.COMPLETED) and
                    (
                        ((Matches.homeTeamId eq teamA) and (Matches.awayTeamId eq teamB)) or
                            ((Matches.homeTeamId eq teamB) and (Matches.awayTeamId eq teamA))
                    )
            }
            .orderBy(Matches.playedAt to SortOrder.DESC_NULLS_LAST)
            .firstOrNull()
            ?.let {
                PreviewPriorMeetingResponse(
                    matchId = it[Matches.id].toString(),
                    homeTeam = it[homeTeam[Teams.name]],
                    awayTeam = it[awayTeam[Teams.name]],
                    homeScore = it[Matches.homeScore]?.toInt(),
                    awayScore = it[Matches.awayScore]?.toInt(),
                    playedAt = it[Matches.playedAt]?.toString(),
                    round = it[Matches.round],
                )
            }

    private data class PairStat(
        var homeWins: Int,
        var awayWins: Int,
        var meetings: Int,
        var lastPlayedAt: OffsetDateTime?,
        /** Each duel result from the home-roster player's perspective, newest first. */
        val results: MutableList<String> = mutableListOf(),
    )

    /**
     * All-time direct singles records for every cross pair of the two rosters, keyed
     * (homeRosterPlayer, awayRosterPlayer). One query over the roster union. Assumes an open tx.
     */
    private fun crossPairStats(
        homeIds: Set<UUID>,
        awayIds: Set<UUID>,
    ): Map<Pair<UUID, UUID>, PairStat> {
        if (homeIds.isEmpty() || awayIds.isEmpty()) return emptyMap()
        val stats = HashMap<Pair<UUID, UUID>, PairStat>()
        Games
            .select(Games.homePlayer1Id, Games.awayPlayer1Id, Games.result, Games.playedAt)
            .where {
                (Games.gameType eq GameType.SINGLES) and
                    (Games.result neq GameResult.NOT_PLAYED) and
                    (
                        ((Games.homePlayer1Id inList homeIds) and (Games.awayPlayer1Id inList awayIds)) or
                            ((Games.homePlayer1Id inList awayIds) and (Games.awayPlayer1Id inList homeIds))
                    )
            }
            // Newest first, so each PairStat's results list is already in display order.
            .orderBy(Games.playedAt to SortOrder.DESC_NULLS_LAST)
            .forEach { row ->
                val gameHome = row[Games.homePlayer1Id] ?: return@forEach
                val gameAway = row[Games.awayPlayer1Id] ?: return@forEach
                // Orient the pair key to (home-roster player, away-roster player).
                val homeRosterPlayer = if (gameHome in homeIds) gameHome else gameAway
                val awayRosterPlayer = if (gameHome in homeIds) gameAway else gameHome
                val stat = stats.getOrPut(homeRosterPlayer to awayRosterPlayer) { PairStat(0, 0, 0, null) }
                stat.meetings++
                val homeRosterWasGameHome = gameHome == homeRosterPlayer
                val gameHomeWon = row[Games.result] == GameResult.HOME
                val homeRosterWon = homeRosterWasGameHome == gameHomeWon
                if (homeRosterWon) stat.homeWins++ else stat.awayWins++
                stat.results.add(if (homeRosterWon) "W" else "L")
                val at = row[Games.playedAt]
                if (at != null && (stat.lastPlayedAt == null || at.isAfter(stat.lastPlayedAt))) {
                    stat.lastPlayedAt = at
                }
            }
        return stats
    }

    /**
     * Ranks every cross pair by an "interest" score (rivalry history led, then ELO closeness, then
     * combined strength) and returns the best [MAX_MATCHUPS], keeping variety so a single player
     * cannot monopolise the list (appears in at most two shown duels).
     */
    private fun rankMatchups(
        homePlayers: List<PreviewPlayerResponse>,
        awayPlayers: List<PreviewPlayerResponse>,
        pairStats: Map<Pair<UUID, UUID>, PairStat>,
    ): List<PreviewMatchupResponse> {
        data class Scored(val matchup: PreviewMatchupResponse, val score: Double)

        val scored = ArrayList<Scored>()
        for (hp in homePlayers) {
            for (ap in awayPlayers) {
                val stat = pairStats[UUID.fromString(hp.id) to UUID.fromString(ap.id)]
                val meetings = stat?.meetings ?: 0
                val score = interestScore(hp, ap, meetings)
                if (score <= 0.0) continue
                val prob =
                    if (hp.elo != null && ap.elo != null) {
                        EloCalculationService.winProbability(hp.elo, ap.elo)
                    } else {
                        null
                    }
                scored +=
                    Scored(
                        PreviewMatchupResponse(
                            homePlayer = hp,
                            awayPlayer = ap,
                            homeWins = stat?.homeWins ?: 0,
                            awayWins = stat?.awayWins ?: 0,
                            meetings = meetings,
                            lastPlayedAt = stat?.lastPlayedAt?.toString(),
                            homeWinProbability = prob,
                            results = stat?.results.orEmpty(),
                        ),
                        score,
                    )
            }
        }
        scored.sortByDescending { it.score }

        val homeUse = HashMap<String, Int>()
        val awayUse = HashMap<String, Int>()
        val picked = ArrayList<PreviewMatchupResponse>()
        for (s in scored) {
            if (picked.size >= MAX_MATCHUPS) break
            val h = s.matchup.homePlayer.id
            val a = s.matchup.awayPlayer.id
            if ((homeUse[h] ?: 0) >= 2 || (awayUse[a] ?: 0) >= 2) continue
            picked += s.matchup
            homeUse[h] = (homeUse[h] ?: 0) + 1
            awayUse[a] = (awayUse[a] ?: 0) + 1
        }
        return picked
    }

    /** Interest score for a projected duel — rivalry-led, with ELO (or classification) closeness/strength. */
    private fun interestScore(
        hp: PreviewPlayerResponse,
        ap: PreviewPlayerResponse,
        meetings: Int,
    ): Double {
        val rivalry = minOf(meetings, RIVALRY_CAP).toDouble()
        var closeness = 0.0
        var strength = 0.0
        if (hp.elo != null && ap.elo != null) {
            closeness = (1.0 - abs(hp.elo - ap.elo) / 200.0).coerceAtLeast(0.0)
            strength = (((hp.elo + ap.elo) / 2.0) - 1000.0).coerceIn(0.0, 1000.0) / 1000.0
        } else {
            val rh = classRank(hp.classification)
            val ra = classRank(ap.classification)
            if (rh > 0 && ra > 0) {
                closeness = (1.0 - abs(rh - ra) / 6.0).coerceAtLeast(0.0)
                strength = ((rh + ra) / 2.0).coerceIn(0.0, 22.0) / 22.0
            }
        }
        return 3.0 * rivalry + 2.5 * closeness + 1.5 * strength
    }

    /** Global ladder rank of a class label ("D1"→1 … "A22"→22); 0 when unknown. */
    private fun classRank(cls: String?): Int = cls?.drop(1)?.toIntOrNull() ?: 0

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
