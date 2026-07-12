package com.ttscore.scraper.knob

import com.ttscore.database.*
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.knob.model.ParsedMatchDetail
import com.ttscore.service.ClassificationService
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

/** Matches fetched+parsed concurrently, then written, per batch — bounds in-memory parsed detail. */
private const val MATCH_BATCH = 200

class MatchScraper(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(MatchScraper::class.java)

    suspend fun run() {
        val matches =
            transaction {
                val homeTeam = Teams.alias("home_team")
                val awayTeam = Teams.alias("away_team")
                (Matches innerJoin Groups innerJoin Federations innerJoin Seasons)
                    .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                    .select(
                        Matches.id,
                        Matches.knobMatchId,
                        Matches.homeTeamId,
                        Matches.awayTeamId,
                        Matches.playedAt,
                        Groups.knobGruppe,
                        Groups.name,
                        Federations.name,
                        Seasons.id,
                        Seasons.name,
                        homeTeam[Teams.name],
                        awayTeam[Teams.name],
                    )
                    .where {
                        (Matches.status eq MatchStatus.COMPLETED) and
                            (Matches.knobMatchId.isNotNull()) and
                            (Groups.knobGruppe.isNotNull()) and
                            (Matches.id notInSubQuery Games.select(Games.matchId).withDistinct())
                    }
                    .map {
                        MatchToScrape(
                            matchId = it[Matches.id],
                            knobMatchId = it[Matches.knobMatchId]!!,
                            homeTeamId = it[Matches.homeTeamId],
                            awayTeamId = it[Matches.awayTeamId],
                            playedAt = it[Matches.playedAt],
                            knobGruppe = it[Groups.knobGruppe]!!,
                            rvid = FEDERATION_RVIDS[it[Federations.name]],
                            seasonId = it[Seasons.id],
                            season = it[Seasons.name],
                            groupName = it[Groups.name],
                            homeTeamName = it[homeTeam[Teams.name]],
                            awayTeamName = it[awayTeam[Teams.name]],
                        )
                    }
            }

        logger.info("MatchScraper: ${matches.size} completed matches without game details")

        // Process in batches: fetch + parse each batch concurrently (network/CPU only), then
        // write the batch serially. Batching bounds how many parsed details are held in memory
        // at once; the concurrent fetch is what removes the per-match network wait.
        var done = 0
        for (batch in matches.chunked(MATCH_BATCH)) {
            val parsed =
                batch.mapConcurrent(SCRAPE_CONCURRENCY) { match ->
                    try {
                        val html =
                            client.fetchMatchDetail(match.knobGruppe, match.knobMatchId, match.season, match.rvid)
                        match to parser.parseMatchDetail(html, match.knobMatchId)
                    } catch (e: Exception) {
                        logger.error("Failed matchId=${match.knobMatchId} gruppe=${match.knobGruppe}: ${e.message}")
                        null
                    }
                }.filterNotNull()

            for ((match, detail) in parsed) {
                try {
                    if (detail.games.isEmpty()) {
                        logger.debug("No games found for matchId=${match.knobMatchId}")
                        continue
                    }
                    writeMatchGames(match, detail)
                } catch (e: Exception) {
                    logger.error("Failed matchId=${match.knobMatchId} gruppe=${match.knobGruppe}: ${e.message}")
                }
            }

            done += batch.size
            logger.info("Progress: ${done.coerceAtMost(matches.size)} / ${matches.size} matches processed")
        }

        logger.info("MatchScraper complete")
    }

    private fun writeMatchGames(
        match: MatchToScrape,
        detail: ParsedMatchDetail,
    ) {
        transaction {
            for (game in detail.games) {
                val homePlayer1Id =
                    upsertPlayer(
                        game.homePlayer1KnobId,
                        game.homePlayer1Name,
                        game.homePlayer1Klass,
                        match.homeTeamId,
                        match.seasonId,
                        match.playedAt,
                    )
                val homePlayer2Id =
                    upsertPlayer(
                        game.homePlayer2KnobId,
                        game.homePlayer2Name,
                        null,
                        match.homeTeamId,
                        match.seasonId,
                        match.playedAt,
                    )
                val awayPlayer1Id =
                    upsertPlayer(
                        game.awayPlayer1KnobId,
                        game.awayPlayer1Name,
                        game.awayPlayer1Klass,
                        match.awayTeamId,
                        match.seasonId,
                        match.playedAt,
                    )
                val awayPlayer2Id =
                    upsertPlayer(
                        game.awayPlayer2KnobId,
                        game.awayPlayer2Name,
                        null,
                        match.awayTeamId,
                        match.seasonId,
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

        logger.debug("Scraped ${detail.games.size} games for matchId=${match.knobMatchId}")
    }

    /**
     * Returns the player's UUID, inserting a new player and player_season record if the knobId
     * is not yet in the database. This ensures match detail scraping never produces null player
     * references — players who only appear as substitutes or guests are created on the fly.
     */
    private fun upsertPlayer(
        knobId: Int?,
        name: String?,
        className: String?,
        teamId: UUID,
        seasonId: UUID,
        playedAt: java.time.OffsetDateTime?,
    ): UUID? {
        if (knobId == null) {
            // Doubles player 2 with no gid on the page — best-effort name lookup. Resolve only when the
            // name is unique; refuse to guess between namesakes rather than link a doubles game + class
            // to an arbitrary player who happens to share the name.
            name ?: return null
            return Players.select(Players.id)
                .where { Players.fullName eq name }
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

        val existing =
            Players.select(Players.id)
                .where { Players.knobId eq knobId }
                .firstOrNull()

        val playerId =
            if (existing != null) {
                existing[Players.id]
            } else {
                Players.insertIgnore {
                    it[Players.knobId] = knobId
                    it[Players.fullName] = name ?: "Unknown"
                }
                Players.select(Players.id)
                    .where { Players.knobId eq knobId }
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
        val knobMatchId: Int,
        val homeTeamId: UUID,
        val awayTeamId: UUID,
        val playedAt: java.time.OffsetDateTime?,
        val knobGruppe: Int,
        val rvid: Int?,
        val seasonId: UUID,
        val season: String,
        val groupName: String,
        val homeTeamName: String?,
        val awayTeamName: String?,
    ) {
        val competitionName: String
            get() = "$groupName | ${homeTeamName ?: "?"} : ${awayTeamName ?: "?"}"
    }
}
