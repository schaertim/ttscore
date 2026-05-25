package com.ttscore.scraper.knob

class BackfillScraper(client: KnobClient, parser: KnobParser) {
    companion object {
        fun create(): BackfillScraper {
            val client = KnobClient()
            val parser = KnobParser()
            return BackfillScraper(client, parser)
        }
    }

    private val groupScraper = GroupScraper(client, parser)
    private val matchScraper = MatchScraper(client, parser)
    private val licenceScraper = OverallPlayerScraper(client, parser)

    /** Full historical backfill: groups â†’ match details + players â†’ licences (all seasons 1989â€“present) */
    suspend fun run() {
        runGroupScraper()
        runMatchScraper()
        runLicenceScraper()
    }

    /** Single-season backfill â€” useful for testing or catching up a specific season */
    suspend fun runForSeason(season: String) {
        groupScraper.run(listOf(season))
        matchScraper.run()
        licenceScraper.run()
    }

    /** Scrapes all group structure, teams, players and matches from knob.ch */
    suspend fun runGroupScraper() = groupScraper.run()

    /** Scrapes individual game results and upserts players encountered in match details */
    suspend fun runMatchScraper() = matchScraper.run()

    /** Resolves real STT licence numbers via the overall player registry */
    suspend fun runLicenceScraper() = licenceScraper.run()
}
