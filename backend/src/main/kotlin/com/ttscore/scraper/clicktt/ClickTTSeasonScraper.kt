package com.ttscore.scraper.clicktt

/**
 * Seeds a single click-tt season from scratch: league structure + match details.
 *
 * Phase 1 — ClickTTGroupScraper: league structure, teams, standings, and match schedule
 * Phase 2 — ClickTTMatchScraper: individual game results, set scores, and player season records
 *
 * Player identity linking (ClickTtIdBackfillJob) is a separate, season-independent concern
 * and must have run before this so ClickTTMatchScraper.upsertPlayer can resolve person IDs.
 */
class ClickTTSeasonScraper(
    private val groupScraper: ClickTTGroupScraper,
    private val matchScraper: ClickTTMatchScraper,
) {
    suspend fun run(
        season: String = "2026/2027",
        federations: Collection<String>? = null,
    ) {
        if (federations != null) groupScraper.run(season, federations) else groupScraper.run(season)
        matchScraper.run()
    }

    companion object {
        fun create(): ClickTTSeasonScraper {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTTSeasonScraper(
                groupScraper = ClickTTGroupScraper(client, parser),
                matchScraper = ClickTTMatchScraper(client, parser),
            )
        }
    }
}
