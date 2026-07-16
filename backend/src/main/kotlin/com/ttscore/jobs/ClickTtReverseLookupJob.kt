package com.ttscore.jobs

import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTParser
import com.ttscore.scraper.knob.SCRAPE_CONCURRENCY
import com.ttscore.scraper.knob.mapConcurrent
import com.ttscore.service.PlayerService
import org.slf4j.LoggerFactory

/**
 * Reverse click-tt linking: resolves click-tt person IDs for players the roster-driven
 * [ClickTtIdBackfillJob] can't reach because they no longer appear on any club's current
 * "licensed players" page (lapsed/expired licence, took a season off, …). Such players still
 * resolve on click-tt's Elo-Filter, which searches the full ranking history by licence number.
 *
 * Complements — does not replace — [ClickTtIdBackfillJob]: run this after it, so it only pays the
 * per-player lookup for the residue that pass leaves unlinked. Idempotent: once a player has a
 * click-tt id they drop out of the candidate set.
 */
class ClickTtReverseLookupJob(
    private val client: ClickTTClient,
    private val parser: ClickTTParser,
) {
    private val logger = LoggerFactory.getLogger(ClickTtReverseLookupJob::class.java)

    suspend fun run() {
        val candidates = PlayerService.getPlayersMissingClickTtId()
        if (candidates.isEmpty()) {
            logger.info("ClickTtReverseLookupJob: no players missing a click-tt id")
            return
        }

        // The Elo-Filter search must be pinned to the current monthly ranking date, which is the
        // pre-selected option on the empty form. Bail rather than guess if the page shape changed.
        val rankingDate = parser.parseEloFilterRankingDate(client.fetchEloFilterForm())
        if (rankingDate == null) {
            logger.error("ClickTtReverseLookupJob: could not read ranking date from Elo-Filter form — aborting")
            return
        }

        logger.info(
            "ClickTtReverseLookupJob: resolving ${candidates.size} players via Elo-Filter (ranking $rankingDate)",
        )

        // Network + parse stage, bounded concurrency; the DB write happens after (serialized).
        val resolved =
            candidates.mapConcurrent(SCRAPE_CONCURRENCY) { (playerId, licence) ->
                try {
                    resolvePersonId(licence, rankingDate)?.let { personId -> licence to personId }
                } catch (e: Exception) {
                    logger.warn("  Elo-Filter lookup failed for player $playerId (licence $licence): ${e.message}")
                    null
                }
            }.filterNotNull()

        // click-tt licences are 1:1 with persons and our licences are unique, so distinct licences
        // yield distinct person ids — no in-batch collision. Still drop any id another row already
        // holds, so one dirty datum can't abort the whole batch update on the unique constraint.
        val taken = PlayerService.getAssignedClickTtIds()
        val mappings = resolved.filter { (_, personId) -> personId !in taken }.toMap()

        PlayerService.updateClickTtIdsBatch(mappings)

        logger.info(
            "ClickTtReverseLookupJob complete — ${mappings.size} linked, " +
                "${resolved.size - mappings.size} skipped (already assigned), " +
                "${candidates.size - resolved.size} not found on click-tt",
        )
    }

    /** Two-step Elo-Filter resolution: search by licence → follow the result row to the person id. */
    private suspend fun resolvePersonId(
        licence: String,
        rankingDate: String,
    ): Int? {
        val searchHtml = client.fetchEloFilterByLicence(licence, rankingDate)
        val detailHref = parser.parseEloFilterResultHref(searchHtml) ?: return null
        val detailHtml = client.fetchUrl(detailHref)
        return parser.parseEloFilterPersonId(detailHtml)
    }

    companion object {
        fun create(): ClickTtReverseLookupJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTtReverseLookupJob(client, parser)
        }
    }
}
