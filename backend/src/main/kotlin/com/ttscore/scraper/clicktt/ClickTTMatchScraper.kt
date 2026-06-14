package com.ttscore.scraper.clicktt

import com.ttscore.database.*
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.clicktt.ClickTTGroupScraper.Companion.toChampionship
import com.ttscore.service.ClassificationService
import com.ttscore.util.clickTtNameToDb
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

class ClickTTMatchScraper(
    private val client: ClickTTClient,
    private val parser: ClickTTParser,
) {
    private val logger = LoggerFactory.getLogger(ClickTTMatchScraper::class.java)

    /**
     * Finds all completed click-tt matches that have no game rows yet and scrapes their details.
     */
    suspend fun run() {
        val matches = pendingMatches()
        logger.info("ClickTTMatchScraper: ${matches.size} completed matches without game details")
        scrapeAll(matches)
        logger.info("ClickTTMatchScraper complete")
    }

    /**
     * Scrapes game details for a specific set of match DB UUIDs (e.g. newly completed matches
     * detected by the poll job). Ignores any IDs that already have game rows.
     */
    suspend fun scrapeForMatches(matchIds: Set<UUID>) {
        if (matchIds.isEmpty()) return
        val matches = pendingMatches(filterIds = matchIds)
        logger.info("ClickTTMatchScraper: scraping ${matches.size} targeted matches")
        scrapeAll(matches)
    }

    private fun pendingMatches(filterIds: Set<UUID>? = null): List<MatchToScrape> =
        transaction {
            val homeTeam = Teams.alias("home_team")
            val awayTeam = Teams.alias("away_team")
            val rows =
                (Matches innerJoin Groups innerJoin Federations innerJoin Seasons)
                    .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                    .select(
                        Matches.id,
                        Matches.clickttMatchId,
                        Matches.homeTeamId,
                        Matches.awayTeamId,
                        Matches.playedAt,
                        Groups.clickttId,
                        Groups.name,
                        Federations.name,
                        Seasons.name,
                        homeTeam[Teams.name],
                        awayTeam[Teams.name],
                    )
                    .where {
                        val base =
                            (Matches.status eq MatchStatus.COMPLETED) and
                                (Matches.clickttMatchId.isNotNull()) and
                                (Groups.clickttId.isNotNull()) and
                                (Matches.id notInSubQuery Games.select(Games.matchId).withDistinct())
                        if (filterIds != null) base and (Matches.id inList filterIds) else base
                    }

            rows.map {
                MatchToScrape(
                    matchId = it[Matches.id],
                    clickttMatchId = it[Matches.clickttMatchId]!!,
                    clickttGroupId = it[Groups.clickttId]!!,
                    homeTeamId = it[Matches.homeTeamId],
                    awayTeamId = it[Matches.awayTeamId],
                    playedAt = it[Matches.playedAt],
                    federationName = it[Federations.name],
                    season = it[Seasons.name],
                    groupName = it[Groups.name],
                    homeTeamName = it[homeTeam[Teams.name]],
                    awayTeamName = it[awayTeam[Teams.name]],
                )
            }
        }

    private suspend fun scrapeAll(matches: List<MatchToScrape>) {
        for ((index, match) in matches.withIndex()) {
            try {
                scrapeMatch(match)
                if (index % 50 == 0) {
                    logger.info("Progress: $index / ${matches.size} matches scraped")
                }
            } catch (e: Exception) {
                logger.error("Failed meetingId=${match.clickttMatchId}: ${e.message}")
            }
        }
    }

    private suspend fun scrapeMatch(match: MatchToScrape) {
        val championship = toChampionship(match.federationName, match.season)
        val html = client.fetchMatchDetail(match.clickttMatchId, championship, match.clickttGroupId)
        val detail = parser.parseClickTTMatchDetail(html, match.clickttMatchId)

        if (detail.games.isEmpty()) {
            logger.debug("No games found for meetingId=${match.clickttMatchId}")
            return
        }

        transaction {
            val seasonId =
                Seasons.select(Seasons.id)
                    .where { Seasons.name eq match.season }
                    .first()[Seasons.id]

            for (game in detail.games) {
                val homePlayer1Id =
                    upsertPlayer(
                        game.homePersonId,
                        game.homeName,
                        game.homeKlass,
                        match.homeTeamId,
                        seasonId,
                        match.playedAt,
                    )
                val homePlayer2Id =
                    upsertPlayer(
                        game.homePersonId2,
                        game.homeName2,
                        game.homeKlass2,
                        match.homeTeamId,
                        seasonId,
                        match.playedAt,
                    )
                val awayPlayer1Id =
                    upsertPlayer(
                        game.awayPersonId,
                        game.awayName,
                        game.awayKlass,
                        match.awayTeamId,
                        seasonId,
                        match.playedAt,
                    )
                val awayPlayer2Id =
                    upsertPlayer(
                        game.awayPersonId2,
                        game.awayName2,
                        game.awayKlass2,
                        match.awayTeamId,
                        seasonId,
                        match.playedAt,
                    )

                Games.insertIgnore {
                    it[Games.matchId] = match.matchId
                    it[Games.gameType] = game.gameType
                    it[Games.orderInMatch] = game.orderInMatch.toShort()
                    it[Games.homePlayer1Id] = homePlayer1Id
                    it[Games.homePlayer2Id] = homePlayer2Id
                    it[Games.awayPlayer1Id] = awayPlayer1Id
                    it[Games.awayPlayer2Id] = awayPlayer2Id
                    it[Games.homeSets] = game.homeSets?.toShort()
                    it[Games.awaySets] = game.awaySets?.toShort()
                    it[Games.result] = game.result
                    it[Games.playedAt] = match.playedAt
                    it[Games.competitionName] = match.competitionName
                }

                val gameId =
                    Games.select(Games.id)
                        .where {
                            (Games.matchId eq match.matchId) and
                                (Games.orderInMatch eq game.orderInMatch.toShort())
                        }
                        .firstOrNull()?.get(Games.id) ?: continue

                for (set in game.sets) {
                    GameSets.insertIgnore {
                        it[GameSets.gameId] = gameId
                        it[GameSets.setNumber] = set.setNumber.toShort()
                        it[GameSets.homePoints] = set.homePoints.toShort()
                        it[GameSets.awayPoints] = set.awayPoints.toShort()
                    }
                }
            }
        }

        logger.debug("Scraped ${detail.games.size} games for meetingId=${match.clickttMatchId}")
    }

    /**
     * Looks up a player by their click-tt person ID and creates a player_season record.
     * The backfill job should have already created rows for all registered club members,
     * but if a player slips through (club ID outside the scanned range, mid-season join)
     * a minimal fallback row is inserted so the game result is not lost.
     */
    private fun upsertPlayer(
        personId: Int?,
        name: String?,
        className: String?,
        teamId: UUID,
        seasonId: UUID,
        playedAt: java.time.OffsetDateTime?,
    ): UUID? {
        val dbName = name?.let { clickTtNameToDb(it) }

        if (personId == null) {
            // Doubles player with no personId — best-effort name lookup. Resolve only when the name is
            // unique; refuse to guess between namesakes (linking a doubles game + class to the wrong
            // "Hess Matthias" is worse than leaving the slot unlinked).
            dbName ?: return null
            return Players.select(Players.id)
                .where { Players.fullName eq dbName }
                .limit(2)
                .map { it[Players.id] }
                .singleOrNull()
                ?.also { playerId ->
                    PlayerSeasons.insertIgnore {
                        it[PlayerSeasons.playerId] = playerId
                        it[PlayerSeasons.teamId] = teamId
                        it[PlayerSeasons.seasonId] = seasonId
                    }
                    ClassificationService.recordMatchClass(playerId, seasonId, playedAt, className)
                }
        }

        val playerId =
            Players.select(Players.id)
                .where { Players.clickttId eq personId }
                .firstOrNull()
                ?.get(Players.id)
                ?: run {
                    // Fallback: player wasn't created by the backfill job (club ID outside
                    // the scanned range, mid-season join, etc.). Insert a minimal row so
                    // the game result is not lost.
                    logger.warn("upsertPlayer: no row for clickttId=$personId (name=$dbName) — inserting fallback row")
                    Players.insertIgnore {
                        it[Players.clickttId] = personId
                        it[Players.fullName] = dbName ?: "Unknown"
                    }
                    Players.select(Players.id)
                        .where { Players.clickttId eq personId }
                        .first()[Players.id]
                }

        PlayerSeasons.insertIgnore {
            it[PlayerSeasons.playerId] = playerId
            it[PlayerSeasons.teamId] = teamId
            it[PlayerSeasons.seasonId] = seasonId
        }

        ClassificationService.recordMatchClass(playerId, seasonId, playedAt, className)

        return playerId
    }

    private data class MatchToScrape(
        val matchId: UUID,
        val clickttMatchId: Int,
        val clickttGroupId: Int,
        val homeTeamId: UUID,
        val awayTeamId: UUID,
        val playedAt: java.time.OffsetDateTime?,
        val federationName: String,
        val season: String,
        val groupName: String,
        val homeTeamName: String?,
        val awayTeamName: String?,
    ) {
        val competitionName: String
            get() = "$groupName | ${homeTeamName ?: "?"} : ${awayTeamName ?: "?"}"
    }
}
