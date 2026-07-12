package com.ttscore.scraper.knob

/**
 * Last season knob.ch owns — "2024/2025". From "2025/2026" onward, click-tt.ch is the source
 * (see ClickTTSeasonScraper / SeasonSyncJob). Single source of truth for this boundary so knob
 * scrapes never drift into click-tt's range.
 */
const val KNOB_LAST_SEASON_YEAR = 2024

/**
 * Maps league names to their knob.ch rvid parameter.
 * STT has no rvid (null) — it is the default national league.
 * Used by both GroupScraper and MatchScraper.
 */
val FEDERATION_RVIDS: Map<String, Int?> =
    mapOf(
        "STT" to null,
        "AGTT" to 1,
        "ANJTT" to 2,
        "ATTT" to 3,
        "AVVF" to 4,
        "MTTV" to 5,
        "NWTTV" to 6,
        "OTTV" to 7,
        "TTVI" to 8,
    )
