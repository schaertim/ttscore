package com.ttscore.jobs

import com.ttscore.database.Federations
import com.ttscore.database.Games
import com.ttscore.database.Groups
import com.ttscore.database.Matches
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.model.FollowTargetType
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.clicktt.ClickTTClient
import com.ttscore.scraper.clicktt.ClickTTGroupScraper
import com.ttscore.scraper.clicktt.ClickTTGroupScraper.GroupRef
import com.ttscore.scraper.clicktt.ClickTTMatchScraper
import com.ttscore.scraper.clicktt.ClickTTParser
import com.ttscore.scraper.clicktt.ClickTTSyncService
import com.ttscore.service.GameService
import com.ttscore.service.PushService
import com.ttscore.service.SeasonService
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
        val groupsWithCompletions = mutableListOf<GroupRef>()
        for (group in groups) {
            try {
                val completed = groupScraper.refreshGroupSchedule(group)
                newlyCompletedMatchIds.addAll(completed)
                if (completed.isNotEmpty()) groupsWithCompletions.add(group)
            } catch (e: Exception) {
                logger.error("MatchPollJob: failed to refresh group ${group.clickttId}: ${e.message}")
            }
        }

        if (newlyCompletedMatchIds.isEmpty()) {
            logger.debug("MatchPollJob: no newly completed matches found")
            return
        }

        logger.info("MatchPollJob: ${newlyCompletedMatchIds.size} matches completed scraping game details")
        matchScraper.scrapeForMatches(newlyCompletedMatchIds)

        logger.info("MatchPollJob: refreshing standings for ${groupsWithCompletions.size} groups")
        for (group in groupsWithCompletions) {
            try {
                groupScraper.refreshGroupStandings(group)
            } catch (e: Exception) {
                logger.error("MatchPollJob: failed to refresh standings for group ${group.clickttId}: ${e.message}")
            }
        }

        // Players who took part in the newly completed matches.
        val playerIds = GameService.getPlayerIdsFromMatches(newlyCompletedMatchIds)

        // Send push notifications for newly completed matches — to followers of the
        // teams, the division group, and the players who played in them.
        sendMatchPushNotifications(newlyCompletedMatchIds, playerIds)

        // Enqueue those players for ELO sync
        logger.info("MatchPollJob: syncing ELO for ${playerIds.size} players from completed matches")
        for (playerId in playerIds) {
            try {
                ClickTTSyncService.syncPlayer(playerId, seasonId)
            } catch (e: Exception) {
                logger.warn("MatchPollJob: ELO sync failed for player $playerId: ${e.message}")
            }
            delay(500L)
        }

        logger.info(
            "MatchPollJob: complete — ${newlyCompletedMatchIds.size} new matches, ${playerIds.size} players synced",
        )
    }

    /** WIN/LOSS/DRAW phrasing — mirrors the frontend feed's feed.won/lost/drew (`feed.ts`, `FeedItemCard.svelte`). */
    private enum class Outcome { WIN, LOSS, DRAW }

    private fun Outcome.word() =
        when (this) {
            Outcome.WIN -> "Won"
            Outcome.LOSS -> "Lost"
            Outcome.DRAW -> "Drew"
        }

    private fun outcomeOf(
        my: Int,
        opp: Int,
    ) = when {
        my > opp -> Outcome.WIN
        my < opp -> Outcome.LOSS
        else -> Outcome.DRAW
    }

    private suspend fun sendMatchPushNotifications(
        matchIds: Set<UUID>,
        playerIds: Set<UUID>,
    ) {
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
        val matchInfoById = matchInfos.associateBy { it.id }

        val teamIds = matchInfos.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        val groupIds = matchInfos.map { it.groupId }.toSet()
        val teamNames: Map<UUID, String> =
            transaction {
                Teams.select(Teams.id, Teams.name)
                    .where { Teams.id inList teamIds }
                    .associate { it[Teams.id] to it[Teams.name] }
            }
        val groupNames: Map<UUID, String> =
            transaction {
                Groups.select(Groups.id, Groups.name)
                    .where { Groups.id inList groupIds }
                    .associate { it[Groups.id] to it[Groups.name] }
            }

        // Team-of-interest score, in "my–opponent" order, matching the feed's "Won 6–4 vs X" wording.
        for (m in matchInfos) {
            val homeTeam = teamNames[m.homeTeamId] ?: continue
            val awayTeam = teamNames[m.awayTeamId] ?: continue
            if (m.homeScore == null || m.awayScore == null) continue
            val groupName = groupNames[m.groupId] ?: "Division"
            val url = "/matches/${m.id}"

            try {
                val homeOutcome = outcomeOf(m.homeScore.toInt(), m.awayScore.toInt())
                PushService.sendToFollowers(
                    FollowTargetType.TEAM,
                    m.homeTeamId,
                    title = homeTeam,
                    body = "${homeOutcome.word()} ${m.homeScore}–${m.awayScore} vs $awayTeam",
                    url = url,
                )

                val awayOutcome = outcomeOf(m.awayScore.toInt(), m.homeScore.toInt())
                PushService.sendToFollowers(
                    FollowTargetType.TEAM,
                    m.awayTeamId,
                    title = awayTeam,
                    body = "${awayOutcome.word()} ${m.awayScore}–${m.homeScore} vs $homeTeam",
                    url = url,
                )

                PushService.sendToFollowers(
                    FollowTargetType.DIVISION_GROUP,
                    m.groupId,
                    title = groupName,
                    body = "$homeTeam ${m.homeScore}–${m.awayScore} $awayTeam",
                    url = url,
                )
            } catch (e: Exception) {
                logger.warn("MatchPollJob: push notification failed for match ${m.id}: ${e.message}")
            }
        }

        // Player-of-interest score — this player's own singles record within the match (not the
        // team's full score), e.g. "Won 2–1 vs Team B". Mirrors the byMatch aggregation in the
        // frontend's feed.ts (fetchPlayerEvents), which is also singles-only and per-player.
        val singlesGames =
            transaction {
                Games.select(Games.matchId, Games.homePlayer1Id, Games.awayPlayer1Id, Games.result)
                    .where { (Games.matchId inList matchIds) and (Games.gameType eq GameType.SINGLES) }
                    .toList()
            }
        val gamesByMatch = singlesGames.groupBy { it[Games.matchId] }

        val playerNames =
            transaction {
                Players.select(Players.id, Players.fullName)
                    .where { Players.id inList playerIds }
                    .associate { it[Players.id] to it[Players.fullName] }
            }

        for ((matchId, games) in gamesByMatch) {
            val matchInfo = matchInfoById[matchId] ?: continue
            val homeTeam = teamNames[matchInfo.homeTeamId] ?: continue
            val awayTeam = teamNames[matchInfo.awayTeamId] ?: continue

            val participants =
                games.flatMap { listOfNotNull(it[Games.homePlayer1Id], it[Games.awayPlayer1Id]) }
                    .toSet()
                    .filter { it in playerIds }

            for (playerId in participants) {
                val playerName = playerNames[playerId] ?: continue
                // Only this player's own games — a team match has several players, each playing
                // their own singles games, so this must not be confused with the team's full score.
                val playerGames =
                    games.filter { it[Games.homePlayer1Id] == playerId || it[Games.awayPlayer1Id] == playerId }
                val isHome = playerGames.first()[Games.homePlayer1Id] == playerId
                val myWins = playerGames.count { it[Games.result] == if (isHome) GameResult.HOME else GameResult.AWAY }
                val oppWins = playerGames.count { it[Games.result] == if (isHome) GameResult.AWAY else GameResult.HOME }
                val opponentTeam = if (isHome) awayTeam else homeTeam

                try {
                    // No win/loss verb — this is the player's own record across their games in the
                    // match (e.g. "2–1"), not a single verdict. Matches the frontend feed's
                    // player_match wording (FeedItemCard.svelte).
                    PushService.sendToFollowers(
                        targetType = FollowTargetType.PLAYER,
                        targetId = playerId,
                        title = playerName,
                        body = "$myWins–$oppWins vs $opponentTeam",
                        url = "/players/$playerId",
                    )
                } catch (e: Exception) {
                    logger.warn("MatchPollJob: push notification failed for player $playerId: ${e.message}")
                }
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
