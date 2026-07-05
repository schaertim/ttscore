package com.ttscore.jobs

import com.ttscore.database.Clubs
import com.ttscore.database.dbQuery
import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTParser
import com.ttscore.scraper.clicktt.model.ClickTTClubMember
import com.ttscore.service.PlayerService
import kotlinx.coroutines.delay
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
        var emptyPages = 0

        // Wide range to cover all Swiss clubs — empty IDs are cheap (10 ms fetch, no extra delay)
        val clubIdRange = 33000..35000

        logger.info("ClickTtIdBackfillJob: scanning ${clubIdRange.count()} club IDs")

        for (clickttClubId in clubIdRange) {
            try {
                // Gate on the MALE page — invalid club IDs won't have "Lizenzierte Spieler"
                val maleHtml = client.fetchClubMembersPage(clickttClubId, "MALE")

                if (!maleHtml.contains("Lizenzierte Spieler")) {
                    emptyPages++
                    if (emptyPages % 200 == 0) {
                        logger.info("  Scanned up to club ID $clickttClubId — $emptyPages empty so far")
                    }
                    // No extra delay for empty IDs — the 10 ms in fetchWithRetry is sufficient
                    continue
                }

                emptyPages = 0

                // Fetch female tab for the same club
                val femaleHtml = client.fetchClubMembersPage(clickttClubId, "FEMALE")

                val malePage = parser.parseClubPage(maleHtml, "MALE")
                val femalePage = parser.parseClubPage(femaleHtml, "FEMALE")
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

                // Polite delay only for real clubs (2 requests already made)
                delay(500L)
            } catch (e: Exception) {
                logger.error("  Error fetching club ID $clickttClubId", e)
            }
        }

        logger.info("ClickTtIdBackfillJob complete — $totalPlayers players and $totalClubs clubs linked")
    }

    companion object {
        fun create(): ClickTtIdBackfillJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTtIdBackfillJob(client, parser)
        }
    }
}
