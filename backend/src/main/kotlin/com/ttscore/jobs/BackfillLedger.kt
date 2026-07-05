package com.ttscore.jobs

import com.ttscore.database.ScraperBackfill
import com.ttscore.database.dbQuery
import org.jetbrains.exposed.sql.insertIgnore
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/**
 * Runs expensive scraper backfills exactly once, ever. Each keyed backfill records its key
 * in the scraper_backfill table on success, so server restarts never re-trigger it.
 *
 * The key is recorded only after [block] completes without throwing — a failed or partial
 * run leaves the key absent and retries on the next boot.
 */
object BackfillLedger {
    private val logger = LoggerFactory.getLogger(BackfillLedger::class.java)

    suspend fun runOnce(
        key: String,
        block: suspend () -> Unit,
    ) {
        if (isDone(key)) {
            logger.info("Backfill '$key' already completed — skipping")
            return
        }
        logger.info("Backfill '$key' starting")
        block()
        markDone(key)
        logger.info("Backfill '$key' complete")
    }

    private suspend fun isDone(key: String): Boolean =
        dbQuery {
            ScraperBackfill.select(ScraperBackfill.key)
                .where { ScraperBackfill.key eq key }
                .firstOrNull() != null
        }

    private suspend fun markDone(key: String) {
        dbQuery {
            ScraperBackfill.insertIgnore {
                it[ScraperBackfill.key] = key
                it[ScraperBackfill.completedAt] = OffsetDateTime.now()
            }
        }
    }
}
