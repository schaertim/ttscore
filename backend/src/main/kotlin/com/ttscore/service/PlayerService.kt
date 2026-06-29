package com.ttscore.service

import com.ttscore.database.*
import com.ttscore.model.*
import com.ttscore.scraper.clicktt.model.ClickTTClubMember
import com.ttscore.util.accentFold
import com.ttscore.util.clickTtNameToDb
import com.ttscore.util.toUuidOrNull
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import java.time.OffsetDateTime
import java.util.*
import kotlin.math.abs

object PlayerService {
    suspend fun getById(playerId: String): PlayerResponse? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            val playerRow =
                Players.select(Players.id, Players.fullName, Players.licenceNr)
                    .where { Players.id eq uuid }
                    .firstOrNull() ?: return@dbQuery null

            val currentClubName =
                (PlayerSeasons innerJoin Teams innerJoin Clubs innerJoin Seasons)
                    .select(Clubs.name)
                    .where { PlayerSeasons.playerId eq uuid }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .firstOrNull()
                    ?.get(Clubs.name)

            val currentElo =
                PlayerElos
                    .select(PlayerElos.eloValue)
                    .where { (PlayerElos.playerId eq uuid) and (PlayerElos.isProvisional eq false) }
                    .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
                    .firstOrNull()
                    ?.get(PlayerElos.eloValue)

            playerRow.toPlayerResponse(
                currentClubName = currentClubName,
                classification = ClassificationService.currentClasses(listOf(uuid))[uuid],
                currentElo = currentElo,
                liveElo = LiveEloService.liveEloFor(uuid, currentElo),
            )
        }
    }

    suspend fun getEloHistory(playerId: String): List<EloEntryResponse>? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            PlayerElos
                .select(PlayerElos.eloValue, PlayerElos.recordedAt)
                .where { PlayerElos.playerId eq uuid }
                .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
                .toList()
                .distinctBy { it[PlayerElos.recordedAt].toLocalDate() }
                .sortedBy { it[PlayerElos.recordedAt] }
                .map {
                    EloEntryResponse(
                        eloValue = it[PlayerElos.eloValue],
                        recordedAt = it[PlayerElos.recordedAt].toString(),
                    )
                }
        }
    }

    suspend fun getMatchHistory(playerId: String): List<PlayerGameResponse>? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            val homeTeam = Teams.alias("home_team")
            val awayTeam = Teams.alias("away_team")
            val homePlayer = Players.alias("home_player")
            val awayPlayer = Players.alias("away_player")

            Games
                .join(Matches, JoinType.LEFT, Games.matchId, Matches.id)
                .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                .join(homePlayer, JoinType.LEFT, Games.homePlayer1Id, homePlayer[Players.id])
                .join(awayPlayer, JoinType.LEFT, Games.awayPlayer1Id, awayPlayer[Players.id])
                .select(
                    Games.id,
                    Games.homePlayer1Id,
                    Games.awayPlayer1Id,
                    Games.homeSets,
                    Games.awaySets,
                    Games.result,
                    Games.homePlayer1EloDelta,
                    Games.awayPlayer1EloDelta,
                    Games.playedAt,
                    Games.competitionName,
                    Matches.id,
                    Matches.homeScore,
                    Matches.awayScore,
                    Matches.round,
                    Matches.status,
                    homeTeam[Teams.name],
                    awayTeam[Teams.name],
                    homePlayer[Players.fullName],
                    awayPlayer[Players.fullName],
                )
                .where {
                    ((Games.homePlayer1Id eq uuid) or (Games.awayPlayer1Id eq uuid)) and
                        (Games.gameType eq GameType.SINGLES)
                }
                .orderBy(Games.playedAt to SortOrder.DESC)
                .toList()
                .let { rows ->
                    val gameIds = rows.map { it[Games.id] }
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
                    val opponentIds =
                        rows.mapNotNull { row ->
                            val isHome = row[Games.homePlayer1Id] == uuid
                            if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id]
                        }.toSet()

                    // Opponent class as it was on each game's date (not the current class).
                    val seasonNames =
                        rows.mapNotNull { row ->
                            row[Games.playedAt]?.let {
                                ClassificationService.seasonNameOf(ClassificationService.localDateOf(it))
                            }
                        }.toSet()
                    val opponentBadges = ClassificationService.classBadges(opponentIds, seasonNames)

                    // Base ELOs for computing provisional deltas of not-yet-rated games.
                    val baseElos = LiveEloService.baseElos(opponentIds + uuid)
                    val playerBase = baseElos[uuid]

                    rows.map { row ->
                        val isHome = row[Games.homePlayer1Id] == uuid
                        val gameId = row[Games.id]
                        val opponentId = if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id]
                        val gameResult = row[Games.result]
                        val officialDelta =
                            if (isHome) row[Games.homePlayer1EloDelta] else row[Games.awayPlayer1EloDelta]
                        val opponentBase = opponentId?.let { baseElos[it] }
                        // Not officially rated yet, but recent + computable → provisional estimate.
                        val isPending =
                            officialDelta == null &&
                                gameResult != GameResult.NOT_PLAYED &&
                                LiveEloService.isWithinPendingWindow(row[Games.playedAt]) &&
                                playerBase != null && opponentBase != null
                        val eloDelta =
                            officialDelta
                                ?: if (isPending) {
                                    LiveEloService.provisionalDelta(playerBase, opponentBase, isHome, gameResult)
                                } else {
                                    null
                                }
                        PlayerGameResponse(
                            matchId = row.getOrNull(Matches.id)?.toString(),
                            gameId = gameId.toString(),
                            playedAt = row[Games.playedAt]?.toString(),
                            homeTeam = row.getOrNull(homeTeam[Teams.name]),
                            awayTeam = row.getOrNull(awayTeam[Teams.name]),
                            homeScore = row.getOrNull(Matches.homeScore)?.toInt(),
                            awayScore = row.getOrNull(Matches.awayScore)?.toInt(),
                            round = row.getOrNull(Matches.round),
                            status = row.getOrNull(Matches.status),
                            competitionName = row[Games.competitionName],
                            playerSide = if (isHome) "home" else "away",
                            opponentId = opponentId?.toString(),
                            opponentName =
                                if (isHome) {
                                    row[awayPlayer[Players.fullName]]
                                } else {
                                    row[homePlayer[Players.fullName]]
                                },
                            opponentClassification =
                                row[Games.playedAt]?.let { playedAt ->
                                    opponentId?.let {
                                        ClassificationService.classOf(
                                            opponentBadges,
                                            it,
                                            ClassificationService.localDateOf(playedAt),
                                        )
                                    }
                                },
                            homeSets = row[Games.homeSets]?.toInt(),
                            awaySets = row[Games.awaySets]?.toInt(),
                            result = gameResult,
                            eloDelta = eloDelta,
                            eloDeltaProvisional = isPending,
                            sets =
                                setsByGame[gameId]?.map { s ->
                                    SetResponse(
                                        setNumber = s[GameSets.setNumber].toInt(),
                                        homePoints = s[GameSets.homePoints].toInt(),
                                        awayPoints = s[GameSets.awayPoints].toInt(),
                                    )
                                } ?: emptyList(),
                        )
                    }
                }
        }
    }

    /** Mutable wins/games accumulator used while folding the season's games. */
    private class WinAcc {
        var wins = 0
        var games = 0

        fun add(won: Boolean) {
            games++
            if (won) wins++
        }

        fun toResponse() = WinRateResponse(wins, games)
    }

    /** Global strength rank of a class — the numeric suffix is monotonic across the ladder (A22..D1). */
    private fun classRank(className: String?): Int? = className?.drop(1)?.toIntOrNull()

    /** Mutable per-competition accumulator. */
    private class CompAcc {
        var wins = 0
        var games = 0
        var tournament = false
    }

    private fun emptyStats(seasonName: String) =
        PlayerSeasonStatsResponse(
            seasonName = seasonName,
            totalGames = 0,
            overall = WinRateResponse(0, 0),
            recentForm = emptyList(),
            opponentBuckets = emptyList(),
            setDistribution = emptyList(),
            setsWon = 0,
            setsLost = 0,
            deuceSetsWon = 0,
            deuceSetsTotal = 0,
            tightGameWins = 0,
            tightGames = 0,
            comebackWins = 0,
            comeFromBehindGames = 0,
            comeFromBehindWins = 0,
            monthly = emptyList(),
            longestWinStreak = 0,
            currentWinStreak = 0,
            bestWinOpponentName = null,
            bestWinOpponentClass = null,
            competitions = emptyList(),
        )

    suspend fun getSeasonStats(playerId: String): PlayerSeasonStatsResponse? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            Players.select(Players.id).where { Players.id eq uuid }.firstOrNull() ?: return@dbQuery null

            val currentSeason =
                Seasons.select(Seasons.id, Seasons.name)
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull() ?: return@dbQuery emptyStats("")
            val currentSeasonId = currentSeason[Seasons.id]
            val seasonName = currentSeason[Seasons.name]

            val homePlayer = Players.alias("home_player")
            val awayPlayer = Players.alias("away_player")

            val rows =
                Games
                    .join(Matches, JoinType.LEFT, Games.matchId, Matches.id)
                    .join(Groups, JoinType.LEFT, Matches.groupId, Groups.id)
                    .join(homePlayer, JoinType.LEFT, Games.homePlayer1Id, homePlayer[Players.id])
                    .join(awayPlayer, JoinType.LEFT, Games.awayPlayer1Id, awayPlayer[Players.id])
                    .select(
                        Games.id,
                        Games.homePlayer1Id,
                        Games.awayPlayer1Id,
                        Games.homeSets,
                        Games.awaySets,
                        Games.result,
                        Games.playedAt,
                        Games.competitionName,
                        Games.matchId,
                        Groups.name,
                        homePlayer[Players.fullName],
                        awayPlayer[Players.fullName],
                    )
                    .where {
                        ((Games.homePlayer1Id eq uuid) or (Games.awayPlayer1Id eq uuid)) and
                            (Games.gameType eq GameType.SINGLES) and
                            ((Groups.seasonId eq currentSeasonId) or Games.matchId.isNull())
                    }
                    .orderBy(Games.playedAt to SortOrder.ASC_NULLS_LAST)
                    .toList()

            if (rows.isEmpty()) return@dbQuery emptyStats(seasonName)

            val gameIds = rows.map { it[Games.id] }
            val setsByGame =
                GameSets
                    .select(GameSets.gameId, GameSets.setNumber, GameSets.homePoints, GameSets.awayPoints)
                    .where { GameSets.gameId inList gameIds }
                    .orderBy(GameSets.setNumber to SortOrder.ASC)
                    .groupBy { it[GameSets.gameId] }

            val opponentIds =
                rows.mapNotNull { row ->
                    val isHome = row[Games.homePlayer1Id] == uuid
                    if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id]
                }.toSet()
            val seasonNames =
                rows.mapNotNull { row ->
                    row[Games.playedAt]?.let {
                        ClassificationService.seasonNameOf(ClassificationService.localDateOf(it))
                    }
                }.toSet()
            val badges = ClassificationService.classBadges(opponentIds + uuid, seasonNames)

            val playerCurrentElo = PlayerElos
                .select(PlayerElos.eloValue)
                .where { (PlayerElos.playerId eq uuid) and (PlayerElos.isProvisional eq false) }
                .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
                .firstOrNull()?.get(PlayerElos.eloValue)
            val playerLiveElo = LiveEloService.liveEloFor(uuid, playerCurrentElo)
            val playerCurrentClass = (playerLiveElo ?: playerCurrentElo)
                ?.let { ClassificationService.fromElo(it) }
                ?: ClassificationService.currentClasses(listOf(uuid))[uuid]
            val playerCurrentRank = classRank(playerCurrentClass)

            val overall = WinAcc()
            val afterLoss1 = WinAcc()
            val setDist = linkedMapOf<Pair<Int, Int>, Int>()
            val tiers = linkedMapOf<String, WinAcc>()
            val monthly = linkedMapOf<String, IntArray>() // [wins, losses]
            val comps = linkedMapOf<String, CompAcc>()
            var setsWon = 0
            var setsLost = 0
            var deuceWon = 0
            var deuceTotal = 0
            var tightWins = 0
            var tightGames = 0
            var comeFromBehindWins = 0
            var comeFromBehindGames = 0
            var bestRank: Int? = null
            var bestName: String? = null
            var bestClass: String? = null
            val decidedResults = mutableListOf<Boolean>()

            for (row in rows) {
                val isHome = row[Games.homePlayer1Id] == uuid
                if (row[Games.result] == GameResult.NOT_PLAYED) continue

                val won =
                    (row[Games.result] == GameResult.HOME && isHome) ||
                        (row[Games.result] == GameResult.AWAY && !isHome)
                val isLeague = row[Games.matchId] != null
                val playedAt = row[Games.playedAt]

                overall.add(won)
                decidedResults.add(won)

                // opponent class (at game date) → per-class tally + best win
                if (playedAt != null) {
                    val date = ClassificationService.localDateOf(playedAt)
                    val oppId = if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id]
                    val oppClass = oppId?.let { ClassificationService.classOf(badges, it, date) }
                    val oppRank = classRank(oppClass)
                    if (oppClass != null) tiers.getOrPut(oppClass) { WinAcc() }.add(won)
                    if (won && oppRank != null && (bestRank == null || oppRank > bestRank!!)) {
                        bestRank = oppRank
                        bestName =
                            if (isHome) {
                                row.getOrNull(awayPlayer[Players.fullName])
                            } else {
                                row.getOrNull(homePlayer[Players.fullName])
                            }
                        bestClass = oppClass
                    }
                    val mk = "%04d-%02d".format(date.year, date.monthValue)
                    val m = monthly.getOrPut(mk) { IntArray(2) }
                    if (won) m[0]++ else m[1]++
                }

                // set-score margin / tight (single-set-margin) game
                val ps = if (isHome) row[Games.homeSets]?.toInt() else row[Games.awaySets]?.toInt()
                val os = if (isHome) row[Games.awaySets]?.toInt() else row[Games.homeSets]?.toInt()
                if (ps != null && os != null) {
                    setDist[ps to os] = (setDist[ps to os] ?: 0) + 1
                    if (abs(ps - os) == 1) {
                        tightGames++
                        if (won) tightWins++
                    }
                }

                // point-by-point: first-set comebacks, set win rate, deuce-set nerve
                val gameSets = setsByGame[row[Games.id]].orEmpty()
                if (gameSets.isNotEmpty()) {
                    val first = gameSets.first()
                    val firstWon =
                        if (isHome) {
                            first[GameSets.homePoints] > first[GameSets.awayPoints]
                        } else {
                            first[GameSets.awayPoints] > first[GameSets.homePoints]
                        }
                    if (!firstWon) afterLoss1.add(won)
                    var playerSetsRunning = 0; var oppSetsRunning = 0; var wasBehind = false
                    for (s in gameSets) {
                        val homeP = s[GameSets.homePoints].toInt()
                        val awayP = s[GameSets.awayPoints].toInt()
                        val my = if (isHome) homeP else awayP
                        val op = if (isHome) awayP else homeP
                        if (my > op) { setsWon++; playerSetsRunning++ } else { setsLost++; oppSetsRunning++ }
                        if (homeP >= 10 && awayP >= 10) { deuceTotal++; if (my > op) deuceWon++ }
                        if (oppSetsRunning > playerSetsRunning) wasBehind = true
                    }
                    if (wasBehind) { comeFromBehindGames++; if (won) comeFromBehindWins++ }
                }

                // per-competition: league games key on division name, tournaments on competition name
                val compKey = row[Games.competitionName] ?: row.getOrNull(Groups.name) ?: "—"
                val c = comps.getOrPut(compKey) { CompAcc() }
                c.games++
                if (won) c.wins++
                if (!isLeague) c.tournament = true
            }

            var longestStreak = 0
            var run = 0
            for (won in decidedResults) {
                run = if (won) run + 1 else 0
                if (run > longestStreak) longestStreak = run
            }
            var currentStreak = 0
            for (won in decidedResults.asReversed()) {
                if (won) currentStreak++ else break
            }

            // Buckets relative to the player's current class: keep classes within ±2 separate,
            // fold the far-stronger into "HIGHER" and the far-weaker into "LOWER".
            val opponentBuckets: List<OpponentBucketResponse> =
                if (playerCurrentRank == null) {
                    tiers.entries
                        .sortedByDescending { classRank(it.key) ?: Int.MIN_VALUE }
                        .map { OpponentBucketResponse(it.key, it.value.wins, it.value.games) }
                } else {
                    var higherW = 0; var higherG = 0
                    var higherNearRank = Int.MIN_VALUE; var higherNearCls = ""
                    var higherFarRank = Int.MIN_VALUE; var higherFarCls = ""
                    var lowerW = 0; var lowerG = 0
                    var lowerNearRank = Int.MAX_VALUE; var lowerNearCls = ""
                    var lowerFarRank = Int.MAX_VALUE; var lowerFarCls = ""
                    val mid = mutableListOf<Triple<Int, String, WinAcc>>()
                    for ((cls, acc) in tiers) {
                        val r = classRank(cls) ?: continue
                        when {
                            r - playerCurrentRank >= 3 -> {
                                higherW += acc.wins; higherG += acc.games
                                // nearClass = lowest rank in HIGHER (closest to player boundary)
                                if (r < higherNearRank || higherNearRank == Int.MIN_VALUE) { higherNearRank = r; higherNearCls = cls }
                                // farClass = highest rank in HIGHER (strongest opponent)
                                if (r > higherFarRank) { higherFarRank = r; higherFarCls = cls }
                            }
                            r - playerCurrentRank <= -3 -> {
                                lowerW += acc.wins; lowerG += acc.games
                                // nearClass = highest rank in LOWER (closest to player boundary)
                                if (r > lowerNearRank || lowerNearRank == Int.MAX_VALUE) { lowerNearRank = r; lowerNearCls = cls }
                                // farClass = lowest rank in LOWER (weakest opponent)
                                if (r < lowerFarRank) { lowerFarRank = r; lowerFarCls = cls }
                            }
                            else -> mid.add(Triple(r, cls, acc))
                        }
                    }
                    buildList {
                        if (higherG > 0) add(
                            OpponentBucketResponse("HIGHER", higherW, higherG, higherNearCls.ifEmpty { null }, higherFarCls.ifEmpty { null })
                        )
                        mid.sortedByDescending { it.first }
                            .forEach { add(OpponentBucketResponse(it.second, it.third.wins, it.third.games)) }
                        if (lowerG > 0) add(
                            OpponentBucketResponse("LOWER", lowerW, lowerG, lowerNearCls.ifEmpty { null }, lowerFarCls.ifEmpty { null })
                        )
                    }
                }

            PlayerSeasonStatsResponse(
                seasonName = seasonName,
                totalGames = overall.games,
                overall = overall.toResponse(),
                recentForm = decidedResults.takeLast(10),
                opponentBuckets = opponentBuckets,
                setDistribution = run {
                    // Wins: most dominant first (4:0, 3:0, 4:1, 3:1 …) → opponentSets ASC, playerSets DESC
                    val wins = setDist.entries.filter { it.key.first > it.key.second }
                        .sortedWith(compareBy({ it.key.second }, { -it.key.first }))
                    // Losses: closest first (3:4, 2:3 …) → playerSets DESC, opponentSets ASC
                    val losses = setDist.entries.filter { it.key.first < it.key.second }
                        .sortedWith(compareBy({ -it.key.first }, { it.key.second }))
                    (wins + losses).map { SetScoreBucketResponse(it.key.first, it.key.second, it.value) }
                },
                setsWon = setsWon,
                setsLost = setsLost,
                deuceSetsWon = deuceWon,
                deuceSetsTotal = deuceTotal,
                tightGameWins = tightWins,
                tightGames = tightGames,
                comebackWins = afterLoss1.wins,
                comeFromBehindGames = comeFromBehindGames,
                comeFromBehindWins = comeFromBehindWins,
                monthly =
                    monthly.entries
                        .sortedBy { it.key }
                        .map { MonthlyFormResponse(it.key, it.value[0], it.value[1]) },
                longestWinStreak = longestStreak,
                currentWinStreak = currentStreak,
                bestWinOpponentName = bestName,
                bestWinOpponentClass = bestClass,
                competitions =
                    comps.entries
                        .sortedByDescending { it.value.games }
                        .map { CompetitionStatResponse(it.key, it.value.wins, it.value.games, it.value.tournament) },
            )
        }
    }

    /**
     * Head-to-head comparison between two players. Refs (live class/ELO) and season stats are
     * computed per player via [getById] / [getSeasonStats]; the direct record is all-time over
     * decided singles encounters. Returns null if either player id is invalid or missing.
     */
    suspend fun getHeadToHead(playerId: String, opponentId: String): HeadToHeadResponse? {
        val uuidA = playerId.toUuidOrNull() ?: return null
        val uuidB = opponentId.toUuidOrNull() ?: return null
        if (uuidA == uuidB) return null

        val playerA = getById(playerId) ?: return null
        val playerB = getById(opponentId) ?: return null
        val statsA = getSeasonStats(playerId) ?: return null
        val statsB = getSeasonStats(opponentId) ?: return null

        return dbQuery {
            val rows =
                Games
                    .select(
                        Games.id,
                        Games.matchId,
                        Games.homePlayer1Id,
                        Games.awayPlayer1Id,
                        Games.homeSets,
                        Games.awaySets,
                        Games.result,
                        Games.playedAt,
                        Games.competitionName,
                    )
                    .where {
                        (Games.gameType eq GameType.SINGLES) and
                            (Games.result neq GameResult.NOT_PLAYED) and
                            (
                                ((Games.homePlayer1Id eq uuidA) and (Games.awayPlayer1Id eq uuidB)) or
                                    ((Games.homePlayer1Id eq uuidB) and (Games.awayPlayer1Id eq uuidA))
                            )
                    }
                    .orderBy(Games.playedAt to SortOrder.DESC_NULLS_LAST)
                    .toList()

            val setsByGame =
                if (rows.isEmpty()) {
                    emptyMap()
                } else {
                    GameSets
                        .select(GameSets.gameId, GameSets.setNumber, GameSets.homePoints, GameSets.awayPoints)
                        .where { GameSets.gameId inList rows.map { it[Games.id] } }
                        .orderBy(GameSets.setNumber to SortOrder.ASC)
                        .groupBy { it[GameSets.gameId] }
                }

            var aWins = 0
            var bWins = 0
            val games =
                rows.map { row ->
                    val homeIsA = row[Games.homePlayer1Id] == uuidA
                    val aWon = (row[Games.result] == GameResult.HOME) == homeIsA
                    if (aWon) aWins++ else bWins++

                    val aSets = (if (homeIsA) row[Games.homeSets] else row[Games.awaySets])?.toInt()
                    val bSets = (if (homeIsA) row[Games.awaySets] else row[Games.homeSets])?.toInt()

                    // Orient each set to A's perspective (homePoints = A's points).
                    val sets =
                        setsByGame[row[Games.id]].orEmpty().map { s ->
                            val homeP = s[GameSets.homePoints].toInt()
                            val awayP = s[GameSets.awayPoints].toInt()
                            SetResponse(
                                setNumber = s[GameSets.setNumber].toInt(),
                                homePoints = if (homeIsA) homeP else awayP,
                                awayPoints = if (homeIsA) awayP else homeP,
                            )
                        }

                    H2HGameResponse(
                        gameId = row[Games.id].toString(),
                        matchId = row[Games.matchId]?.toString(),
                        playedAt = row[Games.playedAt]?.toString(),
                        competitionName = row[Games.competitionName],
                        aSets = aSets,
                        bSets = bSets,
                        aWon = aWon,
                        sets = sets,
                    )
                }

            HeadToHeadResponse(
                playerA = playerA,
                playerB = playerB,
                statsA = statsA,
                statsB = statsB,
                record = H2HRecordResponse(aWins, bWins, games.size),
                games = games,
            )
        }
    }

    suspend fun getClassHistory(playerId: String): List<ClassHistoryEntryResponse>? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            (PlayerClassifications innerJoin Seasons)
                .select(Seasons.name, PlayerClassifications.firstHalfClass, PlayerClassifications.secondHalfClass)
                .where { PlayerClassifications.playerId eq uuid }
                .orderBy(Seasons.name to SortOrder.DESC)
                .limit(5)
                .mapNotNull { row ->
                    // One entry per season — the latest known half (second falls back to first).
                    val classification =
                        row[PlayerClassifications.secondHalfClass]
                            ?: row[PlayerClassifications.firstHalfClass]
                            ?: return@mapNotNull null
                    ClassHistoryEntryResponse(
                        classification = classification,
                        seasonName = row[Seasons.name],
                    )
                }
        }
    }

    suspend fun getPlayerLeagueContext(playerId: String): LeagueContextResponse? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            val teamRow =
                PlayerSeasons
                    .join(Teams, JoinType.INNER, PlayerSeasons.teamId, Teams.id)
                    .join(Groups, JoinType.INNER, Teams.groupId, Groups.id)
                    .join(Seasons, JoinType.INNER, PlayerSeasons.seasonId, Seasons.id)
                    .select(Teams.id, Teams.name, Groups.id, Groups.name)
                    .where { PlayerSeasons.playerId eq uuid }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .firstOrNull() ?: return@dbQuery null

            val teamId = teamRow[Teams.id]
            val teamName = teamRow[Teams.name]
            val groupId = teamRow[Groups.id]
            val groupName = teamRow[Groups.name]

            val standing =
                Standings
                    .select(Standings.position, Standings.won, Standings.drawn, Standings.lost)
                    .where { (Standings.teamId eq teamId) and (Standings.groupId eq groupId) }
                    .firstOrNull()

            val scheduledCount =
                Matches
                    .select(Matches.id)
                    .where {
                        ((Matches.homeTeamId eq teamId) or (Matches.awayTeamId eq teamId)) and
                            (Matches.status eq MatchStatus.SCHEDULED)
                    }
                    .count()
                    .toInt()

            LeagueContextResponse(
                teamId = teamId.toString(),
                teamName = teamName,
                groupId = groupId.toString(),
                groupName = groupName,
                position = standing?.get(Standings.position)?.toInt() ?: 0,
                won = standing?.get(Standings.won)?.toInt() ?: 0,
                drawn = standing?.get(Standings.drawn)?.toInt() ?: 0,
                lost = standing?.get(Standings.lost)?.toInt() ?: 0,
                scheduledMatchCount = scheduledCount,
            )
        }
    }

    suspend fun getPlayerNextMatch(playerId: String): NextMatchResponse? {
        val uuid = playerId.toUuidOrNull() ?: return null
        return dbQuery {
            val teamRow =
                PlayerSeasons
                    .join(Teams, JoinType.INNER, PlayerSeasons.teamId, Teams.id)
                    .join(Groups, JoinType.INNER, Teams.groupId, Groups.id)
                    .join(Seasons, JoinType.INNER, PlayerSeasons.seasonId, Seasons.id)
                    .select(Teams.id, Teams.name, Groups.id, Groups.name)
                    .where { PlayerSeasons.playerId eq uuid }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .firstOrNull() ?: return@dbQuery null

            val teamId = teamRow[Teams.id]
            val teamName = teamRow[Teams.name]
            val groupId = teamRow[Groups.id]
            val groupName = teamRow[Groups.name]

            val homeTeamAlias = Teams.alias("ht")
            val awayTeamAlias = Teams.alias("at")

            Matches
                .join(homeTeamAlias, JoinType.INNER, Matches.homeTeamId, homeTeamAlias[Teams.id])
                .join(awayTeamAlias, JoinType.INNER, Matches.awayTeamId, awayTeamAlias[Teams.id])
                .select(
                    Matches.id,
                    Matches.homeTeamId,
                    Matches.awayTeamId,
                    Matches.round,
                    Matches.playedAt,
                    homeTeamAlias[Teams.name],
                    awayTeamAlias[Teams.name],
                )
                .where {
                    ((Matches.homeTeamId eq teamId) or (Matches.awayTeamId eq teamId)) and
                        (Matches.status eq MatchStatus.SCHEDULED)
                }
                .orderBy(Matches.playedAt to SortOrder.ASC_NULLS_LAST)
                .firstOrNull()
                ?.let { row ->
                    NextMatchResponse(
                        matchId = row[Matches.id].toString(),
                        homeTeam = row[homeTeamAlias[Teams.name]],
                        awayTeam = row[awayTeamAlias[Teams.name]],
                        playerTeamId = teamId.toString(),
                        playerTeamName = teamName,
                        playedAt = row[Matches.playedAt]?.toString(),
                        round = row[Matches.round],
                        groupId = groupId.toString(),
                        groupName = groupName,
                    )
                }
        }
    }

    suspend fun search(
        name: String,
        page: Int,
        size: Int,
    ): PagedResponse<PlayerResponse>? {
        if (name.length < 3) return null

        return dbQuery {
            val tokens = name.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

            // DB stores "Lastname Firstname". Match every token independently (AND) so that both
            // "Tim Schär" and "Schär Tim" find the same player — regardless of the order typed and
            // regardless of whether each token is a full word or a partial prefix ("tim s" → finds
            // "Schär Tim" because "schär tim" contains both "tim" and "s" somewhere in the string).
            val nameCondition: Op<Boolean> =
                tokens
                    .map<String, Op<Boolean>> { Players.fullName.lowerCase() like LikePattern("%$it%") }
                    .reduce { acc, op -> acc and op }

            val total =
                Players.select(Players.id)
                    .where { nameCondition }
                    .count()

            // Step 1 — fetch paged core player rows
            val basePlayers =
                Players.select(Players.id, Players.fullName, Players.licenceNr)
                    .where { nameCondition }
                    .orderBy(Players.fullName to SortOrder.ASC)
                    .limit(size).offset(start = (page * size).toLong())
                    .toList()

            val playerIds = basePlayers.map { it[Players.id] }

            if (playerIds.isEmpty()) {
                return@dbQuery PagedResponse(emptyList(), page, size, total)
            }

            // Step 2 — current club (most recent season) and current class for the found players
            val clubByPlayer =
                (PlayerSeasons innerJoin Teams innerJoin Clubs innerJoin Seasons)
                    .select(PlayerSeasons.playerId, Clubs.name)
                    .where { PlayerSeasons.playerId inList playerIds }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .toList()
                    .groupBy { it[PlayerSeasons.playerId] }
                    .mapValues { it.value.first()[Clubs.name] }

            val classByPlayer = ClassificationService.currentClasses(playerIds)

            // Step 3 — merge results
            val items =
                basePlayers.map { row ->
                    val id = row[Players.id]
                    row.toPlayerResponse(
                        currentClubName = clubByPlayer[id],
                        classification = classByPlayer[id],
                    )
                }

            PagedResponse(items = items, page = page, size = size, total = total)
        }
    }

    suspend fun getAllLicensedPlayers(): List<Pair<UUID, String>> =
        dbQuery {
            Players.select(Players.id, Players.licenceNr)
                .where { Players.licenceNr.isNotNull() }
                .map { it[Players.id] to it[Players.licenceNr]!! }
        }

    suspend fun getAllPlayersWithClickTtId(): List<Pair<UUID, Int>> =
        dbQuery {
            Players.select(Players.id, Players.clickttId)
                .where { Players.clickttId.isNotNull() }
                .map { it[Players.id] to it[Players.clickttId]!! }
        }

    data class EloHistoryEntry(
        val eloValue: Int,
        val recordedAt: OffsetDateTime,
        val isProvisional: Boolean,
    )

    suspend fun replaceEloHistory(
        playerId: UUID,
        entries: List<EloHistoryEntry>,
    ) = dbQuery {
        PlayerElos.deleteWhere { PlayerElos.playerId eq playerId }
        if (entries.isNotEmpty()) {
            PlayerElos.batchInsert(entries) { e ->
                this[PlayerElos.playerId] = playerId
                this[PlayerElos.seasonId] = null
                this[PlayerElos.eloValue] = e.eloValue
                this[PlayerElos.recordedAt] = e.recordedAt
                this[PlayerElos.isProvisional] = e.isProvisional
            }
        }
    }

    /**
     * Resolves a click-tt name ("Lastname, Firstname") to a single player id, or null when it cannot
     * be resolved *uniquely*. Critically, this returns null on a name collision (e.g. two different
     * players both named "Hess Matthias") rather than picking an arbitrary namesake — callers use the
     * result to link games and classifications, so guessing silently corrupts the wrong player's data.
     * Prefer an id-based lookup ([findPlayerIdByClickTtId]) whenever a person/knob id is available.
     */
    suspend fun findPlayerIdByName(clickTtName: String): UUID? {
        val dbName = clickTtNameToDb(clickTtName) // "Lastname Firstname"

        return dbQuery {
            // Fast path: exact case-insensitive SQL match. Resolve only when unique; a collision is
            // ambiguous, so refuse rather than fall through to a fuzzier (even more collision-prone) match.
            val exact =
                Players.select(Players.id)
                    .where { Players.fullName.lowerCase() eq dbName.lowercase() }
                    .limit(2)
                    .map { it[Players.id] }
            if (exact.size == 1) return@dbQuery exact.single()
            if (exact.size >= 2) return@dbQuery null

            // Accent-fold fallback: "Grégory" finds "Gregory". Also unique-or-nothing.
            val folded = accentFold(dbName)
            val foldedMatches =
                Players.select(Players.id, Players.fullName)
                    .toList()
                    .filter { accentFold(it[Players.fullName]) == folded }
                    .map { it[Players.id] }
            if (foldedMatches.size == 1) foldedMatches.single() else null
        }
    }

    suspend fun getLicenceNrById(playerId: UUID): String? =
        dbQuery {
            Players.select(Players.licenceNr)
                .where { Players.id eq playerId }
                .map { it[Players.licenceNr] }
                .firstOrNull()
        }

    suspend fun getPlayersMissingClickTtId(): List<Pair<UUID, String>> =
        dbQuery {
            Players.select(Players.id, Players.licenceNr)
                .where {
                    Players.clickttId.isNull() and Players.licenceNr.isNotNull()
                }
                .map { it[Players.id] to it[Players.licenceNr]!! }
        }

    suspend fun updateClickTtIdsBatch(mappings: Map<String, Int>) {
        if (mappings.isEmpty()) return
        dbQuery {
            for ((licence, personId) in mappings) {
                Players.update({ Players.licenceNr eq licence }) {
                    it[Players.clickttId] = personId
                }
            }
        }
    }

    /**
     * Phase A of the backfill: for each club member whose licence already exists in the DB,
     * writes the click-tt person ID, canonical name, and registration metadata.
     */
    suspend fun updateClickTtDataBatch(members: List<ClickTTClubMember>) {
        if (members.isEmpty()) return
        dbQuery {
            for (member in members) {
                Players.update({ Players.licenceNr eq member.licence }) {
                    it[Players.clickttId] = member.personId
                    // Convert "Lastname, Firstname" → "Lastname Firstname" to match knob storage format
                    it[Players.fullName] = clickTtNameToDb(member.fullName)
                    it[Players.sex] = member.sex
                    it[Players.serie] = member.serie
                    it[Players.nationality] = member.nationality
                }
            }
        }
    }

    /**
     * Returns the subset of the given licence numbers that already exist in the DB.
     * Used to identify which club members couldn't be matched by licence so name+club
     * fallback matching can be attempted for the remainder.
     */
    suspend fun findLicencesInDb(licences: Collection<String>): Set<String> =
        dbQuery {
            Players.select(Players.licenceNr)
                .where { Players.licenceNr inList licences.toList() }
                .mapNotNull { it[Players.licenceNr] }
                .toSet()
        }

    /**
     * Phase C of the backfill: inserts player rows for members that couldn't be matched by
     * licence or name+club. Uses INSERT IGNORE so members already linked by Phase B
     * (whose clicktt_id now lives on an existing row) are silently skipped.
     */
    suspend fun insertUnmatchedClickTtMembers(members: List<ClickTTClubMember>) {
        if (members.isEmpty()) return
        dbQuery {
            for (member in members) {
                Players.insertIgnore {
                    it[Players.clickttId] = member.personId
                    it[Players.licenceNr] = member.licence
                    it[Players.fullName] = clickTtNameToDb(member.fullName)
                    it[Players.sex] = member.sex
                    it[Players.serie] = member.serie
                    it[Players.nationality] = member.nationality
                }
            }
        }
    }

    /**
     * Phase B of the backfill: for club members that couldn't be matched by licence,
     * attempts a name + club fallback — accent-folds both sides and checks that the player
     * has a player_season record at a club whose name fuzzy-matches [clickTtClubName].
     * On a unique match the player gains the licence, click-tt ID, canonical name, and metadata.
     */
    suspend fun matchAndLinkByNameAndClub(
        members: List<ClickTTClubMember>,
        clickTtClubName: String,
    ) = dbQuery {
        val foldedClub = accentFold(clickTtClubName)

        // Find DB clubs whose folded name overlaps with the click-tt club name
        val matchingClubIds =
            Clubs.select(Clubs.id, Clubs.name)
                .toList()
                .filter { row ->
                    val fc = accentFold(row[Clubs.name])
                    fc.contains(foldedClub) || foldedClub.contains(fc)
                }
                .map { it[Clubs.id] }

        if (matchingClubIds.isEmpty()) return@dbQuery

        // Load players at those clubs that still have no licence and no click-tt ID
        val playersAtClub =
            (Players innerJoin PlayerSeasons innerJoin Teams innerJoin Clubs)
                .select(Players.id, Players.fullName)
                .where {
                    (Clubs.id inList matchingClubIds) and
                        Players.licenceNr.isNull() and
                        Players.clickttId.isNull()
                }
                .distinctBy { it[Players.id] }

        // Index by folded name for O(1) lookup
        val byFolded = playersAtClub.groupBy { accentFold(it[Players.fullName]) }

        for (member in members) {
            val dbName = clickTtNameToDb(member.fullName)
            val candidates = byFolded[accentFold(dbName)] ?: continue

            if (candidates.size == 1) {
                Players.update({ Players.id eq candidates[0][Players.id] }) {
                    it[Players.licenceNr] = member.licence
                    it[Players.clickttId] = member.personId
                    it[Players.fullName] = dbName
                    it[Players.sex] = member.sex
                    it[Players.serie] = member.serie
                    it[Players.nationality] = member.nationality
                }
            }
            // Multiple candidates with same folded name at same club → ambiguous, skip
        }
    }

    /**
     * Finds the club that the majority of the given licensed players belong to.
     * Used to match a click-tt club ID to an existing club row scraped from knob.
     */
    suspend fun findClubIdByLicences(licences: List<String>): UUID? {
        if (licences.isEmpty()) return null
        return dbQuery {
            val clubCount = Clubs.id.count()
            Players
                .innerJoin(PlayerSeasons, { Players.id }, { PlayerSeasons.playerId })
                .innerJoin(Teams, { PlayerSeasons.teamId }, { Teams.id })
                .innerJoin(Clubs, { Teams.clubId }, { Clubs.id })
                .select(Clubs.id, clubCount)
                .where { Players.licenceNr inList licences }
                .groupBy(Clubs.id)
                .orderBy(clubCount to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(Clubs.id)
        }
    }

    suspend fun getClickTtIdById(playerId: UUID): Int? =
        dbQuery {
            Players.select(Players.clickttId)
                .where { Players.id eq playerId }
                .map { it[Players.clickttId] }
                .firstOrNull()
        }

    suspend fun findPlayerIdByClickTtId(clickttId: Int): UUID? =
        dbQuery {
            Players.select(Players.id)
                .where { Players.clickttId eq clickttId }
                .firstOrNull()?.get(Players.id)
        }

    private fun ResultRow.toPlayerResponse(
        currentClubName: String? = null,
        classification: String? = null,
        currentElo: Int? = null,
        liveElo: Int? = null,
        isSyncing: Boolean = false,
    ) = PlayerResponse(
        id = this[Players.id].toString(),
        fullName = this[Players.fullName],
        licenceNr = this[Players.licenceNr],
        currentClubName = currentClubName,
        classification = classification,
        // Live class reflects the up-to-date ELO when we have it, else the official one.
        liveClassification = (liveElo ?: currentElo)?.let { ClassificationService.fromElo(it) },
        currentElo = currentElo,
        liveElo = liveElo,
        isSyncing = isSyncing,
    )
}
