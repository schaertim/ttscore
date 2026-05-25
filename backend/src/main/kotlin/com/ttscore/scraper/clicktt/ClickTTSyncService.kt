package com.ttscore.scraper.clicktt

import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.scraper.clicktt.model.ClickTTGame
import com.ttscore.scraper.clicktt.model.ParsedTournamentGame
import com.ttscore.service.GameService
import com.ttscore.service.PlayerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClickTTSyncService {
    private val logger = LoggerFactory.getLogger(ClickTTSyncService::class.java)
    private val client = ClickTTClient()
    private val parser = ClickTTParser()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val swissZone = ZoneId.of("Europe/Zurich")

    private val syncCooldowns = ConcurrentHashMap<UUID, Instant>()
    private val COOLDOWN = Duration.ofMinutes(5)

    /** Syncs ELO and game history for all players with a click-tt ID. Intended for backfill. */
    suspend fun runPortraitBackfill(seasonId: UUID) {
        val players = PlayerService.getAllPlayersWithClickTtId()
        logger.info("Portrait backfill starting — ${players.size} players with click-tt ID")

        var successCount = 0
        var noEloCount = 0
        var failCount = 0
        var tourneyInserts = 0
        var leagueUpdates = 0

        for ((index, pair) in players.withIndex()) {
            val (playerId, personId) = pair
            try {
                val result = syncPlayerDetailed(playerId, seasonId)
                successCount++
                tourneyInserts += result.tournamentGamesInserted
                leagueUpdates += result.leagueDeltasUpdated
                if (result.elo == null) noEloCount++
                if (index % 50 == 0) {
                    logger.info(
                        "Portrait backfill progress: $index / ${players.size} — " +
                            "$successCount ok, $noEloCount no-elo, $failCount failed, " +
                            "$tourneyInserts tournament inserts, $leagueUpdates league delta updates",
                    )
                }
            } catch (e: Exception) {
                failCount++
                logger.warn("Portrait backfill failed for player $playerId (personId=$personId): ${e.message}")
            }
        }

        logger.info(
            "Portrait backfill complete — $successCount ok, $noEloCount no-elo, $failCount failed, " +
                "$tourneyInserts tournament inserts, $leagueUpdates league delta updates",
        )
    }

    /** Syncs a single player on demand (e.g. triggered from the player detail endpoint). */
    suspend fun syncPlayer(
        playerId: UUID,
        seasonId: UUID,
        ignoreCooldown: Boolean = false,
    ) {
        if (!ignoreCooldown) {
            val lastSync = syncCooldowns[playerId]
            if (lastSync != null && Instant.now().isBefore(lastSync.plus(COOLDOWN))) {
                logger.debug("Skipping sync for player $playerId — within cooldown window")
                return
            }
        }
        syncCooldowns[playerId] = Instant.now()
        syncPlayerDetailed(playerId, seasonId)
    }

    private data class SyncResult(
        val elo: Int?,
        val tournamentGamesInserted: Int,
        val leagueDeltasUpdated: Int,
    )

    private suspend fun syncPlayerDetailed(
        playerId: UUID,
        seasonId: UUID,
    ): SyncResult {
        val personId = PlayerService.getClickTtIdById(playerId)
        if (personId == null) {
            logger.warn("Cannot sync player $playerId — no clickttId in database")
            return SyncResult(null, 0, 0)
        }

        logger.debug("Syncing personId=$personId")
        val portraitHtml = client.fetchPlayerPortrait(personId)

        val eloUrl =
            parser.extractEloProtokollUrl(portraitHtml)
                ?.cleanWosid()
                ?.takeIf { it.isNotBlank() && it != "#" }

        if (eloUrl == null) logger.debug("  personId=$personId — no Elo-Protokoll tab found")

        val eloHtml =
            if (eloUrl != null) {
                try {
                    client.fetchUrl(eloUrl)
                } catch (e: Exception) {
                    logger.warn(
                        "  personId=$personId — Elo-Protokoll fetch failed, ELO snapshot still saved: ${e.message}",
                    )
                    null
                }
            } else {
                null
            }

        val portrait = parser.parsePlayerPortrait(portraitHtml, eloHtml, personId)
        logger.debug("  personId=$personId — elo=${portrait.currentElo}, games=${portrait.games.size}")

        if (portrait.currentElo != null) {
            PlayerService.saveBaseElo(playerId, seasonId, portrait.currentElo)
        } else {
            logger.debug("  personId=$personId — no current ELO on portrait page")
        }

        val (tourneyInserts, leagueUpdates) =
            if (portrait.games.isNotEmpty()) {
                processGames(playerId, portrait.games)
            } else {
                0 to 0
            }

        updateTournamentSets(playerId, portraitHtml)

        return SyncResult(portrait.currentElo, tourneyInserts, leagueUpdates)
    }

    private suspend fun processGames(
        playerId: UUID,
        games: List<ClickTTGame>,
    ): Pair<Int, Int> {
        var tourneyInserts = 0
        var leagueUpdates = 0

        for (game in games) {
            val opponentName = game.opponent.substringBefore("(").trim()
            val opponentId = PlayerService.findPlayerIdByName(opponentName)
            val result = if (game.isWin) GameResult.HOME else GameResult.AWAY

            val playedAt =
                LocalDate.parse(game.date, dateFormatter)
                    .atStartOfDay(swissZone)
                    .toOffsetDateTime()

            // First try to backfill the ELO delta on an existing league game row
            if (game.eloDelta != null) {
                val updated = GameService.updateLeagueGameEloDelta(playerId, opponentId, playedAt, game.eloDelta, game.competition)
                if (updated) {
                    leagueUpdates++
                    continue
                }
            } else if (GameService.leagueGameExists(playerId, opponentId, playedAt)) {
                // League game exists but hasn't been rated yet — don't insert a spurious tournament record
                continue
            }

            // No league game found — insert as tournament game if not already stored
            if (!GameService.gameExists(playerId, opponentId, playedAt, game.competition)) {
                GameService.insertTournamentGame(
                    playerId = playerId,
                    opponentId = opponentId,
                    playedAt = playedAt,
                    competition = game.competition,
                    eloDelta = game.eloDelta,
                    result = result,
                    gameType = GameType.SINGLES,
                )
                tourneyInserts++
            }
        }

        return tourneyInserts to leagueUpdates
    }

    private suspend fun updateTournamentSets(
        playerId: UUID,
        portraitHtml: String,
    ) {
        val urls = parser.extractPlayerGameUrls(portraitHtml)
        for (url in urls) {
            try {
                val html = client.fetchUrl(url.cleanWosid())
                val isCup = url.contains("Cup", ignoreCase = true) && url.contains("mode=CHAMPIONSHIP")
                val games = if (isCup) parser.parseCupPage(html) else parser.parseTournamentPage(html)
                applyTournamentSets(playerId, games)
            } catch (e: Exception) {
                logger.warn("Tournament page fetch/parse failed for player $playerId: ${e.message}")
            }
        }
    }

    private suspend fun applyTournamentSets(
        playerId: UUID,
        games: List<ParsedTournamentGame>,
    ) {
        for (game in games) {
            val opponentId =
                if (game.opponentPersonId != null) {
                    PlayerService.findPlayerIdByClickTtId(game.opponentPersonId)
                } else {
                    PlayerService.findPlayerIdByName(game.opponentName)
                } ?: continue

            val dayStart = game.date.atStartOfDay(swissZone).toOffsetDateTime()

            val gameId =
                GameService.updateTournamentGameSets(
                    playerId = playerId,
                    opponentId = opponentId,
                    dayStart = dayStart,
                    homeSets = game.homeSets,
                    awaySets = game.awaySets,
                ) ?: continue

            if (game.sets.isNotEmpty()) {
                GameService.insertTournamentSets(gameId, game.sets)
            }
        }
    }

    /**
     * Removes the WebObjects session ID from click-tt URLs.
     * The wosid parameter is session-specific and causes requests to fail when reused.
     */
    private fun String.cleanWosid(): String =
        replace(Regex("([?&])wosid=[^&]+&?"), "$1")
            .trimEnd('?', '&')
}
