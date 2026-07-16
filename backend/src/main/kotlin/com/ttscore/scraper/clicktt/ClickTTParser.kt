package com.ttscore.scraper.clicktt

import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.clicktt.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ClickTTParser {
    /**
     * Returns all player-game season URLs from a portrait page:
     * - Cup seasons: the "Saisons" column's mode=CHAMPIONSHIP link whose season param says "Cup"
     * - Tournament seasons: the separate "Turniersaison" column's mode=TOURNAMENT link
     *
     * Both are real hrefs already present on the page — do not derive one from the other. A
     * mode=CHAMPIONSHIP link's season param is "Cup 2025/26"; naively swapping only the mode to
     * TOURNAMENT keeps the "Cup" season value and produces a URL matching no real page (the
     * genuine tournament link's season param has no "Cup" prefix), so every derived "tournament"
     * fetch came back empty.
     * Previous-season pages that require login will return empty results when parsed — fine.
     */
    fun extractPlayerGameUrls(portraitHtml: String): List<String> {
        val doc = Jsoup.parse(portraitHtml)
        val base = "https://www.click-tt.ch"

        val cupUrls =
            doc.select("a[href*='mode=CHAMPIONSHIP']")
                .map { it.attr("href") }
                .filter { href -> (extractParam(href, "season") ?: "").contains("Cup", ignoreCase = true) }

        val tournamentUrls = doc.select("a[href*='mode=TOURNAMENT']").map { it.attr("href") }

        return (cupUrls + tournamentUrls)
            .map { if (it.startsWith("http")) it else "$base$it" }
            .distinct()
    }

    /**
     * Parses a tournament season portrait page (mode=TOURNAMENT).
     *
     * Row layout within each result-set table:
     *   tr.table-split           — tournament name header, skip
     *   tr > td[colspan=13]      — competition category header, skip
     *   tr > th                  — column headers (Datum / Gegner / win / Sätze), skip
     *   tr (game row)            — td[0]=date  td[1]=opponent link  td[2]=win icon  td[3]=<b>sets</b>
     *
     * Sets are in "player:opponent" format (e.g. "3:1" means the synced player won 3 sets).
     */
    fun parseTournamentPage(html: String): List<ParsedTournamentGame> {
        val doc = Jsoup.parse(html)
        val games = mutableListOf<ParsedTournamentGame>()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        for (table in doc.select("table.result-set")) {
            // Tournament name lives in a `tr.table-split` header and applies to every game row
            // until the next one. Carried forward as the fallback competition label.
            var currentTournament: String? = null
            for (row in table.select("tbody tr")) {
                if (row.hasClass("table-split")) {
                    currentTournament = row.selectFirst("h2")?.text()?.trim()?.takeIf { it.isNotBlank() }
                    continue
                }
                val cells = row.select("td")
                if (cells.isEmpty()) continue
                if (row.selectFirst("th") != null) continue
                if (cells[0].attr("colspan").isNotBlank()) continue

                val dateText = cells.getOrNull(0)?.text()?.trim() ?: continue
                val date = runCatching {
                    java.time.LocalDateTime.parse(dateText, dateFormatter).toLocalDate()
                }.getOrNull() ?: continue

                val opponentLink = cells.getOrNull(1)?.selectFirst("a[href*='person=']") ?: continue
                val opponentPersonId = extractParam(opponentLink.attr("href"), "person")?.toIntOrNull()
                val opponentName = opponentLink.text().trim().takeIf { it.isNotBlank() } ?: continue

                val isWin = cells.getOrNull(2)?.selectFirst("img[title=Sieg]") != null

                val setsText = cells.getOrNull(3)?.selectFirst("b")?.text()?.trim() ?: continue
                val homeSets = setsText.substringBefore(":").trim().toIntOrNull() ?: continue
                val awaySets = setsText.substringAfter(":").trim().toIntOrNull() ?: continue

                val sets = mutableListOf<ParsedClickTTSet>()
                for (setIndex in 0..6) {
                    val cell = cells.getOrNull(4 + setIndex) ?: break
                    val text = cell.text().trim()
                    if (text.isBlank() || !text.contains(":")) continue
                    val hp = text.substringBefore(":").trim().toIntOrNull() ?: continue
                    val ap = text.substringAfter(":").trim().toIntOrNull() ?: continue
                    sets += ParsedClickTTSet(setNumber = setIndex + 1, homePoints = hp, awayPoints = ap)
                }

                games += ParsedTournamentGame(
                    date = date,
                    opponentPersonId = opponentPersonId,
                    opponentName = opponentName,
                    isWin = isWin,
                    homeSets = homeSets,
                    awaySets = awaySets,
                    sets = sets,
                    competition = currentTournament,
                )
            }
        }
        return games
    }

    /**
     * Parses a cup season page (mode=CHAMPIONSHIP with a Cup season).
     *
     * Row layout (singles, 13 cells):
     *   cells[0] = date "DD.MM.YYYY" — or &nbsp; on continuation rows (carry forward)
     *   cells[1] = position code ("U-B", "V-A", …)
     *   cells[2] = opponent link with person=
     *   cells[3] = opponent klass
     *   cells[4] = win icon
     *   cells[5] = total sets <b>3:2</b>
     *   cells[6..10] = individual set scores (may be used later)
     *
     * Doubles tables contain a "Partner" column header — skip those tables entirely.
     */
    fun parseCupPage(html: String): List<ParsedTournamentGame> {
        val doc = Jsoup.parse(html)
        val games = mutableListOf<ParsedTournamentGame>()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

        for (table in doc.select("table.result-set")) {
            // Skip doubles tables — they have a "Partner" column header
            if (table.select("th").any { it.text().contains("Partner", ignoreCase = true) }) continue

            var currentDate: java.time.LocalDate? = null
            // Each cup round is introduced by an <h2> header (e.g. "1. Hauptrunde Zone 3"),
            // carried forward as the fallback competition label for the rows beneath it.
            var currentRound: String? = null

            for (row in table.select("tbody tr")) {
                val h2 = row.selectFirst("h2")
                if (h2 != null) {
                    currentRound =
                        h2.text().trim()
                            .replace(Regex("""\s*\(\s*\)\s*$"""), "")
                            .trim()
                            .takeIf { it.isNotBlank() }
                    continue
                }
                val cells = row.select("td")
                if (cells.size < 6) continue

                // Update date only when the first cell has real content (not &nbsp; continuation)
                val dateText = cells[0].text().trim()
                if (dateText.isNotBlank()) {
                    currentDate = runCatching {
                        java.time.LocalDate.parse(dateText, dateFormatter)
                    }.getOrNull() ?: currentDate
                }

                val date = currentDate ?: continue

                val opponentLink = cells.getOrNull(2)?.selectFirst("a[href*='person=']") ?: continue
                val opponentPersonId = extractParam(opponentLink.attr("href"), "person")?.toIntOrNull()
                val opponentName = opponentLink.text().trim().takeIf { it.isNotBlank() } ?: continue

                val isWin = cells.getOrNull(4)?.selectFirst("img[title=Sieg]") != null

                val setsText = cells.getOrNull(5)?.selectFirst("b")?.text()?.trim() ?: continue
                val homeSets = setsText.substringBefore(":").trim().toIntOrNull() ?: continue
                val awaySets = setsText.substringAfter(":").trim().toIntOrNull() ?: continue

                val sets = mutableListOf<ParsedClickTTSet>()
                for (setIndex in 0..4) {
                    val cell = cells.getOrNull(6 + setIndex) ?: break
                    val text = cell.text().trim()
                    if (text.isBlank() || !text.contains(":")) continue
                    val hp = text.substringBefore(":").trim().toIntOrNull() ?: continue
                    val ap = text.substringAfter(":").trim().toIntOrNull() ?: continue
                    sets += ParsedClickTTSet(setNumber = setIndex + 1, homePoints = hp, awayPoints = ap)
                }

                games += ParsedTournamentGame(
                    date = date,
                    opponentPersonId = opponentPersonId,
                    opponentName = opponentName,
                    isWin = isWin,
                    homeSets = homeSets,
                    awaySets = awaySets,
                    sets = sets,
                    competition = currentRound,
                )
            }
        }
        return games
    }

    /**
     * Extracts the URL of the Elo-Protokoll (or Ergebnishistorie) tab from a player portrait page.
     * Searches within the content-tabs nav to avoid matching the mobile menu's '#' placeholder links.
     */
    fun extractEloProtokollUrl(portraitHtml: String): String? {
        val doc = Jsoup.parse(portraitHtml)
        val link =
            doc.select(
                "ul.content-tabs a:contains(Elo-Protokoll), ul.content-tabs a:contains(Ergebnishistorie)",
            ).firstOrNull()
        return link?.attr("href")
    }

    /**
     * Extracts the player's current classification from the portrait info table
     * (`<td><b>Klassierung</b></td><td>B13</td>`). For women the value is "male / female"
     * (e.g. "C7 / B12") — we keep the first token. Used only as a current-half fallback.
     */
    fun parseCurrentClass(portraitHtml: String): String? {
        val doc = Jsoup.parse(portraitHtml)
        val value =
            doc.selectFirst("td:has(b:containsOwn(Klassierung))")
                ?.nextElementSibling()?.text()?.trim()
                ?: return null
        return value.substringBefore("/").trim().takeIf { it.isNotBlank() }
    }

    fun parsePlayerPortrait(
        portraitHtml: String,
        eloHtml: String?,
        personId: Int,
    ): ClickTTPlayerPortrait {
        val portraitDoc = Jsoup.parse(portraitHtml)
        val eloDoc = eloHtml?.let { Jsoup.parse(it) }

        // Prefer ELO from the protokoll page; fall back to the portrait page
        val currentElo = eloDoc?.let { parseCurrentElo(it) } ?: parseCurrentElo(portraitDoc)

        // Game history with ELO deltas only exists on the protokoll page
        val games = eloDoc?.let { parseGames(it) } ?: emptyList()

        return ClickTTPlayerPortrait(personId = personId, currentElo = currentElo, games = games)
    }

    /**
     * Parses a club members page for one gender tab.
     *
     * Table columns: [Klassierung | Name | Lizenz-Nr. | Serie | NationalitÃ¤t]
     *
     * Names are returned in raw click-tt format ("Lastname, Firstname") so the
     * storage layer can apply [clickTtNameToDb] uniformly.
     */
    fun parseClubPage(
        html: String,
        gender: String = "MALE",
    ): ClickTTClubPage {
        val doc = Jsoup.parse(html)
        val members = mutableListOf<ClickTTClubMember>()

        for (row in doc.select("table.result-set tbody tr")) {
            val cells = row.select("td")
            if (cells.size < 3) continue

            val link = cells[1].select("a[href*='person=']").firstOrNull() ?: continue
            val personId = link.attr("href").let { extractParam(it, "person") }?.toIntOrNull() ?: continue
            // Raw "Lastname, Firstname" — no normalisation here; storage layer converts format
            val fullName = link.text().trim().takeIf { it.isNotBlank() } ?: continue
            val licence = cells[2].text().trim().takeIf { it.isNotBlank() } ?: continue
            val serie = cells.getOrNull(3)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val nationality = cells.getOrNull(4)?.text()?.trim()?.takeIf { it.isNotBlank() }

            members.add(
                ClickTTClubMember(
                    licence = licence,
                    personId = personId,
                    fullName = fullName,
                    sex = gender,
                    serie = serie,
                    nationality = nationality,
                ),
            )
        }

        return ClickTTClubPage(clubName = parseClubName(doc), members = members)
    }

    /**
     * Parses a regional club-search page (clubSearch?searchPattern=CH.0N) and returns the
     * click-tt club IDs it lists. Each club is a link of the form `clubInfoDisplay?club=NNNNN`.
     * The federation's own pseudo-club link is included too but is harmless — it simply won't
     * match any of our clubs during the backfill.
     */
    fun parseClubSearchPage(html: String): List<Int> =
        Jsoup.parse(html)
            .select("a[href*='clubInfoDisplay']")
            .mapNotNull { extractParam(it.attr("href"), "club")?.toIntOrNull() }
            .distinct()

    /**
     * Reads the ranking date pre-selected on the Elo-Filter form (`<option selected>`), which is
     * the current monthly ranking. Returns null if the form shape changed.
     */
    fun parseEloFilterRankingDate(html: String): String? =
        Jsoup.parse(html)
            .selectFirst("select[name=rankingDate] option[selected]")
            ?.attr("value")?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Returns the relative detail href (`eloFilter?…&ranking=NNN`) for the single result row of a
     * licence search, or null when the search returned no player (empty results table).
     */
    fun parseEloFilterResultHref(html: String): String? =
        Jsoup.parse(html)
            .selectFirst("table.result-set tbody tr td a[href*='ranking=']")
            ?.attr("href")?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Extracts the click-tt person ID from an Elo-Filter detail page — it links to the player's
     * portrait (`playerPortrait?…&person=NNN`), the only place the person ID is exposed.
     */
    fun parseEloFilterPersonId(html: String): Int? =
        Jsoup.parse(html)
            .selectFirst("a[href*='playerPortrait'][href*='person=']")
            ?.let { extractParam(it.attr("href"), "person") }
            ?.toIntOrNull()

    private fun parseClubName(doc: Document): String? =
        // The h1 contains "ClubName\nLizenzierte Spieler des Vereins" — strip the subtitle regardless
        // of whether a <br> is present between them.
        doc.selectFirst("div.content-section h1, h1.page-title, h1")
            ?.text()?.trim()
            ?.substringBefore("Lizenzierte Spieler")?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun parseCurrentElo(doc: Document): Int? {
        // eloFilter page: info table has <td><b>Elo-Wert</b></td><td class="right">1016</td>
        val eloLabelCell = doc.selectFirst("td:has(b:containsOwn(Elo-Wert))")
        if (eloLabelCell != null) {
            eloLabelCell.nextElementSibling()?.text()?.trim()?.toIntOrNull()?.let { return it }
        }

        // Portrait page: Klassierung shows a classification string like "C7 / B12" — no numeric ELO here.
        // Some older pages may show "A21 (1234)", so still try parsing as a fallback.
        val klassCell =
            doc.select("td:containsOwn(Klassierung)").next("td").firstOrNull()
                ?: doc.select("div:containsOwn(Klassierung)").next("div").firstOrNull()
        val text = klassCell?.text()?.trim() ?: return null
        return Regex("\\b([A-Z]\\d{1,2})\\s*\\((\\d+)\\)").find(text)?.groupValues?.last()?.toIntOrNull()
    }

    private fun parseGames(doc: Document): List<ClickTTGame> {
        val games = mutableListOf<ClickTTGame>()

        // Find all result tables that have a "Spieler" / "Gegner" / "Opponent" column header.
        // "Begegnung" (competition column) also uniquely identifies the game history tables.
        val tables =
            doc.select("table.result-set").filter { table ->
                table.select("th").any { th ->
                    val text = th.text()
                    text.contains("Spieler", ignoreCase = true) ||
                        text.contains("Gegner", ignoreCase = true) ||
                        text.contains("Opponent", ignoreCase = true) ||
                        text.contains("Begegnung", ignoreCase = true)
                }
            }

        for (table in tables) {
            for (row in table.select("tbody tr")) {
                val cells = row.select("td")
                if (cells.size < 7) continue

                val date = cells[0].text().trim()
                val competition = cells[1].text().trim()
                val playerMonthlyElo = cells[2].text().trim().toIntOrNull()
                val opponent = cells[3].text().trim()
                val opponentElo = cells[4].text().trim().toIntOrNull()
                val isWin = cells[5].select("img[title=Sieg], img[alt=Sieg]").isNotEmpty()
                // ELO delta can be empty for preview games not yet rated
                val eloDelta = cells[6].text().trim().replace(",", ".").toDoubleOrNull()

                games.add(
                    ClickTTGame(
                        date = date,
                        competition = competition,
                        opponent = opponent,
                        opponentElo = opponentElo,
                        eloDelta = eloDelta,
                        isWin = isWin,
                        playerMonthlyElo = playerMonthlyElo,
                    ),
                )
            }
        }
        return games
    }

    // -------------------------------------------------------------------------
    // Season scraping — league page, group standings, match schedule, match detail
    // -------------------------------------------------------------------------

    /**
     * Parses the league overview page and returns one entry per group link.
     * The category (Herren / Damen / Senioren O40 / …) is taken from the nearest
     * preceding <h2> in document order.
     */
    fun parseLeaguePage(
        html: String,
        championship: String,
    ): List<ParsedClickTTGroup> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<ParsedClickTTGroup>()
        var currentCategory = ""

        // Walk all h2 headings and group links in document order so we can carry
        // the category forward without needing complex ancestor traversal.
        for (el in doc.select("h2, a[href*='groupPage']")) {
            when (el.tagName()) {
                "h2" -> currentCategory = el.text().trim()
                "a" -> {
                    val href = el.attr("href")
                    val groupId = extractParam(href, "group")?.toIntOrNull() ?: continue
                    val divisionName = el.text().trim().takeIf { it.isNotBlank() } ?: continue
                    result +=
                        ParsedClickTTGroup(
                            groupId = groupId,
                            championship = championship,
                            divisionName = divisionName,
                            category = currentCategory,
                        )
                }
            }
        }
        return result
    }

    /**
     * Parses the standings table from a click-tt group page.
     * Column layout: [zone-icon | rank | team-link | played | W | D | L | games "F:A" | +/- | pts]
     */
    fun parseGroupStandings(html: String): List<ParsedClickTTStanding> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<ParsedClickTTStanding>()

        for (row in doc.select("table.result-set tbody tr")) {
            val cells = row.select("td")
            if (cells.size < 9) continue

            val teamLink = cells[2].selectFirst("a[href*='teamPortrait']") ?: continue
            val teamTableId = extractParam(teamLink.attr("href"), "teamtable")?.toIntOrNull() ?: continue
            val teamName = teamLink.text().trim().takeIf { it.isNotBlank() } ?: continue
            val position = cells[1].text().trim().trimEnd('.').toIntOrNull() ?: continue

            val played = cells[3].text().trim().toIntOrNull() ?: 0
            val won = cells[4].text().trim().toIntOrNull() ?: 0
            val drawn = cells[5].text().trim().toIntOrNull() ?: 0
            val lost = cells[6].text().trim().toIntOrNull() ?: 0

            // Games cell: "48:36"
            val gamesText = cells[7].text().trim()
            val gamesFor = gamesText.substringBefore(":").trim().toIntOrNull() ?: 0
            val gamesAgainst = gamesText.substringAfter(":").trim().toIntOrNull() ?: 0

            // Points cell is cells[9] and contains "W:L" format e.g. "18:6" — take the left side
            val points =
                cells[9].text().trim().substringBefore(":").trim().toIntOrNull()
                    ?: cells[8].text().trim().substringBefore(":").trim().toIntOrNull()
                    ?: 0

            // click-tt uses "Relegation" as the alt text for the promotion (upward) zone arrow —
            // confusing naming on their part. "Absteiger" marks the actual relegation zone.
            val isPromotion = cells[0].selectFirst("img[alt='Relegation']") != null
            val isRelegation = cells[0].selectFirst("img[alt='Absteiger']") != null

            result +=
                ParsedClickTTStanding(
                    teamName = teamName,
                    teamTableId = teamTableId,
                    position = position,
                    played = played,
                    won = won,
                    drawn = drawn,
                    lost = lost,
                    gamesFor = gamesFor,
                    gamesAgainst = gamesAgainst,
                    points = points,
                    isPromotion = isPromotion,
                    isRelegation = isRelegation,
                )
        }
        return result
    }

    /**
     * Parses the full match schedule (Spielplan) from a click-tt group page
     * requested with displayDetail=meetings.
     *
     * The schedule table has 13 columns (0-indexed):
     *   0  day name "Sa."       — OR td.tabelle-rowspan on continuation rows
     *   1  date "11.10.2025"    — OR td.tabelle-rowspan on continuation rows
     *   2  time "14:00"         (nowrap)
     *   3  Spiellokal link "(2)"(center+nowrap)
     *   4  round number "1"
     *   5  home team name       (nowrap)
     *   6  home winner icon
     *   7  away team name       (nowrap)
     *   8  away winner icon
     *   9  score / meeting link (center+nowrap) — e.g. "6:2" when completed
     *   10+ additional cells (unused)
     *
     * Date rows introduce a new date/time context that carries forward to all
     * subsequent rows in the same date block (marked by td.tabelle-rowspan placeholders).
     */
    fun parseMatchSchedule(html: String): List<ParsedClickTTMatch> {
        val doc = Jsoup.parse(html)
        val result = mutableListOf<ParsedClickTTMatch>()
        var currentDate = ""
        var currentTime: String? = null

        for (row in doc.select("table.result-set tbody tr")) {
            val cells = row.select("td")
            if (cells.size < 10) continue

            // A row NOT starting with td.tabelle-rowspan sets a new date/time context.
            if (!cells[0].hasClass("tabelle-rowspan")) {
                currentDate = cells[1].text().trim()
                val timeText = cells[2].text().trim().substringBefore("\u00a0").substringBefore(" ").trim()
                if (timeText.matches(Regex("""\d{2}:\d{2}"""))) currentTime = timeText
            }

            val homeTeamName = cells[5].text().trim().takeIf { it.isNotBlank() } ?: continue
            val awayTeamName = cells[7].text().trim().takeIf { it.isNotBlank() } ?: continue
            val round = cells[4].text().trim().takeIf { it.isNotBlank() }

            val scoreLink = cells[9].selectFirst("a[href*='groupMeetingReport']")
            val meetingId = scoreLink?.attr("href")?.let { extractParam(it, "meeting")?.toIntOrNull() }
            val scoreText = scoreLink?.text()?.trim()
            val homeScore = scoreText?.substringBefore(":")?.trim()?.toIntOrNull()
            val awayScore = scoreText?.substringAfter(":")?.trim()?.toIntOrNull()
            val status = if (meetingId != null) MatchStatus.COMPLETED else MatchStatus.SCHEDULED

            if (currentDate.isEmpty()) continue // Guard against malformed HTML

            result +=
                ParsedClickTTMatch(
                    meetingId = meetingId,
                    date = currentDate,
                    time = currentTime,
                    round = round,
                    homeTeamName = homeTeamName,
                    awayTeamName = awayTeamName,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    status = status,
                )
        }
        return result
    }

    /**
     * Parses individual game results from a click-tt match detail page.
     *
     * Row layout: [game-label | home-player | home-klass | away-player | away-klass |
     *              set1 | set2 | set3 | set4 | set5 | total-sets | running-score]
     *
     * Singles have one <a href*="person="> per player cell.
     * Doubles have TWO — unlike knob.ch which uses a single combined link.
     */
    fun parseClickTTMatchDetail(
        html: String,
        meetingId: Int,
    ): ParsedClickTTMatchDetail {
        val doc = Jsoup.parse(html)
        val games = mutableListOf<ParsedClickTTGame>()
        var order = 0

        for (row in doc.select("table.result-set tbody tr")) {
            val cells = row.select("td")
            // Need at least: label + home-player + home-klass + away-player + away-klass + 1 set + total
            if (cells.size < 7) continue

            val label = cells[0].text().trim()
            // Skip sub-total / total rows (they have no recognisable game label)
            if (label.isBlank() || label.equals("Total", ignoreCase = true)) continue

            val gameType = if (label.contains("Doppel", ignoreCase = true)) GameType.DOUBLES else GameType.SINGLES
            order++

            // --- Player extraction ---
            val homeLinks = cells[1].select("a[href*='person=']")
            val awayLinks = cells[3].select("a[href*='person=']")

            val homePersonId = homeLinks.getOrNull(0)?.attr("href")?.let { extractParam(it, "person")?.toIntOrNull() }
            val homeName = homeLinks.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val homePersonId2 = homeLinks.getOrNull(1)?.attr("href")?.let { extractParam(it, "person")?.toIntOrNull() }
            val homeName2 = homeLinks.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }

            val awayPersonId = awayLinks.getOrNull(0)?.attr("href")?.let { extractParam(it, "person")?.toIntOrNull() }
            val awayName = awayLinks.getOrNull(0)?.text()?.trim()?.takeIf { it.isNotBlank() }
            val awayPersonId2 = awayLinks.getOrNull(1)?.attr("href")?.let { extractParam(it, "person")?.toIntOrNull() }
            val awayName2 = awayLinks.getOrNull(1)?.text()?.trim()?.takeIf { it.isNotBlank() }

            // Klass is in cells[2] (home) and cells[4] (away).
            // For singles: plain text e.g. "A19".
            // For doubles: two values separated by <br> e.g. "A21\nA20" — use textNodes() to
            // split them individually rather than .text() which joins with a space ("A21 A20").
            val homeKlassNodes = cells[2].textNodes().map { it.text().trim() }.filter { it.isNotBlank() }
            val homeKlass = homeKlassNodes.getOrNull(0)
            val homeKlass2 = homeKlassNodes.getOrNull(1)
            val awayKlassNodes = cells[4].textNodes().map { it.text().trim() }.filter { it.isNotBlank() }
            val awayKlass = awayKlassNodes.getOrNull(0)
            val awayKlass2 = awayKlassNodes.getOrNull(1)

            // --- Set scores (cells 5..9) ---
            val sets = mutableListOf<ParsedClickTTSet>()
            for (setIndex in 0..4) {
                val cell = cells.getOrNull(5 + setIndex) ?: break
                val text = cell.text().trim()
                if (text.isBlank() || !text.contains(":")) continue
                val hp = text.substringBefore(":").trim().toIntOrNull() ?: continue
                val ap = text.substringAfter(":").trim().toIntOrNull() ?: continue
                sets += ParsedClickTTSet(setNumber = setIndex + 1, homePoints = hp, awayPoints = ap)
            }

            // Total sets from the cell after the 5 set cells (index 10)
            val totalCell = cells.getOrNull(10)
            val totalText = totalCell?.text()?.trim() ?: ""
            val homeSets = totalText.substringBefore(":").trim().toIntOrNull()
            val awaySets = totalText.substringAfter(":").trim().toIntOrNull()

            val result =
                when {
                    homeSets != null && awaySets != null && homeSets > awaySets -> GameResult.HOME
                    homeSets != null && awaySets != null && awaySets > homeSets -> GameResult.AWAY
                    else -> GameResult.NOT_PLAYED
                }

            games +=
                ParsedClickTTGame(
                    orderInMatch = order,
                    gameType = gameType,
                    homePersonId = homePersonId,
                    homeName = homeName,
                    homeKlass = homeKlass,
                    homePersonId2 = homePersonId2,
                    homeName2 = homeName2,
                    homeKlass2 = homeKlass2,
                    awayPersonId = awayPersonId,
                    awayName = awayName,
                    awayKlass = awayKlass,
                    awayPersonId2 = awayPersonId2,
                    awayName2 = awayName2,
                    awayKlass2 = awayKlass2,
                    homeSets = homeSets,
                    awaySets = awaySets,
                    result = result,
                    sets = sets,
                )
        }

        return ParsedClickTTMatchDetail(meetingId = meetingId, games = games)
    }

    private fun extractParam(
        url: String,
        key: String,
    ): String? =
        url.split("?", "&")
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
}
