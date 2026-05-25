package com.ttscore.scraper.clicktt

import com.ttscore.jobs.ClickTtIdBackfillJob

/**
 * Orchestrates the full click-tt season scrape for a given season.
 *
 * Phase 1 â€” ClickTtIdBackfillJob: links click-tt person/club IDs to existing knob-scraped records,
 *            and inserts fresh rows for any registered players not yet in the DB.
 * Phase 2 â€” ClickTTGroupScraper: league structure, teams, standings, and match schedule
 * Phase 3 â€” ClickTTMatchScraper: individual game results, set scores, and player season records
 *
 * The backfill job runs first so all registered players exist before match scraping begins.
 */
class ClickTTSeasonScraper(
    private val groupScraper: ClickTTGroupScraper,
    private val backfillJob: ClickTtIdBackfillJob,
    private val matchScraper: ClickTTMatchScraper,
) {
    suspend fun run(
        season: String = "2025/2026",
        federations: Collection<String>? = null,
    ) {
        backfillJob.run()
        if (federations != null) groupScraper.run(season, federations) else groupScraper.run(season)
        matchScraper.run()
    }

    companion object {
        fun create(): ClickTTSeasonScraper {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTTSeasonScraper(
                groupScraper = ClickTTGroupScraper(client, parser),
                backfillJob = ClickTtIdBackfillJob(client, parser),
                matchScraper = ClickTTMatchScraper(client, parser),
            )
        }
    }
}
