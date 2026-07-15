package com.ttscore.scraper.clicktt

import com.ttscore.database.dbQuery
import com.ttscore.model.GameResult
import com.ttscore.scraper.clicktt.model.ClickTTGame
import com.ttscore.scraper.clicktt.model.ParsedTournamentGame
import com.ttscore.scraper.knob.mapConcurrent
import com.ttscore.service.ClassificationService
import com.ttscore.service.GameService
import com.ttscore.service.LiveEloService
import com.ttscore.service.PlayerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object ClickTTSyncService {
    private val logger = LoggerFactory.getLogger(ClickTTSyncService::class.java)
    private val client = ClickTTClient()
    private val parser = ClickTTParser()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val swissZone = ZoneId.of("Europe/Zurich")

    private val syncCooldowns = ConcurrentHashMap<UUID, Instant>()
    private val COOLDOWN = Duration.ofMinutes(5)

    // How many player portraits to sync in parallel during the full backfill. Each sync fans out to
    // ~3 concurrent click-tt fetches, so 8 players ≈ up to ~24 in-flight requests — in line with the
    // match scraper's SCRAPE_CONCURRENCY and comfortably within the 10-connection DB pool.
    private const val PORTRAIT_BACKFILL_CONCURRENCY = 8

    /** True when [playerId] was synced within the cooldown window — a fresh sync would be a no-op. */
    fun isWithinCooldown(playerId: UUID): Boolean {
        val lastSync = syncCooldowns[playerId] ?: return false
        return Instant.now().isBefore(lastSync.plus(COOLDOWN))
    }

    /** Syncs ELO and game history for all players with a click-tt ID. Intended for backfill. */
    suspend fun runPortraitBackfill(seasonId: UUID) {
        val players = PlayerService.getAllPlayersWithClickTtId()
        logger.info("Portrait backfill starting — ${players.size} players with click-tt ID")

        // Players are independent, so sync them with bounded concurrency instead of one-at-a-time.
        // Concurrency is deliberately lower than the knob SCRAPE_CONCURRENCY: each player sync itself
        // fans out to ~3 concurrent click-tt fetches, so this caps peak requests to click-tt at a
        // polite level, and stays within the DB pool (concurrent DB writes are safe now that Exposed
        // runs on a pooled DataSource).
        val processed = AtomicInteger(0)
        val results =
            players.mapConcurrent(PORTRAIT_BACKFILL_CONCURRENCY) { (playerId, personId) ->
                val result =
                    try {
                        syncPlayerDetailed(playerId, seasonId)
                    } catch (e: Exception) {
                        logger.warn("Portrait backfill failed for player $playerId (personId=$personId): ${e.message}")
                        null
                    }
                val done = processed.incrementAndGet()
                if (done % 50 == 0) logger.info("Portrait backfill progress: $done / ${players.size}")
                result
            }

        logger.info(
            "Portrait backfill complete — " +
                "${results.count { it != null }} ok, " +
                "${results.count { it != null && it.elo == null }} no-elo, " +
                "${results.count { it == null }} failed, " +
                "${results.sumOf { it?.tournamentGamesInserted ?: 0 }} tournament inserts, " +
                "${results.sumOf { it?.leagueDeltasUpdated ?: 0 }} elo-delta updates",
        )
    }

    /** Syncs a single player on demand (e.g. triggered from the player detail endpoint). */
    suspend fun syncPlayer(
        playerId: UUID,
        seasonId: UUID,
        ignoreCooldown: Boolean = false,
    ) {
        if (!ignoreCooldown && isWithinCooldown(playerId)) {
            logger.debug("Skipping sync for player $playerId — within cooldown window")
            return
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

        // The secondary pages are all derived from the portrait and independent of each other, so
        // fetch them concurrently rather than one-after-another:
        //  - the tournament/cup season pages (source of truth for non-league games), and
        //  - the Elo-Protokoll page (the only page carrying ELO deltas).
        val gameUrls = parser.extractPlayerGameUrls(portraitHtml)
        val eloUrl =
            parser.extractEloProtokollUrl(portraitHtml)
                ?.cleanWosid()
                ?.takeIf { it.isNotBlank() && it != "#" }
        if (eloUrl == null) logger.debug("  personId=$personId — no Elo-Protokoll tab found")

        val (gamePages, eloHtml) =
            coroutineScope {
                // Pair each page with its original URL so the cup/tournament classification (which
                // keys on the mode param) survives; cleanWosid only strips the session id.
                val gamePagesDeferred =
                    gameUrls.map { url -> async { url to fetchOrNull(url.cleanWosid(), "tournament/cup page") } }
                val eloDeferred = async { eloUrl?.let { fetchOrNull(it, "Elo-Protokoll") } }
                gamePagesDeferred.awaitAll() to eloDeferred.await()
            }

        // Insert tournament & cup games from the fetched pages. These are the source of truth for
        // non-league games (full result + set scores + opponent person-id). ELO deltas are filled
        // later from the Elo-Protokoll, so this must run BEFORE the delta update below.
        val tourneyInserts = insertTournamentGamesFromPages(playerId, gamePages)

        val portrait = parser.parsePlayerPortrait(portraitHtml, eloHtml, personId)
        logger.debug("  personId=$personId — elo=${portrait.currentElo}, games=${portrait.games.size}")

        if (portrait.currentElo != null) {
            val officialEntries = computeOfficialEloHistory(portrait.games, portrait.currentElo)
            val provisionalEntries = computeProvisionalEloHistory(playerId, officialEntries.lastOrNull()?.eloValue ?: portrait.currentElo)
            logger.debug("  personId=$personId — ${officialEntries.size} official + ${provisionalEntries.size} provisional ELO entries")
            PlayerService.replaceEloHistory(playerId, officialEntries + provisionalEntries)
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
     * Builds per-game official ELO history from the Elo-Protokoll game list.
     *
     * The Elo-Protokoll exposes the player's monthly official ELO (cells[2]) — same value for every
     * game settled in the same monthly rating run. When that value changes we snap to the new anchor,
     * then apply per-game deltas forward within the month to produce a data point after every rated
     * game. [currentElo] is the most recent official ELO from the summary table and is used as a
     * fallback anchor when no monthly value is available in the list.
     */
    private fun computeOfficialEloHistory(
        games: List<ClickTTGame>,
        currentElo: Int,
    ): List<PlayerService.EloHistoryEntry> {
        val sorted =
            games.mapNotNull { game ->
                runCatching { LocalDate.parse(game.date, dateFormatter) }.getOrNull()
                    ?.let { game to it }
            }.sortedBy { it.second }

        val entries = mutableListOf<PlayerService.EloHistoryEntry>()
        var monthlyAnchor: Int? = null
        var runningElo = 0.0
        val dayCounter = mutableMapOf<LocalDate, Int>()

        for ((game, date) in sorted) {
            if (game.eloDelta == null) continue

            // Snap to a new monthly anchor whenever the official column changes.
            val anchor = game.playerMonthlyElo ?: monthlyAnchor ?: currentElo
            if (monthlyAnchor == null || anchor != monthlyAnchor) {
                monthlyAnchor = anchor
                runningElo = anchor.toDouble()
            }

            runningElo += game.eloDelta

            val idx = dayCounter.getOrDefault(date, 0)
            dayCounter[date] = idx + 1
            entries.add(
                PlayerService.EloHistoryEntry(
                    eloValue = runningElo.roundToInt(),
                    recordedAt = date.atStartOfDay(swissZone).plusMinutes(idx.toLong()).toOffsetDateTime(),
                    isProvisional = false,
                ),
            )
        }

        return entries
    }

    /**
     * Computes provisional ELO entries for pending (not yet officially rated) games, starting from
     * [lastOfficialElo]. Each game advances the running ELO using our own calculator so the graph
     * extends right up to today without any frontend delta arithmetic.
     */
    private suspend fun computeProvisionalEloHistory(
        playerId: UUID,
        lastOfficialElo: Int,
    ): List<PlayerService.EloHistoryEntry> =
        dbQuery {
            val pending = LiveEloService.pendingGamesFor(playerId)
            if (pending.isEmpty()) return@dbQuery emptyList()

            val opponentBases =
                LiveEloService.baseElos(pending.mapNotNull { it.opponentId }.toSet())

            var runningElo = lastOfficialElo.toDouble()
            val dayCounter = mutableMapOf<LocalDate, Int>()

            pending.mapNotNull { game ->
                val opponentBase = game.opponentId?.let { opponentBases[it] } ?: return@mapNotNull null
                val delta = LiveEloService.provisionalDelta(
                    runningElo.roundToInt(),
                    opponentBase,
                    game.playerIsHome,
                    game.result,
                )
                runningElo += delta

                val date = game.playedAt?.toLocalDate() ?: return@mapNotNull null
                val idx = dayCounter.getOrDefault(date, 0)
                dayCounter[date] = idx + 1
                PlayerService.EloHistoryEntry(
                    eloValue = runningElo.roundToInt(),
                    recordedAt = date.atStartOfDay(swissZone).plusMinutes(idx.toLong()).toOffsetDateTime(),
                    isProvisional = true,
                )
            }
        }

    /** Fetches [url], returning null (and logging) on failure so one bad page doesn't abort a sync. */
    private suspend fun fetchOrNull(
        url: String,
        label: String,
    ): String? =
        try {
            client.fetchUrl(url)
        } catch (e: Exception) {
            logger.warn("  $label fetch failed for $url: ${e.message}")
            null
        }

    /**
     * Parses each already-fetched TOURNAMENT/Cup page (paired with its source URL, which selects the
     * cup vs. tournament parser) and inserts each singles game with its sets unless it already
     * exists. Returns the number of newly inserted games.
     */
    private suspend fun insertTournamentGamesFromPages(
        playerId: UUID,
        pages: List<Pair<String, String?>>,
    ): Int {
        var inserts = 0
        for ((url, html) in pages) {
            if (html == null) continue
            try {
                val isCup = url.contains("Cup", ignoreCase = true) && url.contains("mode=CHAMPIONSHIP")
                val games = if (isCup) parser.parseCupPage(html) else parser.parseTournamentPage(html)
                inserts += insertTournamentGames(playerId, games)
            } catch (e: Exception) {
                logger.warn("Tournament/cup page parse/insert failed for player $playerId ($url): ${e.message}")
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
        // `games` is in Elo-Protokoll page order (index 0 = topmost row); that index is stored as
        // the game's stable ordering key so same-day games no longer reshuffle across reloads. The
        // raw Protokoll opponent name + result is passed through; the matcher resolves the row
        // locally (the row's opponent is already correct via person-id), avoiding the duplicate-name
        // trap. Applied as one batch so the whole protocol is a single transaction, not one per game.
        val updates =
            games.withIndex().mapNotNull { (protocolOrder, game) ->
                val eloDelta = game.eloDelta ?: return@mapNotNull null
                val playedAt =
                    LocalDate.parse(game.date, dateFormatter)
                        .atStartOfDay(swissZone)
                        .toOffsetDateTime()
                GameService.EloDeltaUpdate(
                    opponentName = game.opponent,
                    dayStart = playedAt,
                    eloDelta = eloDelta,
                    eloOrder = protocolOrder,
                    competition = game.competition,
                )
            }
        return GameService.applyEloDeltas(playerId, updates)
    }

    /**
     * Removes the WebObjects session ID from click-tt URLs.
     * The wosid parameter is session-specific and causes requests to fail when reused.
     */
    private fun String.cleanWosid(): String =
        replace(Regex("([?&])wosid=[^&]+&?"), "$1")
            .trimEnd('?', '&')
}
