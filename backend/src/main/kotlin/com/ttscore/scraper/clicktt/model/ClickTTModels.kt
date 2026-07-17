package com.ttscore.scraper.clicktt.model

data class ClickTTGame(
    val date: String,
    val competition: String,
    val opponent: String,
    val opponentElo: Int?,
    val eloDelta: Double?,
    val isWin: Boolean,
    val playerMonthlyElo: Int?,
)

data class ClickTTPlayerPortrait(
    val personId: Int,
    val currentElo: Int?,
    val games: List<ClickTTGame>,
)

/**
 * One row of a click-tt Elo-Filter search result (`table.result-set`). A licence search returns at
 * most one row; a name search can return several namesakes.
 */
data class EloFilterResultRow(
    /** Raw click-tt format: "Lastname, Firstname" */
    val name: String,
    /** License-No. column — may be blank/absent. */
    val licence: String?,
    /** Club column — plain text, may be blank for lapsed/edge-case players. */
    val club: String?,
    /** Agecategory column ("Active", "O40", …) → our `category`. */
    val category: String?,
    /**
     * Relative detail href (`eloFilter?…&ranking=NNN`). Its `ranking=` id is a different id space
     * from the click-tt person id — the href must be followed to reach the person/club ids.
     */
    val detailHref: String,
)

data class ParsedTournamentGame(
    val date: java.time.LocalDate,
    val opponentPersonId: Int?,
    val opponentName: String,
    val isWin: Boolean,
    val homeSets: Int,
    val awaySets: Int,
    val sets: List<ParsedClickTTSet> = emptyList(),
    /** Tournament name (TOURNAMENT page) or cup round (Cup page). Used as a fallback competition
     *  label until the Elo-Protokoll overwrites it for rated games. */
    val competition: String? = null,
)
