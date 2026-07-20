package com.ttscore.scraper.knob

import com.ttscore.database.Seasons
import com.ttscore.service.PlayerService
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Attaches STT licence numbers to knob player rows — the authoritative identity spine.
 *
 * knob has no shared player key across its pages (match/profile pages carry a gid but no licence;
 * the licence registry carries a licence but no gid), so the two must be joined. Rather than guess
 * that join by name+club — the source of every namesake mislink — this drives off knob's own
 * licence search, the one page that maps a licence to its exact gid(s). For every licence the
 * registry ever lists we look up its gid(s) and hand the pair to [PlayerService.reconcileLicence],
 * which stamps the licence onto the right row and, in the same step, merges any duplicate rows knob
 * created by assigning that person several gids over the years.
 *
 * Because the join is by id, not name, there is no disambiguation to get wrong: a licence either
 * resolves to a gid we hold (attached) or it doesn't (the person never appeared in a scraped match).
 * The only remaining fuzzy matching in the whole pipeline is click-tt linking for players who have
 * no licence at all — an inherently unsolvable set no method could help.
 *
 * Always covers the full 1989-present range: each season's registry page lists only that season's
 * transfers, so a player who last moved years ago appears on exactly one page.
 */
class OverallPlayerScraper(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(OverallPlayerScraper::class.java)

    suspend fun run() {
        val latestSeasonName =
            transaction {
                Seasons.select(Seasons.name)
                    .orderBy(Seasons.name, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(Seasons.name)
            }
        if (latestSeasonName == null) {
            logger.warn("OverallPlayerScraper: no seasons in DB, skipping")
            return
        }

        val toYear = latestSeasonName.substringBefore("/").toInt()
        val seasons = generateSeasons(fromYear = 1989, toYear = toYear)
        logger.info("OverallPlayerScraper: collecting licences across ${seasons.size} seasons up to $latestSeasonName")

        // 1. Collect every distinct licence from all registry pages (network + parse, no DB writes).
        val licences = linkedSetOf<String>()
        for (seasonName in seasons) {
            try {
                val players = parser.parseOverallPlayers(client.fetchOverallPlayers(seasonName))
                if (players.isEmpty()) {
                    logger.info("  $seasonName — no players found (licence registry predates online records)")
                } else {
                    logger.info("  $seasonName — ${players.size} licensed players")
                    licences += players.map { it.licenceNr }
                }
            } catch (e: Exception) {
                logger.error("Failed season=$seasonName: ${e.message}")
            }
        }
        logger.info("OverallPlayerScraper: ${licences.size} distinct licences to resolve")

        // 2. Authoritative licence→gid search (concurrent, network only).
        val gidMap =
            licences.toList().mapConcurrent(SCRAPE_CONCURRENCY) { licence ->
                try {
                    licence to parser.parseSearchGids(client.searchByLicence(licence))
                } catch (e: Exception) {
                    logger.warn("  licence search failed for $licence: ${e.message}")
                    licence to emptyList()
                }
            }

        // 3. Attach + merge serially (per-licence try/catch so one bad merge is logged, not fatal).
        var attached = 0
        var merged = 0
        var noGid = 0
        var noRow = 0
        var processed = 0
        for ((licence, gids) in gidMap) {
            try {
                if (gids.isEmpty()) {
                    noGid++
                } else {
                    val outcome = PlayerService.reconcileLicence(licence, gids)
                    if (outcome.attached) attached++ else noRow++
                    merged += outcome.merged
                }
            } catch (e: Exception) {
                logger.warn("  reconcile failed for licence $licence: ${e.message}")
            }
            if (++processed % 2000 == 0) logger.info("  progress: $processed / ${gidMap.size} licences")
        }

        logger.info(
            "OverallPlayerScraper complete — $attached licences linked to a knob player, " +
                "$merged duplicate rows merged, $noGid with no knob gid, $noRow gid not in our data",
        )
    }
}
