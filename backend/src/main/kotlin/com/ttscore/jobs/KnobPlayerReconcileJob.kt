package com.ttscore.jobs

import com.ttscore.scraper.knob.KnobClient
import com.ttscore.scraper.knob.KnobParser
import com.ttscore.scraper.knob.SCRAPE_CONCURRENCY
import com.ttscore.scraper.knob.mapConcurrent
import com.ttscore.service.PlayerService
import org.slf4j.LoggerFactory

/**
 * Collapses duplicate player rows using knob's licence search — the only page that maps a licence
 * to its player id(s).
 *
 * Two duplication sources both trace back to knob having no shared player key across its pages:
 *   1. A licence never got linked to a knob row (namesakes made name-matching ambiguous), so the
 *      click-tt backfill inserted a fresh, orphaned click-tt-only row for that person.
 *   2. knob assigns one person several gids over the years, which land as separate player rows.
 *
 * For every distinct licence we hold, this searches knob → gid(s) and hands the pair to
 * [PlayerService.reconcileLicence], which merges every row that is that person into one. Runs after
 * the click-tt id backfill (so orphans exist) but before click-tt season scraping (so those orphans
 * have no gameplay yet and merge cheaply).
 */
class KnobPlayerReconcileJob(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(KnobPlayerReconcileJob::class.java)

    suspend fun run() {
        val licences = PlayerService.distinctLicences()
        logger.info("KnobPlayerReconcileJob: ${licences.size} licences to resolve")

        // Stage 1 — search every licence concurrently (network only, no DB writes).
        val gidMap =
            licences.mapConcurrent(SCRAPE_CONCURRENCY) { licence ->
                try {
                    licence to parser.parseSearchGids(client.searchByLicence(licence))
                } catch (e: Exception) {
                    logger.warn("  search failed for licence $licence: ${e.message}")
                    licence to emptyList()
                }
            }

        // Stage 2 — reconcile serially (single non-pooled connection; per-licence try/catch so one
        // bad merge is logged and skipped, not fatal).
        var mergedRows = 0
        var stampedRows = 0
        var noGid = 0
        var processed = 0
        for ((licence, gids) in gidMap) {
            try {
                if (gids.isEmpty()) {
                    noGid++
                } else {
                    val outcome = PlayerService.reconcileLicence(licence, gids)
                    mergedRows += outcome.merged
                    if (outcome.stamped) stampedRows++
                }
            } catch (e: Exception) {
                logger.warn("  reconcile failed for licence $licence: ${e.message}")
            }
            if (++processed % 2000 == 0) {
                logger.info("  progress: $processed / ${gidMap.size} licences")
            }
        }

        logger.info(
            "KnobPlayerReconcileJob complete — $mergedRows duplicate rows merged, " +
                "$stampedRows rows stamped with a new knob id, $noGid licences with no knob match",
        )
    }

    companion object {
        fun create(): KnobPlayerReconcileJob = KnobPlayerReconcileJob(KnobClient(), KnobParser())
    }
}
