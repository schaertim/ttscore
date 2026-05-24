package com.ttfeed.scraper.clicktt.model

data class ClickTTGame(
    val date: String,
    val competition: String,
    val opponent: String,
    val opponentElo: Int?,
    val eloDelta: Double?,
    val isWin: Boolean,
)

data class ClickTTPlayerPortrait(
    val personId: Int,
    val currentElo: Int?,
    val games: List<ClickTTGame>,
)

data class ClickTTClubMember(
    val licence: String,
    val personId: Int,
    /** Raw click-tt format: "Lastname, Firstname" */
    val fullName: String,
    /** "MALE" or "FEMALE" */
    val sex: String,
    /** STT age-category: "Aktive", "O40", "U19", … */
    val serie: String?,
    /** ISO 3-letter country code: "SUI", "GER", … */
    val nationality: String?,
)

data class ClickTTClubPage(
    val clubName: String?,
    val members: List<ClickTTClubMember>,
)
