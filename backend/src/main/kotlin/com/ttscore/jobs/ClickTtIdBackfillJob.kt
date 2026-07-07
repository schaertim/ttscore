package com.ttscore.jobs

import com.ttscore.database.Clubs
import com.ttscore.database.dbQuery
import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTParser
import com.ttscore.scraper.clicktt.model.ClickTTClubMember
import com.ttscore.scraper.clicktt.model.ClickTTClubPage
import com.ttscore.scraper.knob.SCRAPE_CONCURRENCY
import com.ttscore.scraper.knob.mapConcurrent
import com.ttscore.service.PlayerService
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

class ClickTtIdBackfillJob(
    private val client: ClickTTClient,
    private val parser: ClickTTParser,
) {
    private val logger = LoggerFactory.getLogger(ClickTtIdBackfillJob::class.java)

    suspend fun run() {
        var totalPlayers = 0
        var totalClubs = 0

        // Wide range to cover all Swiss clubs — most IDs are misses.
        val clubIdRange = 33000..35000

        logger.info("ClickTtIdBackfillJob: scanning ${clubIdRange.count()} club IDs")

        // Stage 1 — probe every id concurrently on the MALE page alone; invalid club ids won't
        // have "Lizenzierte Spieler". Cheapest possible filter before the (rarer) FEMALE fetch.
        val hits =
            clubIdRange.toList().mapConcurrent(SCRAPE_CONCURRENCY) { clickttClubId ->
                try {
                    val maleHtml = client.fetchClubMembersPage(clickttClubId, "MALE")
                    if (maleHtml.contains("Lizenzierte Spieler")) clickttClubId to maleHtml else null
                } catch (e: Exception) {
                    logger.error("  Error probing club ID $clickttClubId", e)
                    null
                }
            }.filterNotNull()

        logger.info("ClickTtIdBackfillJob: ${hits.size} real clubs found — fetching rosters")

        // Stage 2 — real clubs only: fetch the FEMALE page concurrently too, parse both.
        val clubs =
            hits.mapConcurrent(SCRAPE_CONCURRENCY) { (clickttClubId, maleHtml) ->
                try {
                    val femaleHtml = client.fetchClubMembersPage(clickttClubId, "FEMALE")
                    val malePage = parser.parseClubPage(maleHtml, "MALE")
                    val femalePage = parser.parseClubPage(femaleHtml, "FEMALE")
                    ClubHit(clickttClubId, malePage, femalePage)
                } catch (e: Exception) {
                    logger.error("  Error fetching roster for club ID $clickttClubId", e)
                    null
                }
            }.filterNotNull()

        // Stage 3 — DB writes, serialized (single non-pooled connection). Per-club try/catch
        // mirrors the original loop: one failing club is logged and skipped, not fatal.
        for ((clickttClubId, malePage, femalePage) in clubs) {
            try {
                val allMembers: List<ClickTTClubMember> = malePage.members + femalePage.members
                val clubName = malePage.clubName ?: femalePage.clubName

                if (allMembers.isEmpty()) continue

                // Phase A — match by licence number (primary key)
                PlayerService.updateClickTtDataBatch(allMembers)
                totalPlayers += allMembers.size

                val licences = allMembers.map { it.licence }

                // Phase B + C — for members whose licence wasn't in our DB
                val matchedLicences = PlayerService.findLicencesInDb(licences)
                val unmatched = allMembers.filter { it.licence !in matchedLicences }

                if (unmatched.isNotEmpty()) {
                    // Phase B — name + club fallback: links unmatched members to existing knob rows
                    if (clubName != null) {
                        PlayerService.matchAndLinkByNameAndClub(unmatched, clubName)
                    }

                    // Phase C — insert any still-unmatched members as fresh rows.
                    // INSERT IGNORE means members already linked by Phase B (their clicktt_id
                    // now lives on an existing row) are silently skipped.
                    PlayerService.insertUnmatchedClickTtMembers(unmatched)

                    logger.debug(
                        "  Club ID $clickttClubId — ${unmatched.size} unmatched by licence," +
                            " ran name+club fallback + insert",
                    )
                }

                val ourClubId = PlayerService.findClubIdByLicences(licences)
                if (ourClubId != null && clubName != null) {
                    dbQuery {
                        Clubs.update({ Clubs.id eq ourClubId }) {
                            it[Clubs.clickttId] = clickttClubId
                            it[Clubs.name] = clubName
                        }
                    }
                    totalClubs++
                    logger.info(
                        "  Club ID $clickttClubId → '$clubName' — " +
                            "${allMembers.size} players (${malePage.members.size}M/${femalePage.members.size}F)",
                    )
                } else {
                    logger.debug("  Club ID $clickttClubId ($clubName) — no matching club found in DB")
                }
            } catch (e: Exception) {
                logger.error("  Error processing club ID $clickttClubId", e)
            }
        }

        logger.info("ClickTtIdBackfillJob complete — $totalPlayers players and $totalClubs clubs linked")
    }

    private data class ClubHit(
        val clickttClubId: Int,
        val malePage: ClickTTClubPage,
        val femalePage: ClickTTClubPage,
    )

    companion object {
        fun create(): ClickTtIdBackfillJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTtIdBackfillJob(client, parser)
        }
    }
}
