package com.ttfeed.scraper.knob

import com.ttfeed.database.Clubs
import com.ttfeed.database.PlayerSeasons
import com.ttfeed.database.Players
import com.ttfeed.database.Seasons
import com.ttfeed.database.Teams
import com.ttfeed.util.accentFold
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.*

class OverallPlayerScraper(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(OverallPlayerScraper::class.java)

    suspend fun run() {
        // Always scrape all seasons up to the latest one in the DB so that a partial
        // backfill (e.g. a single season for debugging) still gets the full transfer
        // history needed to link licence numbers.
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
        val seasons = generateSeasons(fromYear = 1989, toYear = toYear)

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
        var totalInserted = 0

        for (seasonName in seasons) {
            try {
                val (updated, inserted) = scrapeSeason(seasonName, existingPlayers, existingByFolded)
                totalUpdated += updated
                totalInserted += inserted
            } catch (e: Exception) {
                logger.error("Failed season=$seasonName: ${e.message}")
            }
        }

        logger.info("OverallPlayerScraper complete — $totalUpdated updated, $totalInserted new inserts")
    }

    private suspend fun scrapeSeason(
        seasonName: String,
        existingPlayers: Map<String, List<Pair<String?, UUID>>>,
        existingByFolded: Map<String, List<Pair<String?, UUID>>>,
    ): Pair<Int, Int> {
        val html = client.fetchOverallPlayers(seasonName)
        val players = parser.parseOverallPlayers(html)

        if (players.isEmpty()) {
            logger.info("  $seasonName — no players found (licence registry predates online records)")
            return 0 to 0
        }

        logger.info("  $seasonName — ${players.size} licensed players scraped")

        var updated = 0
        var inserted = 0

        transaction {
            for (player in players) {
                // Exact match first; fall back to accent-folded match so "Grégory" finds "Gregory"
                val candidates =
                    existingPlayers[player.fullName]
                        ?: existingByFolded[accentFold(player.fullName)]

                when {
                    candidates == null -> {
                        Players.insertIgnore {
                            it[Players.licenceNr] = player.licenceNr
                            it[Players.fullName] = player.fullName
                        }
                        inserted++
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
                            logger.warn(
                                "  Name collision for '${player.fullName}' (${candidates.size} candidates)," +
                                    " club='${player.newClub}' — skipping licence ${player.licenceNr}",
                            )
                        }
                    }
                }
            }
        }

        if (updated > 0 || inserted > 0) {
            logger.info("    → $updated updated, $inserted new inserts")
        }

        return updated to inserted
    }

    private fun findPlayerAtClub(
        playerIds: List<UUID>,
        clubName: String,
    ): UUID? {
        val clubNameLower = clubName.lowercase()
        return transaction {
            (PlayerSeasons innerJoin Teams innerJoin Clubs)
                .select(PlayerSeasons.playerId, Clubs.name)
                .where { PlayerSeasons.playerId inList playerIds }
                .firstOrNull { it[Clubs.name].lowercase().contains(clubNameLower) }
                ?.get(PlayerSeasons.playerId)
        }
    }
}
