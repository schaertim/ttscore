package com.ttfeed.jobs

import com.ttfeed.database.Federations
import com.ttfeed.database.Groups
import com.ttfeed.database.Matches
import com.ttfeed.database.Players
import com.ttfeed.database.Seasons
import com.ttfeed.database.Teams
import com.ttfeed.model.FollowTargetType
import com.ttfeed.model.MatchStatus
import com.ttfeed.scraper.clicktt.ClickTTClient
import com.ttfeed.scraper.clicktt.ClickTTGroupScraper
import com.ttfeed.scraper.clicktt.ClickTTGroupScraper.GroupRef
import com.ttfeed.scraper.clicktt.ClickTTMatchScraper
import com.ttfeed.scraper.clicktt.ClickTTParser
import com.ttfeed.scraper.clicktt.ClickTTSyncService
import com.ttfeed.service.GameService
import com.ttfeed.service.PushService
import com.ttfeed.service.SeasonService
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

class MatchPollJob(
    private val groupScraper: ClickTTGroupScraper,
    private val matchScraper: ClickTTMatchScraper,
) {
    private val logger = LoggerFactory.getLogger(MatchPollJob::class.java)

    suspend fun run() {
        val currentSeason = SeasonService.getCurrentSeason()
        if (currentSeason == null) {
            logger.warn("MatchPollJob: no season in database, skipping")
            return
        }
        val (seasonId, _) = currentSeason

        val groups = findGroupsWithPastDueMatches()
        if (groups.isEmpty()) {
            logger.debug("MatchPollJob: no groups with past-due scheduled matches")
            return
        }

        logger.info("MatchPollJob: refreshing ${groups.size} groups with past-due matches")

        val newlyCompletedMatchIds = mutableSetOf<java.util.UUID>()
        for (group in groups) {
            try {
                val completed = groupScraper.refreshGroupSchedule(group)
                newlyCompletedMatchIds.addAll(completed)
            } catch (e: Exception) {
                logger.error("MatchPollJob: failed to refresh group ${group.clickttId}: ${e.message}")
            }
        }

        if (newlyCompletedMatchIds.isEmpty()) {
            logger.debug("MatchPollJob: no newly completed matches found")
            return
        }

        logger.info("MatchPollJob: ${newlyCompletedMatchIds.size} matches completed — scraping game details")
        matchScraper.scrapeForMatches(newlyCompletedMatchIds)

        // Send push notifications for newly completed matches
        sendMatchPushNotifications(newlyCompletedMatchIds)

        // Enqueue players from newly completed matches for ELO sync
        val playerIds = GameService.getPlayerIdsFromMatches(newlyCompletedMatchIds)
        logger.info("MatchPollJob: syncing ELO for ${playerIds.size} players from completed matches")
        for (playerId in playerIds) {
            try {
                ClickTTSyncService.syncPlayer(playerId, seasonId)
            } catch (e: Exception) {
                logger.warn("MatchPollJob: ELO sync failed for player $playerId: ${e.message}")
            }
            delay(500L)
        }

        // Send push notifications for affected players
        sendPlayerPushNotifications(playerIds)

        logger.info(
            "MatchPollJob: complete — ${newlyCompletedMatchIds.size} new matches, ${playerIds.size} players synced",
        )
    }

    private suspend fun sendMatchPushNotifications(matchIds: Set<UUID>) {
        data class MatchInfo(
            val id: UUID,
            val homeScore: Short?,
            val awayScore: Short?,
            val homeTeamId: UUID,
            val awayTeamId: UUID,
            val groupId: UUID,
        )

        val matchInfos =
            transaction {
                Matches.selectAll()
                    .where { Matches.id inList matchIds }
                    .map {
                        MatchInfo(
                            id = it[Matches.id],
                            homeScore = it[Matches.homeScore],
                            awayScore = it[Matches.awayScore],
                            homeTeamId = it[Matches.homeTeamId],
                            awayTeamId = it[Matches.awayTeamId],
                            groupId = it[Matches.groupId],
                        )
                    }
            }

        val teamIds = matchInfos.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        val teamNames: Map<UUID, String> =
            transaction {
                Teams.select(Teams.id, Teams.name)
                    .where { Teams.id inList teamIds }
                    .associate { it[Teams.id] to it[Teams.name] }
            }

        for (m in matchInfos) {
            val homeTeam = teamNames[m.homeTeamId] ?: continue
            val awayTeam = teamNames[m.awayTeamId] ?: continue
            val score =
                if (m.homeScore != null && m.awayScore != null) {
                    "${m.homeScore}:${m.awayScore}"
                } else {
                    "—:—"
                }
            val title = "$homeTeam vs $awayTeam"
            val body = "Result: $score"
            val url = "/matches/${m.id}"
            try {
                PushService.sendToFollowers(FollowTargetType.TEAM, m.homeTeamId, title, body, url)
                PushService.sendToFollowers(FollowTargetType.TEAM, m.awayTeamId, title, body, url)
                PushService.sendToFollowers(FollowTargetType.DIVISION_GROUP, m.groupId, title, body, url)
            } catch (e: Exception) {
                logger.warn("MatchPollJob: push notification failed for match ${m.id}: ${e.message}")
            }
        }
    }

    private suspend fun sendPlayerPushNotifications(playerIds: Collection<UUID>) {
        val playerNames =
            transaction {
                Players.select(Players.id, Players.fullName)
                    .where { Players.id inList playerIds }
                    .associate { it[Players.id] to it[Players.fullName] }
            }

        for ((playerId, playerName) in playerNames) {
            try {
                PushService.sendToFollowers(
                    targetType = FollowTargetType.PLAYER,
                    targetId = playerId,
                    title = playerName,
                    body = "New match result available",
                    url = "/players/$playerId",
                )
            } catch (e: Exception) {
                logger.warn("MatchPollJob: push notification failed for player $playerId: ${e.message}")
            }
        }
    }

    private fun findGroupsWithPastDueMatches(): List<GroupRef> =
        transaction {
            val now = OffsetDateTime.now()
            (Groups innerJoin Federations innerJoin Seasons innerJoin Matches)
                .select(Groups.id, Groups.clickttId, Federations.name, Seasons.name)
                .where {
                    (Matches.status eq MatchStatus.SCHEDULED) and
                        (Matches.playedAt less now) and
                        (Groups.clickttId.isNotNull())
                }
                .withDistinct()
                .map {
                    GroupRef(
                        dbId = it[Groups.id],
                        clickttId = it[Groups.clickttId]!!,
                        championship = ClickTTGroupScraper.toChampionship(it[Federations.name], it[Seasons.name]),
                    )
                }
        }

    companion object {
        fun create(): MatchPollJob {
            val client = ClickTTClient()
            val parser = ClickTTParser()
            return MatchPollJob(
                groupScraper = ClickTTGroupScraper(client, parser),
                matchScraper = ClickTTMatchScraper(client, parser),
            )
        }
    }
}
