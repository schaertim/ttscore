package com.ttscore.scraper.clicktt

import com.ttscore.model.GameResult
import com.ttscore.scraper.clicktt.model.ClickTTGame
import com.ttscore.scraper.clicktt.model.ParsedTournamentGame
import com.ttscore.service.ClassificationService
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
        var deltaUpdates = 0

        for ((index, pair) in players.withIndex()) {
            val (playerId, personId) = pair
            try {
                val result = syncPlayerDetailed(playerId, seasonId)
                successCount++
                tourneyInserts += result.tournamentGamesInserted
                deltaUpdates += result.leagueDeltasUpdated
                if (result.elo == null) noEloCount++
                if (index % 50 == 0) {
                    logger.info(
                        "Portrait backfill progress: $index / ${players.size} — " +
                            "$successCount ok, $noEloCount no-elo, $failCount failed, " +
                            "$tourneyInserts tournament inserts, $deltaUpdates elo-delta updates",
                    )
                }
            } catch (e: Exception) {
                failCount++
                logger.warn("Portrait backfill failed for player $playerId (personId=$personId): ${e.message}")
            }
        }

        logger.info(
            "Portrait backfill complete — $successCount ok, $noEloCount no-elo, $failCount failed, " +
                "$tourneyInserts tournament inserts, $deltaUpdates elo-delta updates",
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

        // 1. Insert tournament & cup games from the dedicated season pages. These are the source of
        //    truth for non-league games (full result + set scores + opponent person-id). ELO deltas
        //    are filled later from the Elo-Protokoll, so we run this BEFORE the delta update below.
        val tourneyInserts = insertTournamentAndCupGames(playerId, portraitHtml)

        // 2. ELO snapshot + delta/competition decoration from the Elo-Protokoll. This page is the
        //    only one with ELO deltas, and it is used exclusively to UPDATE existing league and
        //    tournament rows — it never inserts, which is what prevents duplicate game rows.
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

        // Fallback: the portrait's "Klassierung" is the player's current class. Use it to fill the
        // current half of the current season when no match observation provided one (e.g. players
        // who only appear in tournaments/cups and have no league match this half).
        ClassificationService.fillCurrentClassIfAbsent(playerId, seasonId, parser.parseCurrentClass(portraitHtml))

        val deltaUpdates =
            if (portrait.games.isNotEmpty()) {
                updateEloDeltas(playerId, portrait.games)
            } else {
                0
            }

        return SyncResult(portrait.currentElo, tourneyInserts, deltaUpdates)
    }

    /**
     * Fetches the player's TOURNAMENT and Cup season pages and inserts each singles game (with its
     * sets) unless it already exists. Returns the number of newly inserted games.
     */
    private suspend fun insertTournamentAndCupGames(
        playerId: UUID,
        portraitHtml: String,
    ): Int {
        val urls = parser.extractPlayerGameUrls(portraitHtml)
        var inserts = 0
        for (url in urls) {
            try {
                val html = client.fetchUrl(url.cleanWosid())
                val isCup = url.contains("Cup", ignoreCase = true) && url.contains("mode=CHAMPIONSHIP")
                val games = if (isCup) parser.parseCupPage(html) else parser.parseTournamentPage(html)
                inserts += insertTournamentGames(playerId, games)
            } catch (e: Exception) {
                logger.warn("Tournament/cup page fetch/parse failed for player $playerId: ${e.message}")
            }
        }
        return inserts
    }

    private suspend fun insertTournamentGames(
        playerId: UUID,
        games: List<ParsedTournamentGame>,
    ): Int {
        var inserts = 0
        for (game in games) {
            val opponentId =
                game.opponentPersonId?.let { PlayerService.findPlayerIdByClickTtId(it) }
                    ?: PlayerService.findPlayerIdByName(game.opponentName)

            val playedAt = game.date.atStartOfDay(swissZone).toOffsetDateTime()
            // Derive the result from the set score (authoritative) rather than the win icon, which
            // can be missing/misdetected and would then disagree with the stored sets — producing a
            // wrong-signed ELO delta (a win scored as a loss).
            val result =
                when {
                    game.homeSets > game.awaySets -> GameResult.HOME
                    game.awaySets > game.homeSets -> GameResult.AWAY
                    else -> GameResult.NOT_PLAYED
                }

            val inserted =
                GameService.insertTournamentGameIfAbsent(
                    playerId = playerId,
                    opponentId = opponentId,
                    playedAt = playedAt,
                    competition = game.competition,
                    result = result,
                    homeSets = game.homeSets,
                    awaySets = game.awaySets,
                    sets = game.sets,
                )
            if (inserted) inserts++
        }
        return inserts
    }

    /**
     * Walks the Elo-Protokoll games and writes each player's ELO delta (and competition name) onto
     * the matching existing row — league or tournament. Never inserts. Unrated rows (no delta) are
     * skipped. Returns the number of rows updated.
     */
    private suspend fun updateEloDeltas(
        playerId: UUID,
        games: List<ClickTTGame>,
    ): Int {
        var updates = 0
        for (game in games) {
            val eloDelta = game.eloDelta ?: continue
            val opponentName = game.opponent.substringBefore("(").trim()
            val opponentId = PlayerService.findPlayerIdByName(opponentName)

            val playedAt =
                LocalDate.parse(game.date, dateFormatter)
                    .atStartOfDay(swissZone)
                    .toOffsetDateTime()

            if (GameService.updateGameEloDelta(playerId, opponentId, playedAt, eloDelta, game.competition)) {
                updates++
            }
        }
        return updates
    }

    /**
     * Removes the WebObjects session ID from click-tt URLs.
     * The wosid parameter is session-specific and causes requests to fail when reused.
     */
    private fun String.cleanWosid(): String =
        replace(Regex("([?&])wosid=[^&]+&?"), "$1")
            .trimEnd('?', '&')
}
