package com.ttscore.jobs

import com.ttscore.database.Federations
import com.ttscore.database.Groups
import com.ttscore.database.Seasons
import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTGroupScraper
import com.ttscore.scraper.clicktt.ClickTTGroupScraper.GroupRef
import com.ttscore.scraper.clicktt.ClickTTMatchScraper
import com.ttscore.scraper.clicktt.ClickTTParser
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Keeps the current click-tt season in sync. Runs nightly, and once at startup to seed
 * the season immediately.
 *
 * Phase 1 — Discovery: scrape the season's league pages to seed groups click-tt has newly
 *            published. isAlreadyScraped skips fully-populated groups, so reruns are cheap
 *            and only do real work for groups added since the last run. This is what lets a
 *            progressively-released season fill in automatically with no code changes.
 * Phase 2 — Refresh: re-fetch schedule + standings for groups already in the DB, catching
 *            postponements and reschedules that the past-due poll would miss.
 * Phase 3 — Match details: scrape game/set details for any completed match still missing
 *            game rows (DB-driven, so it also covers matches this job just flipped to
 *            completed during the refresh phase).
 */
class SeasonSyncJob(
    private val groupScraper: ClickTTGroupScraper,
    private val matchScraper: ClickTTMatchScraper,
) {
    private val logger = LoggerFactory.getLogger(SeasonSyncJob::class.java)

    suspend fun run(season: String) {
        // Phase 1 — discovery: seed any newly-published groups for the season.
        logger.info("SeasonSyncJob: discovering groups for season $season")
        groupScraper.run(season)

        // Phase 2 — refresh known groups' schedule + standings.
        val groups = getGroupsForSeason(season)
        logger.info("SeasonSyncJob: refreshing ${groups.size} known groups for season $season")
        var refreshed = 0
        for (group in groups) {
            try {
                groupScraper.refreshGroupSchedule(group)
                groupScraper.refreshGroupStandings(group)
                refreshed++
            } catch (e: Exception) {
                logger.error("SeasonSyncJob: failed group ${group.clickttId}: ${e.message}")
            }
            delay(500L)
        }

        // Phase 3 — scrape game details for any completed match without game rows yet.
        matchScraper.run()

        logger.info("SeasonSyncJob: complete — $refreshed / ${groups.size} groups refreshed")
    }

    private fun getGroupsForSeason(season: String): List<GroupRef> =
        transaction {
            (Groups innerJoin Federations innerJoin Seasons)
                .select(Groups.id, Groups.clickttId, Federations.name, Seasons.name)
                .where {
                    (Seasons.name eq season) and
                        Groups.clickttId.isNotNull()
                }
                .map {
                    GroupRef(
                        dbId = it[Groups.id],
                        clickttId = it[Groups.clickttId]!!,
                        championship = ClickTTGroupScraper.toChampionship(it[Federations.name], it[Seasons.name]),
                    )
                }
        }

    companion object {
        fun create(): SeasonSyncJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return SeasonSyncJob(
                groupScraper = ClickTTGroupScraper(client, parser),
                matchScraper = ClickTTMatchScraper(client, parser),
            )
        }
    }
}
