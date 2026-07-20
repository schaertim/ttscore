package com.ttscore.scraper.knob

import com.ttscore.database.*
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.knob.model.ParsedMatchDetail
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
                // "Completed matches with no game rows yet" as a LEFT JOIN … IS NULL anti-join. A plain
                // `id NOT IN (SELECT match_id FROM game)` is wrong: tournament/cup games carry
                // match_id = NULL, and `x NOT IN (… NULL …)` is never true, so that form silently
                // matched nothing once any tournament game existed. The anti-join also avoids a full
                // distinct scan of the 1.3M-row game table (index probe on idx_game_match instead).
                (Matches innerJoin Groups innerJoin Federations innerJoin Seasons)
                    .join(homeTeam, JoinType.LEFT, Matches.homeTeamId, homeTeam[Teams.id])
                    .join(awayTeam, JoinType.LEFT, Matches.awayTeamId, awayTeam[Teams.id])
                    .join(Games, JoinType.LEFT, Matches.id, Games.matchId)
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
                            Games.matchId.isNull()
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
                    upsertPlayer(game.homePlayer1KnobId, game.homePlayer1Name, match.homeTeamId, match.seasonId)
                val homePlayer2Id =
                    upsertPlayer(game.homePlayer2KnobId, game.homePlayer2Name, match.homeTeamId, match.seasonId)
                val awayPlayer1Id =
                    upsertPlayer(game.awayPlayer1KnobId, game.awayPlayer1Name, match.awayTeamId, match.seasonId)
                val awayPlayer2Id =
                    upsertPlayer(game.awayPlayer2KnobId, game.awayPlayer2Name, match.awayTeamId, match.seasonId)

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
     *
     * Classification is intentionally NOT recorded here: the per-match bracket is on a women's
     * ladder for women's-only divisions, so class is filled afterwards from each player's profile
     * page (always men's-ladder) by [KnobPlayerClassScraper].
     */
    private fun upsertPlayer(
        knobId: Int?,
        name: String?,
        teamId: UUID,
        seasonId: UUID,
    ): UUID? {
        if (knobId == null) {
            // Doubles player 2 with no gid on the page — best-effort name lookup. Resolve only when the
            // name is unique; refuse to guess between namesakes rather than link a doubles game to an
            // arbitrary player who happens to share the name.
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
