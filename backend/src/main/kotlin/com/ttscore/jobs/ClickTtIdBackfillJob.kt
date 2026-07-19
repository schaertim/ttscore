package com.ttscore.jobs

import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTParser
import com.ttscore.scraper.clicktt.model.EloFilterResultRow
import com.ttscore.scraper.knob.SCRAPE_CONCURRENCY
import com.ttscore.scraper.knob.mapConcurrent
import com.ttscore.service.PlayerService
import com.ttscore.service.PlayerService.UnlinkedPlayer
import com.ttscore.util.clubNamesSimilar
import com.ttscore.util.knobNameSplitCandidates
import com.ttscore.util.personNamesNearMatch
import com.ttscore.util.personNamesSimilar
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Player-driven click-tt linking. Drives off knob's own player list and resolves each unlinked
 * player through click-tt's Elo-Filter search — which covers the full ranking history, not just
 * current club rosters — so it also reaches players who have since lapsed or changed clubs (the
 * structural blind spot of the old roster-crawl approach). Two passes:
 *
 *  - Pass 1 (players with a licence): search by licence number; the returned name is verified
 *    against knob's stored name before any linking (a licence can resolve to different people across
 *    the two systems). On a verified match the result row is followed to the detail page for the
 *    real person id + click-tt club id.
 *  - Pass 2 (players with no licence): search by name; a single verified candidate is resolved
 *    directly, several namesakes are disambiguated by matching each candidate's club against the
 *    player's knob club(s).
 *
 * Name verification has three tiers: an exact (accent/punctuation-insensitive) token match is
 * trusted on its own; a close-but-not-exact match (a 1-2 letter misspelling on either side — knob
 * and click-tt both have data-entry typos) additionally requires the row's click-tt club to match
 * one of the player's knob clubs before it's trusted — a near-match name alone isn't strong enough
 * evidence, but near-match name + matching club is. When click-tt's own club column is blank
 * (common for lapsed/edge-case players — there's nothing to cross-check), a tighter single-letter
 * edit distance is trusted on its own instead, since requiring a club that doesn't exist would
 * otherwise make every such typo permanently unresolvable.
 *
 * Resolving a player also resolves their current click-tt club (id on the detail page, name on the
 * result row), which is used to link/verify the knob club (see [PlayerService.linkResolvedClub]).
 *
 * Idempotent: a linked player drops out of the candidate set, so re-runs only pay for whatever is
 * still unlinked.
 */
class ClickTtIdBackfillJob(
    private val client: ClickTTClient,
    private val parser: ClickTTParser,
) {
    private val logger = LoggerFactory.getLogger(ClickTtIdBackfillJob::class.java)

    private data class Resolved(
        val playerId: UUID,
        val personId: Int,
        /** Raw click-tt name ("Lastname, Firstname"); adopted as the canonical player name. */
        val clickTtName: String,
        val category: String?,
        /** Licence to write (backfill/correct), or null to leave the player's licence untouched. */
        val licence: String?,
        val matchMethod: PlayerService.MatchMethod,
        val clickTtClubId: Int?,
        val clickTtClubName: String?,
    )

    suspend fun run() {
        // Every search must be pinned to the current monthly ranking date, pre-selected on the empty
        // form. Bail rather than guess if the page shape changed.
        val rankingDate = parser.parseEloFilterRankingDate(client.fetchEloFilterForm())
        if (rankingDate == null) {
            logger.error("ClickTtIdBackfillJob: could not read ranking date from Elo-Filter form — aborting")
            return
        }

        val withLicence = PlayerService.getUnlinkedPlayersWithLicence()
        val withoutLicence = PlayerService.getUnlinkedPlayersWithoutLicence()
        logger.info(
            "ClickTtIdBackfillJob: ${withLicence.size} unlinked players with licence, " +
                "${withoutLicence.size} without (ranking $rankingDate)",
        )

        // Pass-2 namesake disambiguation and near-match name confirmation (both passes) need each
        // player's knob club names — load once up front so the concurrent resolve stage never does a
        // per-player DB round-trip.
        val allUnlinked = withLicence + withoutLicence
        val clubNamesByPlayer = PlayerService.getKnobClubNamesFor(allUnlinked.map { it.id })

        // Network + parse stage, bounded concurrency. Reads only; DB writes happen serialized after.
        // Licensed players come first so they win a person-id tie against an unlicensed namesake row.
        val resolved =
            allUnlinked.mapConcurrent(SCRAPE_CONCURRENCY) { player ->
                try {
                    val knobClubNames = clubNamesByPlayer[player.id].orEmpty()
                    if (player.licence != null) {
                        resolveByLicence(player, rankingDate, knobClubNames)
                    } else {
                        resolveByName(player, rankingDate, knobClubNames)
                    }
                } catch (e: Exception) {
                    logger.warn("  Resolve failed for player ${player.id} ('${player.fullName}'): ${e.message}")
                    null
                }
            }.filterNotNull()

        // Serialized DB writes. Drop a person id already held by another row (unique constraint) or
        // already claimed earlier in this same batch, so one dirty datum can't abort the whole run.
        val taken = PlayerService.getAssignedClickTtIds().toMutableSet()
        var linked = 0
        var licencesWritten = 0
        var clubsLinked = 0
        for (r in resolved) {
            if (r.personId in taken) {
                logger.warn("  Person id ${r.personId} already assigned — skipping player ${r.playerId}")
                continue
            }
            // Per-player guard: a single failing write (e.g. an unforeseen constraint clash on this
            // player's row or club) must never abort the whole multi-thousand-player backfill.
            try {
                taken += r.personId
                when (
                    PlayerService.updateClickTtLink(
                        r.playerId, r.personId, r.clickTtName, r.category, r.licence, r.matchMethod,
                    )
                ) {
                    PlayerService.LicenceWriteResult.WRITTEN -> {
                        licencesWritten++
                        logger.info("  Licence set to ${r.licence} for player ${r.playerId} ('${r.clickTtName}')")
                    }
                    PlayerService.LicenceWriteResult.CONFLICT ->
                        logger.warn(
                            "  Licence ${r.licence} for player ${r.playerId} already held by another row — " +
                                "linked click-tt id + name, licence left as-is",
                        )
                    PlayerService.LicenceWriteResult.UNCHANGED -> {}
                }
                linked++

                if (r.clickTtClubId != null && r.clickTtClubName != null) {
                    when (PlayerService.linkResolvedClub(r.playerId, r.clickTtClubId, r.clickTtClubName)) {
                        PlayerService.ClubLinkResult.LINKED -> {
                            clubsLinked++
                            logger.info(
                                "  Club ${r.clickTtClubId} ('${r.clickTtClubName}') linked via player ${r.playerId}",
                            )
                        }
                        PlayerService.ClubLinkResult.LINKED_KEPT_NAME -> {
                            clubsLinked++
                            logger.info(
                                "  Club ${r.clickTtClubId} linked via player ${r.playerId} — kept knob name " +
                                    "('${r.clickTtClubName}' already taken; ClubDedupeJob will merge)",
                            )
                        }
                        PlayerService.ClubLinkResult.CONFLICT ->
                            logger.warn(
                                "  Club anomaly: click-tt club ${r.clickTtClubId} ('${r.clickTtClubName}') " +
                                    "disagrees with the knob club already recorded (player ${r.playerId}) — left untouched",
                            )
                        // ALREADY_LINKED / NO_NAME_MATCH / AMBIGUOUS — unremarkable, no action.
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                logger.warn("  Write failed for player ${r.playerId} ('${r.clickTtName}'): ${e.message}")
            }
        }

        logger.info(
            "ClickTtIdBackfillJob complete — $linked players linked " +
                "($licencesWritten licences backfilled/corrected), $clubsLinked clubs linked",
        )
    }

    /**
     * Verifies [row]'s name against [player]'s knob name — see the class doc for the three tiers.
     * Returns a short tag for logging, or null when none of them pass.
     */
    private fun verifyName(
        row: EloFilterResultRow,
        player: UnlinkedPlayer,
        knobClubNames: List<String>,
    ): String? {
        if (personNamesSimilar(row.name, player.fullName)) return "exact"
        if (row.club != null) {
            val clubConfirmed = knobClubNames.any { clubNamesSimilar(it, row.club) }
            if (clubConfirmed && personNamesNearMatch(row.name, player.fullName)) return "near+club"
        } else if (personNamesNearMatch(row.name, player.fullName, maxTotalEditDistance = 1)) {
            return "near+no-club"
        }
        return null
    }

    /** Maps a [verifyName] verdict to a [PlayerService.MatchMethod] in the given search context. */
    private fun licenceMethod(verdict: String) =
        when (verdict) {
            "exact" -> PlayerService.MatchMethod.LICENCE
            "near+club" -> PlayerService.MatchMethod.LICENCE_NEAR_CLUB
            else -> PlayerService.MatchMethod.LICENCE_NEAR
        }

    private fun nameMethod(verdict: String) =
        when (verdict) {
            "exact" -> PlayerService.MatchMethod.NAME
            "near+club" -> PlayerService.MatchMethod.NAME_NEAR_CLUB
            else -> PlayerService.MatchMethod.NAME_NEAR
        }

    /**
     * Pass 1: resolve a licensed player via licence search (name-verified). If the licence returns
     * nothing, or returns a *different* person (knob's stored licence is wrong — happens), fall back
     * to a name search; a match found that way corrects/backfills the licence to click-tt's value.
     */
    private suspend fun resolveByLicence(
        player: UnlinkedPlayer,
        rankingDate: String,
        knobClubNames: List<String>,
    ): Resolved? {
        val licence = player.licence!!
        var rows = parser.parseEloFilterResultRows(client.fetchEloFilterByLicence(licence, rankingDate))
        // F-prefix quirk: some licences only resolve when prefixed with 'F' (knob stores them bare).
        // Try bare first, retry once with the prefix only on zero results.
        if (rows.isEmpty()) {
            rows = parser.parseEloFilterResultRows(client.fetchEloFilterByLicence("F$licence", rankingDate))
        }
        val row = rows.firstOrNull()

        if (row != null) {
            val verdict = verifyName(row, player, knobClubNames)
            if (verdict != null) {
                if (verdict != "exact") {
                    logger.info(
                        "  Name match ($verdict) for licence $licence: knob '${player.fullName}' vs " +
                            "click-tt '${row.name}'",
                    )
                }
                // Licence matched and the name verified — keep knob's licence (it's correct).
                return resolveDetail(player.id, row, licenceToWrite = null, method = licenceMethod(verdict))
            }
        }

        // Licence missing or pointing at someone else — try to find the real person by name instead.
        if (row != null) {
            logger.info(
                "  Licence $licence resolves to a different person on click-tt ('${row.name}' vs knob " +
                    "'${player.fullName}') — trying name search",
            )
        } else {
            logger.debug("  No click-tt result for licence $licence (player ${player.id}) — trying name search")
        }
        return resolveByName(player, rankingDate, knobClubNames)
    }

    /**
     * Resolve a player by name. Tries each [knobNameSplitCandidates] surname/first-name split in turn
     * — the common case (single-word surname) resolves on the first try, so this only costs extra
     * requests for the compound-surname case. For each split, namesakes are disambiguated by matching
     * each candidate's club against the player's knob club(s). A match backfills/corrects the licence
     * to click-tt's value (the row's licence column) — the player either had none or a wrong one.
     */
    private suspend fun resolveByName(
        player: UnlinkedPlayer,
        rankingDate: String,
        knobClubNames: List<String>,
    ): Resolved? {
        for ((lastname, firstname) in knobNameSplitCandidates(player.fullName)) {
            val rows = parser.parseEloFilterResultRows(client.fetchEloFilterByName(lastname, firstname, rankingDate))
            // Keep each verified row with the verdict that verified it, for the match-method label.
            val verified =
                rows.mapNotNull { row -> verifyName(row, player, knobClubNames)?.let { row to it } }

            val (match, method) =
                when (verified.size) {
                    0 -> continue // try the next surname/first-name split
                    1 -> {
                        val (row, verdict) = verified.single()
                        row to nameMethod(verdict)
                    }
                    else -> {
                        // Namesakes: keep only candidates whose click-tt club matches a knob club of
                        // this player. Link only when exactly one survives — never guess. A club match
                        // is the confirming signal, so this is recorded as NAME_CLUB.
                        val byClub =
                            verified.filter { (r, _) ->
                                val club = r.club ?: return@filter false
                                knobClubNames.any { clubNamesSimilar(it, club) }
                            }
                        if (byClub.size != 1) {
                            logger.warn(
                                "  Ambiguous name '${player.fullName}' (player ${player.id}): ${verified.size} " +
                                    "candidates, ${byClub.size} club-matched — not linking",
                            )
                            return null
                        }
                        byClub.single().first to PlayerService.MatchMethod.NAME_CLUB
                    }
                }
            return resolveDetail(player.id, match, licenceToWrite = match.licence, method = method)
        }
        logger.debug("  No click-tt result for name '${player.fullName}' (player ${player.id})")
        return null
    }

    /** Follows a result row's detail link to extract the person id (+ click-tt club id). */
    private suspend fun resolveDetail(
        playerId: UUID,
        row: EloFilterResultRow,
        licenceToWrite: String?,
        method: PlayerService.MatchMethod,
    ): Resolved? {
        val detailHtml = client.fetchUrl(row.detailHref)
        val personId =
            parser.parseEloFilterPersonId(detailHtml) ?: run {
                logger.warn("  No person id on detail page for player $playerId (row '${row.name}')")
                return null
            }
        return Resolved(
            playerId = playerId,
            personId = personId,
            clickTtName = row.name,
            category = row.category,
            licence = licenceToWrite,
            matchMethod = method,
            clickTtClubId = parser.parseEloFilterClubId(detailHtml),
            clickTtClubName = row.club,
        )
    }

    companion object {
        fun create(): ClickTtIdBackfillJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return ClickTtIdBackfillJob(client, parser)
        }
    }
}
