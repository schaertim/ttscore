package com.ttscore.scraper.knob

import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.knob.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.Normalizer

class KnobParser {
    // -------------------------------------------------------------------------
    // Gruppe page — identifies which division/league a gruppe belongs to
    // -------------------------------------------------------------------------

    /**
     * Parses a gruppe page and returns structural metadata about it.
     * Returns null if the page does not correspond to the requested gruppe
     * (knob.ch redirects invalid gruppe IDs to a default page).
     *
     * [seasonYear] is used to determine league assignment — before 2011 all
     * groups were STT regardless of nav block position.
     */
    fun parseGruppePage(
        html: String,
        requestedGruppe: Int,
        seasonYear: Int,
    ): GruppePageResult? {
        val doc = Jsoup.parse(html)

        // Find the red (active) nav item and verify it matches the requested gruppe
        var selectedGruppe = -1
        var selectedBlockIndex = -1

        doc.select("ul#mainNav").forEachIndexed { blockIdx, navList ->
            navList.select("li a").forEach { link ->
                if (link.selectFirst("font[color=red]") != null) {
                    val gruppe =
                        extractParam(link.attr("href"), "gruppe")?.toIntOrNull()
                            ?: return@forEach
                    selectedGruppe = gruppe
                    selectedBlockIndex = blockIdx
                }
            }
        }

        if (selectedGruppe == -1 || selectedGruppe != requestedGruppe) return null

        // Content header format: "NLB / Gruppe 2" or just "NLA"
        val contentHeader =
            doc.select("table.a01Bar td.playerStatTitle")
                .firstOrNull()?.text()?.trim()
                ?: return null

        val (divisionName, groupName) =
            if (contentHeader.contains("/")) {
                val division = contentHeader.substringBefore("/").trim()
                val groupPart = contentHeader.substringAfter("/").trim()
                // Numeric group parts get a prefix: "2" → "Gruppe 2"
                val group = if (groupPart.toIntOrNull() != null) "Gruppe $groupPart" else groupPart
                division to group
            } else {
                contentHeader to "Gruppe 1"
            }

        if (divisionName.isBlank()) return null

        return GruppePageResult(
            gruppeId = selectedGruppe,
            leagueName = resolveLeague(doc, selectedBlockIndex, seasonYear),
            divisionName = divisionName,
            groupName = groupName,
        )
    }

    // -------------------------------------------------------------------------
    // Division page — teams, players, matches
    // -------------------------------------------------------------------------

    fun parseDivisionPage(html: String): ParsedDivisionPage {
        val doc = Jsoup.parse(html)
        val standings = parseStandings(html)
        return ParsedDivisionPage(
            teams = parseTeams(doc),
            players = parsePlayers(doc),
            matches = parseMatches(doc),
            standings = standings.standings,
            promotionSpots = standings.promotionSpots,
            relegationSpots = standings.relegationSpots,
        )
    }

    /** Column indices for a standings table body row, varying by layout (see [parseStandings]). */
    private data class StandingsColumns(
        val minCells: Int,
        val teamCol: Int,
        val playedCol: Int,
        val wonCol: Int,
        val drawnCol: Int,
        val lostCol: Int,
        val siegVerhCol: Int,
        val pointsCol: Int,
    )

    private val PRIMARY_STANDINGS_COLUMNS =
        StandingsColumns(
            minCells = 9, teamCol = 1, playedCol = 3, wonCol = 4,
            drawnCol = 5, lostCol = 6, siegVerhCol = 7, pointsCol = 9,
        )

    // Same columns, shifted one left — this layout has no spacer column between Team and Spiele,
    // and collapses the trailing per-season stats into a single padding td.
    private val ALT_STANDINGS_COLUMNS =
        StandingsColumns(
            minCells = 9, teamCol = 1, playedCol = 2, wonCol = 3,
            drawnCol = 4, lostCol = 5, siegVerhCol = 6, pointsCol = 8,
        )

    private fun parseStandings(html: String): ParsedStandingsPage {
        val doc = Jsoup.parse(html)

        // The standings table is normally the first pTitle table — it has the team ranking header.
        val primaryTable =
            doc.select("table.pTitle").firstOrNull {
                it.selectFirst("tr td:contains(Rang)") != null ||
                    it.select("tr.psauf, tr.psab, tr.psodd, tr.playerStats").isNotEmpty()
            }
        if (primaryTable != null) {
            val parsed = parseStandingsTable(primaryTable, PRIMARY_STANDINGS_COLUMNS)
            if (parsed.standings.isNotEmpty()) return parsed
        }

        // Fallback: some knob pages render the team ranking table with a mistyped class
        // ("test.pTitle" instead of "pTitle") and a slightly different column layout, so the
        // primary lookup above misses it entirely. Locate it via the "Team-Rangliste" header bar
        // that always precedes it, rather than the table's own (unreliable) class.
        val altTable =
            doc.select("table.a02Bar")
                .firstOrNull { it.text().contains("Team-Rangliste") }
                ?.nextElementSibling()
        if (altTable != null && altTable.selectFirst("tr td:contains(Rang)") != null) {
            val parsed = parseStandingsTable(altTable, ALT_STANDINGS_COLUMNS)
            if (parsed.standings.isNotEmpty()) return parsed
        }

        // Some groups genuinely have no standings table at all (cups/playoffs) — the caller
        // falls back to the participating-teams list in that case.
        return ParsedStandingsPage(emptyList(), 0, 0)
    }

    private fun parseStandingsTable(
        table: Element,
        cols: StandingsColumns,
    ): ParsedStandingsPage {
        val rows = table.select("tr")
        val standings = mutableListOf<ParsedStandingRow>()
        var promotionSpots = 0
        var relegationStart = Int.MAX_VALUE
        var position = 1

        for (row in rows) {
            // Promotion/relegation zone separators — normally a class on the <tr> itself, but one
            // known layout puts it on the row's single (colspan) <td> instead.
            if (hasZoneClass(row, "auf")) {
                promotionSpots = position - 1
                continue
            }
            if (hasZoneClass(row, "ab")) {
                relegationStart = position
                continue
            }

            val isStandingRow =
                row.hasClass("psauf") || row.hasClass("psab") ||
                    row.hasClass("psodd") || row.hasClass("playerStats")
            if (!isStandingRow) continue

            val cells = row.select("td")
            if (cells.size < cols.minCells) continue

            val teamLink = cells[cols.teamCol].selectFirst("a") ?: continue
            val knobTeamId = extractParam(teamLink.attr("href"), "teamid")?.toIntOrNull() ?: continue

            val played = cells[cols.playedCol].text().trim().toIntOrNull() ?: continue
            val won = cells[cols.wonCol].text().trim().toIntOrNull() ?: continue
            val drawn = cells[cols.drawnCol].text().trim().toIntOrNull() ?: continue
            val lost = cells[cols.lostCol].text().trim().toIntOrNull() ?: continue

            // SiegVerh column format: "105:35"
            val siegVerh = cells[cols.siegVerhCol].text().trim()
            val gamesFor = siegVerh.substringBefore(":").trim().toIntOrNull() ?: 0
            val gamesAgainst = siegVerh.substringAfter(":").trim().toIntOrNull() ?: 0

            // Points are in a bold td
            val points = cells[cols.pointsCol].text().trim().toIntOrNull() ?: continue

            standings.add(
                ParsedStandingRow(
                    position = position,
                    knobTeamId = knobTeamId,
                    played = played,
                    won = won,
                    drawn = drawn,
                    lost = lost,
                    gamesFor = gamesFor,
                    gamesAgainst = gamesAgainst,
                    points = points,
                ),
            )
            position++
        }

        val relegationSpots =
            if (relegationStart == Int.MAX_VALUE) {
                0
            } else {
                standings.size - relegationStart + 1
            }

        return ParsedStandingsPage(standings, promotionSpots, relegationSpots)
    }

    private fun hasZoneClass(
        row: Element,
        cls: String,
    ): Boolean = row.hasClass(cls) || row.select("td").firstOrNull()?.hasClass(cls) == true

    private fun resolveLeague(
        doc: Document,
        selectedBlockIndex: Int,
        seasonYear: Int,
    ): String {
        if (seasonYear < 2011) return "STT" // regional leagues didn't exist before 2011
        if (selectedBlockIndex == 0) return "STT"

        // Regional — the active league is the grayed-out item (no <a>) in the rvNav
        val rvNav = doc.selectFirst("ul#rvNav") ?: return "NWTTV"
        val grayedItem =
            rvNav.select("li").firstOrNull { li ->
                li.selectFirst("a") == null && li.text().trim().isNotBlank()
            }
        return grayedItem?.text()?.trim() ?: "NWTTV"
    }

    private fun parseTeams(doc: Document): List<ParsedTeam> {
        // Primary: extract teams from the match schedule. Every match row carries home and
        // away team links with teamid/clubid in the href, so this works for regular groups,
        // playoffs, and cups — anything that has matches.
        val matchTable =
            doc.select("tr:has(td:containsOwn(Runde))")
                .firstOrNull()?.parent()?.parent()

        if (matchTable != null) {
            val teams =
                matchTable.select("tr.psodd, tr.playerStats, tr.pshl")
                    .flatMap { row ->
                        val cells = row.select("td")
                        if (cells.size < 4) return@flatMap emptyList()
                        listOfNotNull(
                            cells.getOrNull(2)?.selectFirst("a")?.let { teamFromLink(it) },
                            cells.getOrNull(3)?.selectFirst("a")?.let { teamFromLink(it) },
                        )
                    }
                    .distinctBy { it.knobTeamId }
            if (teams.isNotEmpty()) return teams
        }

        // Fallback: standings table for groups that exist but have no matches yet.
        return doc.select("table.a02Bar").first()
            ?.nextElementSibling()
            ?.select("tr.psauf, tr.psab, tr.psodd, tr.playerStats, tr.pshl")
            ?.mapNotNull { row ->
                row.select("td.playerName a").firstOrNull()?.let { teamFromLink(it) }
            }
            ?.distinctBy { it.knobTeamId }
            ?: emptyList()
    }

    private fun teamFromLink(link: Element): ParsedTeam? {
        val href = link.attr("href")
        val clubId = extractParam(href, "clubid")?.toIntOrNull() ?: return null
        val teamId = extractParam(href, "teamid")?.toIntOrNull() ?: return null
        return ParsedTeam(name = nfc(link.text().trim()), knobClubId = clubId, knobTeamId = teamId)
    }

    private fun parsePlayers(doc: Document): List<ParsedPlayer> {
        // The player table (a05Bar) is followed by two filter tables before the actual data table
        val playerTable =
            doc.select("table.a05Bar").first()
                ?.nextElementSibling() // sort mode toggle table
                ?.nextElementSibling() // stammspieler filter table
                ?.nextElementSibling() // actual player ranking table

        return playerTable
            ?.select("tr.psauf, tr.psab, tr.psodd, tr.playerStats, tr.pshl")
            ?.mapNotNull { row ->
                val cells = row.select("td")
                // Expected columns: rank | player link | team link | klass | AnzMS | ...
                if (cells.size < 9) return@mapNotNull null

                val playerLink = cells[1].selectFirst("a") ?: return@mapNotNull null
                val teamLink = cells[2].selectFirst("a") ?: return@mapNotNull null
                val knobId =
                    extractParam(playerLink.attr("href"), "gid")?.toIntOrNull()
                        ?: return@mapNotNull null
                val clubId =
                    extractParam(teamLink.attr("href"), "clubid")?.toIntOrNull()
                        ?: return@mapNotNull null
                val teamId =
                    extractParam(teamLink.attr("href"), "teamid")?.toIntOrNull()
                        ?: return@mapNotNull null

                ParsedPlayer(
                    fullName = nfc(playerLink.text().trim()),
                    knobId = knobId,
                    klass = expandKlass(cells[3].text().trim()) ?: "",
                    knobClubId = clubId,
                    knobTeamId = teamId,
                )
            }
            ?: emptyList()
    }

    private fun parseMatches(doc: Document): List<ParsedMatch> {
        // The match table is identified by a header row containing "Runde"
        // This handles both layouts (with and without Vorrunde column)
        val matchTable =
            doc.select("tr:has(td:containsOwn(Runde))")
                .firstOrNull()?.parent()?.parent()
                ?: return emptyList()

        return matchTable.select("tr.psodd, tr.playerStats, tr.pshl")
            .mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 5) return@mapNotNull null

                val matchId =
                    cells[0].attr("title")
                        .let { extractParamFromTitle(it, "matchid") }
                        ?.toIntOrNull() ?: return@mapNotNull null

                // Round is a number ("8 ( 1 )") or cup label ("Viertelfinal") — store normalised
                val roundRaw = cells[0].text().trim()
                val round =
                    roundRaw.split(" ").first().toIntOrNull()?.toString()
                        ?: roundRaw.takeIf { it.isNotBlank() }

                val homeTeamId =
                    extractParam(
                        cells[2].selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                        "teamid",
                    )?.toIntOrNull() ?: return@mapNotNull null

                val awayTeamId =
                    extractParam(
                        cells[3].selectFirst("a")?.attr("href") ?: return@mapNotNull null,
                        "teamid",
                    )?.toIntOrNull() ?: return@mapNotNull null

                // Completed matches have a linked score ("4:6"); forfeits have plain text ("0:0 w.o.")
                val scoreText =
                    cells[4].selectFirst("a")?.text()?.trim()
                        ?: cells[4].text().trim().takeIf { it.isNotBlank() }
                val (homeScore, awayScore, status) = parseScore(scoreText)

                ParsedMatch(
                    knobMatchId = matchId,
                    round = round,
                    homeKnobTeamId = homeTeamId,
                    awayKnobTeamId = awayTeamId,
                    playedAt = cells[1].text().trim().takeIf { it.isNotBlank() },
                    homeScore = homeScore,
                    awayScore = awayScore,
                    status = status,
                )
            }
    }

    private fun parseScore(scoreText: String?): Triple<Int?, Int?, MatchStatus> {
        if (scoreText == null || !scoreText.contains(":")) {
            return Triple(null, null, MatchStatus.SCHEDULED)
        }
        val parts = scoreText.split(":")
        val home = parts[0].trim().toIntOrNull()
        // Strip trailing annotations like " w.o." before parsing the away score
        val away = parts[1].trim().substringBefore(" ").toIntOrNull()
        return if (home != null && away != null) {
            Triple(home, away, MatchStatus.COMPLETED)
        } else {
            Triple(null, null, MatchStatus.COMPLETED)
        }
    }

    // -------------------------------------------------------------------------
    // Match detail page — individual game and set results
    // -------------------------------------------------------------------------

    fun parseMatchDetail(
        html: String,
        matchId: Int,
    ): ParsedMatchDetail {
        val doc = Jsoup.parse(html)

        // The match detail page is the group page with one match's detail expanded inline.
        // Multiple tables on the page share the same tr CSS classes (psodd, playerStats), so
        // we identify the correct table by the presence of a "Partie" header cell, then
        // additionally guard every row by requiring player links — only genuine game rows have those.
        val gameTable =
            doc.select("table:has(tr > td:containsOwn(Partie))").firstOrNull()
                ?: return ParsedMatchDetail(matchId, emptyList())

        val games = mutableListOf<ParsedGame>()
        // Built from singles rows so doubles player 2 can be resolved by name.
        // Singles always precede doubles in knob.ch match layout.
        val nameToKnobId = mutableMapOf<String, Int>()
        var order = 1

        for (row in gameTable.select("tr")) {
            val cells = row.select("td")

            // Game rows have 16 cells (with individual set scores) or 12 cells (set area collapsed
            // into a single colspan=5 td). Rows shorter than 12 are header/total/other rows.
            if (cells.size < 12) continue

            // Only genuine game rows carry player links — skip header, total, and summary rows.
            val homeLinks = cells.getOrNull(1)?.select("a[href*='gid=']") ?: continue
            val awayLinks = cells.getOrNull(2)?.select("a[href*='gid=']") ?: continue
            if (homeLinks.isEmpty() || awayLinks.isEmpty()) continue

            // For doubles, knob.ch puts both players in a single <a> tag ("A / B [ klass ]").
            // For singles, there is one <a> tag with one player ("A [ klass ]").
            val isDoubles = cells[1].text().contains("/")

            val homeRaw = homeLinks[0].text().trim()
            val awayRaw = awayLinks[0].text().trim()
            val homeGid1 = extractParam(homeLinks[0].attr("href"), "gid")?.toIntOrNull()
            val awayGid1 = extractParam(awayLinks[0].attr("href"), "gid")?.toIntOrNull()

            // The class in brackets (e.g. "[ 2 ]") is intentionally ignored — it's on a women's
            // ladder for women's-only divisions. Classification is taken from the profile page
            // instead (see KnobPlayerClassScraper); here we only strip the bracket off the name.
            val homeClean = stripKlass(homeRaw)
            val awayClean = stripKlass(awayRaw)

            val homeName1: String?
            val homeName2: String?
            val homeGid2: Int?
            val awayName1: String?
            val awayName2: String?
            val awayGid2: Int?

            if (!isDoubles) {
                homeName1 = homeClean.takeIf { it.isNotBlank() }?.let { nfc(it) }
                homeName2 = null
                homeGid2 = null
                awayName1 = awayClean.takeIf { it.isNotBlank() }?.let { nfc(it) }
                awayName2 = null
                awayGid2 = null
                // Register in name map so the doubles row below can resolve player 2 by name
                if (homeGid1 != null && homeName1 != null) nameToKnobId[homeName1] = homeGid1
                if (awayGid1 != null && awayName1 != null) nameToKnobId[awayName1] = awayGid1
            } else {
                // "Player A / Player B" — split on the slash to get each name
                homeName1 = homeClean.substringBefore("/").trim().takeIf { it.isNotBlank() }?.let { nfc(it) }
                homeName2 = homeClean.substringAfter("/").trim().takeIf { it.isNotBlank() }?.let { nfc(it) }
                homeGid2 = homeName2?.let { nameToKnobId[it] }
                awayName1 = awayClean.substringBefore("/").trim().takeIf { it.isNotBlank() }?.let { nfc(it) }
                awayName2 = awayClean.substringAfter("/").trim().takeIf { it.isNotBlank() }?.let { nfc(it) }
                awayGid2 = awayName2?.let { nameToKnobId[it] }
            }

            // A team fielding only two players produces "w.o." forfeit rows where one side is an
            // empty player placeholder (<a href="...gid=&...">[  ]</a>): a real link, but with no
            // gid and no name. These still count toward the match score but are not actual games,
            // so we skip them rather than inserting a phantom game into the history.
            if (homeName1 == null || awayName1 == null) continue

            // Cells 3..7 hold individual set scores (e.g. "11:8") when available.
            // When unavailable they collapse into a single colspan=5 td — parseSetScores
            // breaks early on blank/non-score text and returns an empty list.
            val sets = parseSetScores(cells, fromIndex = 3, toIndex = 7)

            // Set count totals sit at fixed offsets from the END of the row, which is stable
            // across both the 16-cell (full set detail) and 12-cell (collapsed) row formats.
            // Layout from the end: [..., homeSets, ":", awaySets, homePoints, ":", awayPoints, homeRunning, awayRunning]
            //                            -8         -7  -6         -5          -4  -3           -2           -1
            val homeSets = cells.getOrNull(cells.size - 8)?.text()?.trim()?.toIntOrNull()
            val awaySets = cells.getOrNull(cells.size - 6)?.text()?.trim()?.toIntOrNull()

            val result =
                when {
                    homeSets != null && awaySets != null && homeSets > awaySets -> GameResult.HOME
                    homeSets != null && awaySets != null && awaySets > homeSets -> GameResult.AWAY
                    else -> GameResult.NOT_PLAYED
                }

            games.add(
                ParsedGame(
                    orderInMatch = order++,
                    gameType = if (isDoubles) GameType.DOUBLES else GameType.SINGLES,
                    homePlayer1KnobId = homeGid1,
                    homePlayer1Name = homeName1,
                    homePlayer2KnobId = homeGid2,
                    homePlayer2Name = homeName2,
                    awayPlayer1KnobId = awayGid1,
                    awayPlayer1Name = awayName1,
                    awayPlayer2KnobId = awayGid2,
                    awayPlayer2Name = awayName2,
                    homeSets = homeSets,
                    awaySets = awaySets,
                    result = result,
                    sets = sets,
                ),
            )
        }

        return ParsedMatchDetail(matchId, games)
    }

    private fun parseSetScores(
        cells: List<Element>,
        fromIndex: Int,
        toIndex: Int,
    ): List<ParsedSet> {
        val sets = mutableListOf<ParsedSet>()
        for (i in fromIndex..toIndex) {
            val text = cells.getOrNull(i)?.text()?.trim() ?: break
            if (text.isBlank() || text == "\u00a0") break
            val parts = text.split(":")
            val home = parts.getOrNull(0)?.toIntOrNull() ?: break
            val away = parts.getOrNull(1)?.toIntOrNull() ?: break
            sets.add(ParsedSet(setNumber = sets.size + 1, homePoints = home, awayPoints = away))
        }
        return sets
    }

    // -------------------------------------------------------------------------
    // Overall player registry (?overall=5)
    // -------------------------------------------------------------------------

    /**
     * Parses the nationwide licensed player list for a season.
     * Returns players that have a valid STT license number.
     * Rows with "nicht STT lizenziert" or a blank license are skipped.
     */
    fun parseOverallPlayers(html: String): List<ParsedLicensedPlayer> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.pTitle") ?: return emptyList()

        return table.select("tr.psauf, tr.psab")
            .mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 2) return@mapNotNull null
                val rawName = cells[0].text().trim()
                if (rawName.isBlank()) return@mapNotNull null
                val licence = cells[1].text().trim()
                if (licence.isBlank() || licence == "-" || licence == "0" || licence.contains("nicht")) {
                    return@mapNotNull null
                }
                val newClub = cells.getOrNull(4)?.text()?.trim()?.takeIf { it.isNotBlank() }
                ParsedLicensedPlayer(fullName = nfc(rawName), licenceNr = licence, newClub = newClub)
            }
    }

    /**
     * Distinct player ids (`gid`) from a licence-search result. One licence resolves to one person,
     * but knob may have assigned that person several gids over the years — all appear here.
     */
    fun parseSearchGids(html: String): List<Int> =
        Jsoup.parse(html)
            .select("a[href*=gid=]")
            .mapNotNull { extractParam(it.attr("href"), "gid")?.toIntOrNull() }
            .distinct()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a raw knob.ch Klassierung number (1–22) to the Swiss TT letter-prefix format.
     * knob.ch stores just the ladder position; the standard format used by click-tt and the DB
     * prefixes the position with the division letter:
     *   D = 1–5 | C = 6–10 | B = 11–15 | A = 16–22
     * Values that are already formatted (e.g. "D2"), empty, or null are returned unchanged.
     */
    private fun expandKlass(raw: String?): String? {
        val s = raw ?: return null
        val num = s.toIntOrNull() ?: return s
        val prefix =
            when (num) {
                in 1..5 -> "D"
                in 6..10 -> "C"
                in 11..15 -> "B"
                in 16..22 -> "A"
                else -> return s
            }
        return "$prefix$num"
    }

    private fun stripKlass(text: String): String = text.trim().replace(Regex("""\s*\[[^\]]+\]$"""), "").trim()

    /**
     * Parses the "Follow-your-player" table on a player's profile page into a
     * `(knob gruppe id, season) -> class` map. This class column is always on the men's ladder, so
     * it's used to override the per-match bracket (which is on a women's ladder for women's-only
     * divisions). The table is identified by its "Saison" header — deliberately NOT the ranking
     * table further down, which lists *other* players (whose classes may be women's-ladder).
     *
     * Each row carries the class in a `[A-D]<n>` cell and the gruppe + season in its link's
     * `gruppe=` / `ms=` params (present even on continuation rows where the season cell is blank).
     */
    fun parsePlayerProfileClasses(html: String): Map<Pair<Int, String>, String> {
        val doc = Jsoup.parse(html)
        val table =
            doc.select("table").firstOrNull { table ->
                table.select("tr td").any { it.text().trim().equals("Saison", ignoreCase = true) }
            } ?: return emptyMap()

        val classPattern = Regex("^[A-D]\\d{1,2}$")
        val result = mutableMapOf<Pair<Int, String>, String>()
        for (row in table.select("tr")) {
            val klass =
                row.select("td").firstOrNull { classPattern.matches(it.text().trim()) }?.text()?.trim()
                    ?: continue
            val link = row.select("a[href*=gruppe=][href*=ms=]").firstOrNull() ?: continue
            val gruppe = extractParam(link.attr("href"), "gruppe")?.toIntOrNull() ?: continue
            val season = extractParam(link.attr("href"), "ms")?.let { msToSeason(it) } ?: continue
            result[gruppe to season] = klass
        }
        return result
    }

    /** knob's compact season param "20242025" → the canonical "2024/2025" form. */
    private fun msToSeason(ms: String): String? =
        if (ms.length == 8) "${ms.substring(0, 4)}/${ms.substring(4, 8)}" else null

    private fun extractParam(
        url: String,
        key: String,
    ): String? =
        url.split("&", "?")
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.substringBefore("&")

    private fun extractParamFromTitle(
        title: String,
        key: String,
    ): String? =
        title.split(";")
            .firstOrNull { it.trim().startsWith("$key=") }
            ?.substringAfter("=")
            ?.trim()

    /** Normalises to Unicode NFC so accented characters compare equal regardless of source encoding. */
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)
}
