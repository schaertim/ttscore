package com.ttscore.scraper.knob

import com.ttscore.database.Clubs
import com.ttscore.database.PlayerSeasons
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.service.PlayerService
import com.ttscore.util.accentFold
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.*

class OverallPlayerScraper(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(OverallPlayerScraper::class.java)

    /**
     * Always scrapes the full 1989-present range. Each season's page only lists players who
     * transferred that season, so a player who last transferred years ago only ever appears on
     * that one page — narrowing the range causes most players to never get a licenceNr, which
     * permanently breaks click-tt player matching (see ClickTtIdBackfillJob).
     */
    suspend fun run() {
        val fromYear = 1989
        val latestSeasonName =
            transaction {
                Seasons.select(Seasons.name)
                    .orderBy(Seasons.name, SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                    ?.get(Seasons.name)
            }

        if (latestSeasonName == null) {
            logger.warn("OverallPlayerScraper: no seasons in DB, skipping")
            return
        }

        val toYear = latestSeasonName.substringBefore("/").toInt()
        val seasons = generateSeasons(fromYear = fromYear, toYear = toYear)

        logger.info("OverallPlayerScraper: scraping all ${seasons.size} seasons up to $latestSeasonName")

        // Build lookup once — covers all players regardless of season.
        // License lives on the player row, not on player_season, so no season scoping needed.
        // After the GroupScraper phase 1 fix, DB names are already clean (no age suffixes),
        // so a direct name lookup is sufficient.
        val existingPlayers: Map<String, List<Pair<String?, UUID>>> =
            transaction {
                Players.select(Players.id, Players.fullName, Players.licenceNr)
                    .groupBy(
                        { it[Players.fullName] },
                        { Pair(it[Players.licenceNr], it[Players.id]) },
                    )
            }

        // Secondary lookup keyed by accent-folded + lowercased name so that "Grégory" matches
        // "Gregory" and vice-versa — knob.ch is inconsistent with diacritics across pages/seasons.
        val existingByFolded: Map<String, List<Pair<String?, UUID>>> =
            existingPlayers.entries
                .groupBy { (name, _) -> accentFold(name) }
                .mapValues { (_, entries) -> entries.flatMap { it.value } }

        var totalUpdated = 0
        // Licences the name+club heuristic couldn't disambiguate (namesakes) — resolved
        // authoritatively afterwards via knob's licence→gid search.
        val ambiguous = mutableListOf<String>()

        for (seasonName in seasons) {
            try {
                val result = scrapeSeason(seasonName, existingPlayers, existingByFolded)
                totalUpdated += result.updated
                ambiguous += result.ambiguousLicences
            } catch (e: Exception) {
                logger.error("Failed season=$seasonName: ${e.message}")
            }
        }

        totalUpdated += resolveAmbiguousByLicenceSearch(ambiguous)

        logger.info("OverallPlayerScraper complete — $totalUpdated licences linked")
    }

    private data class SeasonResult(
        val updated: Int,
        val ambiguousLicences: List<String>,
    )

    /**
     * Authoritative fallback for the namesakes the name+club pass couldn't split: knob's licence
     * search maps each licence to its exact gid(s), so we attach by id — ground truth — rather than
     * guessing. Only the ambiguous residue (a small fraction) pays the per-licence network cost.
     * Returns how many were newly attached.
     */
    private suspend fun resolveAmbiguousByLicenceSearch(licences: List<String>): Int {
        if (licences.isEmpty()) return 0
        // Some may already have been resolved by name+club in a different season — skip those.
        val attached = PlayerService.distinctLicences().toSet()
        val todo = licences.toSet().minus(attached).toList()
        if (todo.isEmpty()) return 0

        logger.info("OverallPlayerScraper: resolving ${todo.size} ambiguous licences via knob licence search")

        // Stage 1 — search each licence concurrently (network only, no DB writes).
        val gidMap =
            todo.mapConcurrent(SCRAPE_CONCURRENCY) { licence ->
                try {
                    licence to parser.parseSearchGids(client.searchByLicence(licence))
                } catch (e: Exception) {
                    logger.warn("  licence search failed for $licence: ${e.message}")
                    licence to emptyList()
                }
            }

        // Stage 2 — attach serially (per-licence try/catch so one failure isn't fatal).
        var attachedCount = 0
        for ((licence, gids) in gidMap) {
            try {
                if (PlayerService.attachLicenceToKnobRow(licence, gids)) attachedCount++
            } catch (e: Exception) {
                logger.warn("  licence attach failed for $licence: ${e.message}")
            }
        }
        logger.info(
            "OverallPlayerScraper: licence search resolved $attachedCount / ${todo.size} ambiguous licences",
        )
        return attachedCount
    }

    private suspend fun scrapeSeason(
        seasonName: String,
        existingPlayers: Map<String, List<Pair<String?, UUID>>>,
        existingByFolded: Map<String, List<Pair<String?, UUID>>>,
    ): SeasonResult {
        val html = client.fetchOverallPlayers(seasonName)
        val players = parser.parseOverallPlayers(html)

        if (players.isEmpty()) {
            logger.info("  $seasonName — no players found (licence registry predates online records)")
            return SeasonResult(0, emptyList())
        }

        logger.info("  $seasonName — ${players.size} licensed players scraped")

        var updated = 0
        val ambiguous = mutableListOf<String>()

        transaction {
            for (player in players) {
                // Exact match first; fall back to accent-folded match so "Grégory" finds "Gregory"
                val candidates =
                    existingPlayers[player.fullName]
                        ?: existingByFolded[accentFold(player.fullName)]

                when {
                    candidates == null -> {
                        // No existing player row found — skip. The licence scraper only links
                        // licences to players already in the DB from match scraping. Players who
                        // appear in the licence registry but not in any scraped match detail are
                        // not useful records (no games, no club, no class). In a full historical
                        // backfill the match scraper runs first and creates the row; in a
                        // single-season run the player simply didn't play that season.
                    }
                    candidates.size == 1 -> {
                        val (currentLicence, playerId) = candidates[0]
                        if (currentLicence == null) {
                            val licenceTaken =
                                Players.select(Players.id)
                                    .where { Players.licenceNr eq player.licenceNr }
                                    .firstOrNull() != null
                            if (!licenceTaken) {
                                Players.update({ Players.id eq playerId }) {
                                    it[Players.licenceNr] = player.licenceNr
                                }
                                updated++
                            }
                        }
                    }
                    else -> {
                        // Multiple players share this name — use new club to disambiguate
                        val playerId =
                            if (player.newClub != null) {
                                findPlayerAtClub(candidates.map { it.second }, player.newClub)
                            } else {
                                null
                            }

                        if (playerId != null) {
                            val currentLicence = candidates.first { it.second == playerId }.first
                            if (currentLicence == null) {
                                val licenceTaken =
                                    Players.select(Players.id)
                                        .where { Players.licenceNr eq player.licenceNr }
                                        .firstOrNull() != null
                                if (!licenceTaken) {
                                    Players.update({ Players.id eq playerId }) {
                                        it[Players.licenceNr] = player.licenceNr
                                    }
                                    updated++
                                }
                            }
                        } else {
                            // Name+club couldn't disambiguate the namesakes (none — or several — of
                            // them is at that club in match data). Defer to the authoritative
                            // licence→gid search run after all seasons.
                            ambiguous += player.licenceNr
                        }
                    }
                }
            }
        }

        if (updated > 0) {
            logger.info("    → $updated licences linked")
        }

        return SeasonResult(updated = updated, ambiguousLicences = ambiguous)
    }

    private fun findPlayerAtClub(
        playerIds: List<UUID>,
        clubName: String,
    ): UUID? {
        val clubNameLower = clubName.lowercase()
        return transaction {
            val atClub =
                (PlayerSeasons innerJoin Teams innerJoin Clubs)
                    .select(PlayerSeasons.playerId, Clubs.name)
                    .where { PlayerSeasons.playerId inList playerIds }
                    .filter { it[Clubs.name].lowercase().contains(clubNameLower) }
                    .map { it[PlayerSeasons.playerId] }
                    .distinct()
            // Resolve only when the club uniquely identifies one of the namesakes; two distinct players
            // sharing both name and club can't be told apart here, so refuse rather than mislink a licence.
            atClub.singleOrNull()
        }
    }
}
