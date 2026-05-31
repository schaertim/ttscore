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

    /** Full historical backfill: groups → match details + players → licences (all seasons 1989–present) */
    suspend fun run() {
        runGroupScraper()
        runMatchScraper()
        runLicenceScraper()
    }

    /**
     * Single-season backfill — useful for testing or catching up a specific season.
     * [federations] optionally restricts the group scrape to a subset (e.g. listOf("MTTV"));
     * the match scraper is DB-driven so it naturally only processes whatever groups were inserted.
     */
    suspend fun runForSeason(
        season: String,
        federations: Collection<String>? = null,
    ) {
        groupScraper.run(listOf(season), federations)
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
