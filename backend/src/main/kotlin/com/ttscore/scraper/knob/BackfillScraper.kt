package com.ttscore.scraper.knob

import org.slf4j.LoggerFactory

class BackfillScraper(client: KnobClient, parser: KnobParser) {
    companion object {
        fun create(): BackfillScraper {
            val client = KnobClient()
            val parser = KnobParser()
            return BackfillScraper(client, parser)
        }
    }

    private val logger = LoggerFactory.getLogger(BackfillScraper::class.java)

    private val groupScraper = GroupScraper(client, parser)
    private val matchScraper = MatchScraper(client, parser)
    private val classScraper = KnobPlayerClassScraper(client, parser)
    private val licenceScraper = OverallPlayerScraper(client, parser)

    /** Full historical backfill: groups → match details + players → classes → licences (1989–present) */
    suspend fun run() {
        runGroupScraper()
        runMatchScraper()
        runClassScraper()
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
        classScraper.run()
        licenceScraper.run()
    }

    /**
     * Multi-season backfill scoped to [fromYear]..[toYear] (defaults to [KNOB_LAST_SEASON_YEAR],
     * knob's last owned season) — a quick correctness/speed test of a small season window before
     * committing to the full 1989 run. Unlike [runForSeason]'s single season, the group/match
     * scrape here can span multiple years, but the licence pass always covers the full
     * 1989-present range regardless of [fromYear] — see [OverallPlayerScraper.run] for why
     * narrowing it silently breaks click-tt player matching.
     *
     * [toYear] is clamped to [KNOB_LAST_SEASON_YEAR] regardless of what's passed in — knob and
     * click-tt own a strict, non-overlapping split of seasons (see [KNOB_LAST_SEASON_YEAR]), and
     * this scraper must never reach into click-tt's range.
     */
    suspend fun runFromYear(
        fromYear: Int,
        toYear: Int = KNOB_LAST_SEASON_YEAR,
    ) {
        val clampedToYear = toYear.coerceAtMost(KNOB_LAST_SEASON_YEAR)
        if (toYear > KNOB_LAST_SEASON_YEAR) {
            logger.warn(
                "runFromYear: requested toYear=$toYear exceeds knob's last owned season " +
                    "($KNOB_LAST_SEASON_YEAR/${KNOB_LAST_SEASON_YEAR + 1}) — clamping to " +
                    "$clampedToYear. Seasons after that belong to click-tt.",
            )
        }
        groupScraper.run(generateSeasons(fromYear, clampedToYear))
        matchScraper.run()
        classScraper.run()
        licenceScraper.run()
    }

    /** Scrapes all group structure, teams, players and matches from knob.ch */
    suspend fun runGroupScraper() = groupScraper.run()

    /** Scrapes individual game results and upserts players encountered in match details */
    suspend fun runMatchScraper() = matchScraper.run()

    /** Fills classification per player from their (men's-ladder) profile page — see the scraper. */
    suspend fun runClassScraper() = classScraper.run()

    /** Resolves real STT licence numbers via the overall player registry */
    suspend fun runLicenceScraper() = licenceScraper.run()
}
