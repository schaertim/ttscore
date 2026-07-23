package com.ttscore.scraper.clicktt

import com.ttscore.database.*
import com.ttscore.model.MatchStatus
import com.ttscore.scraper.clicktt.model.ParsedClickTTGroup
import com.ttscore.scraper.clicktt.model.ParsedClickTTMatch
import com.ttscore.scraper.clicktt.model.ParsedClickTTParticipant
import com.ttscore.scraper.clicktt.model.ParsedClickTTStanding
import com.ttscore.scraper.knob.FEDERATION_RVIDS
import com.ttscore.scraper.knob.SCRAPE_CONCURRENCY
import com.ttscore.scraper.knob.mapConcurrent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class ClickTTGroupScraper(
    private val client: ClickTTClient,
    private val parser: ClickTTParser,
) {
    private val logger = LoggerFactory.getLogger(ClickTTGroupScraper::class.java)

    /**
     * Scrapes all federations for the given season.
     * Season format: "2025/2026" → championship "MTTV 25/26" etc.
     */
    suspend fun run(
        season: String = "2025/2026",
        federations: Collection<String> = FEDERATION_RVIDS.keys,
    ) {
        logger.info("ClickTTGroupScraper: starting season $season (federations: ${federations.joinToString()})")

        for (federationName in federations) {
            val championship = toChampionship(federationName, season)
            try {
                scrapeFederation(championship, federationName, season)
            } catch (e: Exception) {
                logger.error("Failed to scrape $championship: ${e.message}")
            }
        }

        logger.info("ClickTTGroupScraper: complete for season $season")
    }

    private suspend fun scrapeFederation(
        championship: String,
        federationName: String,
        season: String,
    ) {
        logger.info("  $championship — fetching league page")
        val html = client.fetchLeaguePage(championship)
        val groups = parser.parseLeaguePage(html, championship)

        if (groups.isEmpty()) {
            logger.info("  $championship — no groups found (federation may not exist on click-tt)")
            return
        }

        logger.info("  $championship — ${groups.size} groups")

        // Pre-filter already-scraped groups with cheap serial DB reads (no HTML dependency),
        // then fetch the remaining groups' standings+schedule pages concurrently.
        val pending = groups.filterNot { isAlreadyScraped(it.groupId) }

        val fetched =
            pending.mapConcurrent(SCRAPE_CONCURRENCY) { group ->
                try {
                    // Standings gives us teams, positions, and promotion/relegation zones
                    val standingsHtml = client.fetchGroupPage(group.championship, group.groupId)
                    val standings = parser.parseGroupStandings(standingsHtml)

                    // Schedule gives us all matches (completed + upcoming) — always fetched
                    val scheduleHtml =
                        client.fetchGroupPage(group.championship, group.groupId, displayDetail = "meetings")
                    val matches = parser.parseMatchSchedule(scheduleHtml)

                    // Cup/final/playoff groups have no standings table but do list their entrants in a
                    // "Teilnehmende Mannschaften" table (with teamtable ids) — parsed only when needed.
                    val participants =
                        if (standings.isEmpty()) {
                            parser.parseParticipatingTeams(standingsHtml)
                                .ifEmpty { parser.parseParticipatingTeams(scheduleHtml) }
                        } else {
                            emptyList()
                        }

                    GroupPageData(group, standings, participants, matches)
                } catch (e: Exception) {
                    logger.error("    group=${group.groupId} (${group.divisionName}) failed: ${e.message}")
                    null
                }
            }.filterNotNull()

        for ((group, standings, participants, matches) in fetched) {
            try {
                if (standings.isEmpty()) {
                    // No standings table → cup/final/playoff group. Its teams come from the
                    // "Teilnehmende Mannschaften" table, keyed by their globally-unique teamtable id
                    // (created/resolved exactly like standings teams) — never by name, which would
                    // conflate same-named teams across unrelated competitions/federations.
                    if (matches.isEmpty() || participants.isEmpty()) {
                        logger.debug(
                            "    group=${group.groupId} (${group.divisionName}) — no standings, " +
                                "${participants.size} participants, ${matches.size} matches — skipping",
                        )
                        continue
                    }

                    transaction {
                        val seasonId = upsertSeason(season)
                        val federationId = upsertFederation(federationName)
                        val groupId = upsertGroup(federationId, seasonId, group.divisionName, group.groupId)

                        val teamIdByName =
                            participants.associate { it.teamName to upsertTeamByTableId(it.teamName, it.teamTableId, groupId) }

                        upsertMatchesReturningCompleted(matches, groupId, teamIdByName)
                    }

                    logger.info(
                        "    group=${group.groupId} → ${group.divisionName} — " +
                            "cup/playoff group, ${participants.size} teams, ${matches.size} matches (no standings)",
                    )
                    continue
                }

                transaction {
                    val seasonId = upsertSeason(season)
                    val federationId = upsertFederation(federationName)
                    val groupId = upsertGroup(federationId, seasonId, group.divisionName, group.groupId)

                    // teamIdByTableId: clicktt teamtable ID → DB UUID
                    val teamIdByTableId = upsertTeams(standings, groupId)
                    // teamIdByName: team name → DB UUID (used to resolve match schedule rows)
                    val teamIdByName: Map<String, UUID> =
                        standings
                            .mapNotNull { s -> teamIdByTableId[s.teamTableId]?.let { s.teamName to it } }
                            .toMap()

                    upsertStandings(standings, groupId, teamIdByTableId)
                    updateGroupZones(groupId, standings)
                    upsertMatchesReturningCompleted(matches, groupId, teamIdByName)
                }

                logger.info(
                    "    group=${group.groupId} → ${group.divisionName} — " +
                        "${standings.size} teams, ${matches.size} matches",
                )
            } catch (e: Exception) {
                logger.error("    group=${group.groupId} (${group.divisionName}) failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB upserts
    // -------------------------------------------------------------------------

    private fun isAlreadyScraped(clickttGroupId: Int): Boolean =
        transaction {
            Groups.select(Groups.id)
                .where { Groups.clickttId eq clickttGroupId }
                .firstOrNull()
                ?.let { row -> Teams.selectAll().where { Teams.groupId eq row[Groups.id] }.count() > 0 }
                ?: false
        }

    private fun upsertSeason(name: String): UUID {
        Seasons.insertIgnore { it[Seasons.name] = name }
        return Seasons.select(Seasons.id).where { Seasons.name eq name }.first()[Seasons.id]
    }

    private fun upsertFederation(name: String): UUID {
        Federations.insertIgnore { it[Federations.name] = name }
        return Federations.select(Federations.id).where { Federations.name eq name }.first()[Federations.id]
    }

    private fun upsertGroup(
        federationId: UUID,
        seasonId: UUID,
        name: String,
        clickttId: Int,
    ): UUID {
        Groups.insertIgnore {
            it[Groups.federationId] = federationId
            it[Groups.seasonId] = seasonId
            it[Groups.name] = name
            it[Groups.clickttId] = clickttId
            // knobGruppe left null for click-tt groups
        }
        return Groups.select(Groups.id)
            .where { Groups.clickttId eq clickttId }
            .first()[Groups.id]
    }

    /**
     * Inserts or retrieves teams from the standings list.
     * Returns a map of clicktt teamTableId → DB team UUID.
     */
    private fun upsertTeams(
        standings: List<ParsedClickTTStanding>,
        groupId: UUID,
    ): Map<Int, UUID> = standings.associate { it.teamTableId to upsertTeamByTableId(it.teamName, it.teamTableId, groupId) }

    /**
     * Inserts or retrieves a single team by its globally-unique click-tt teamtable id.
     * Shared by the standings path and the participating-teams path (cup/final groups).
     */
    private fun upsertTeamByTableId(
        teamName: String,
        teamTableId: Int,
        groupId: UUID,
    ): UUID {
        // Derive club name using the same trailing-suffix stripping as GroupScraper
        val cleanClubName = teamName.replace(Regex("""\s+(\d+|[IVX]+|[a-zA-Z])$"""), "").trim()

        Clubs.insertIgnore { it[Clubs.name] = cleanClubName }
        val clubId =
            Clubs.select(Clubs.id)
                .where { Clubs.name eq cleanClubName }
                .first()[Clubs.id]

        // Teams with a clickttId are globally unique — no groupId scoping needed
        val existingId =
            Teams.select(Teams.id)
                .where { Teams.clickttId eq teamTableId }
                .firstOrNull()?.get(Teams.id)

        return existingId ?: run {
            Teams.insert {
                it[Teams.name] = teamName
                it[Teams.clubId] = clubId
                it[Teams.groupId] = groupId
                it[Teams.clickttId] = teamTableId
            }
            Teams.select(Teams.id)
                .where { Teams.clickttId eq teamTableId }
                .first()[Teams.id]
        }
    }

    private fun upsertStandings(
        standings: List<ParsedClickTTStanding>,
        groupId: UUID,
        teamIdByTableId: Map<Int, UUID>,
    ) {
        for (standing in standings) {
            val teamId = teamIdByTableId[standing.teamTableId] ?: continue
            Standings.upsert(Standings.groupId, Standings.teamId) {
                it[Standings.groupId] = groupId
                it[Standings.teamId] = teamId
                it[Standings.position] = standing.position.toShort()
                it[Standings.played] = standing.played.toShort()
                it[Standings.won] = standing.won.toShort()
                it[Standings.drawn] = standing.drawn.toShort()
                it[Standings.lost] = standing.lost.toShort()
                it[Standings.gamesFor] = standing.gamesFor.toShort()
                it[Standings.gamesAgainst] = standing.gamesAgainst.toShort()
                it[Standings.points] = standing.points.toShort()
            }
        }
    }

    private fun updateGroupZones(
        groupId: UUID,
        standings: List<ParsedClickTTStanding>,
    ) {
        val promotionSpots = standings.count { it.isPromotion }
        val relegationSpots = standings.count { it.isRelegation }
        if (promotionSpots > 0 || relegationSpots > 0) {
            Groups.update({ Groups.id eq groupId }) {
                it[Groups.promotionSpots] = promotionSpots.toShort()
                it[Groups.relegationSpots] = relegationSpots.toShort()
            }
        }
    }

    /**
     * Re-fetches the match schedule for an already-known group and updates match statuses.
     * Returns the DB UUIDs of matches that transitioned from SCHEDULED to COMPLETED.
     */
    suspend fun refreshGroupSchedule(group: GroupRef): List<UUID> {
        val scheduleHtml = client.fetchGroupPage(group.championship, group.clickttId, displayDetail = "meetings")
        val matches = parser.parseMatchSchedule(scheduleHtml)

        return transaction {
            val teamIdByName =
                Teams.select(Teams.id, Teams.name)
                    .where { Teams.groupId eq group.dbId }
                    .associate { it[Teams.name] to it[Teams.id] }

            upsertMatchesReturningCompleted(matches, group.dbId, teamIdByName)
        }
    }

    /** Re-fetches standings for an already-known group and updates the DB. */
    suspend fun refreshGroupStandings(group: GroupRef) {
        val standingsHtml = client.fetchGroupPage(group.championship, group.clickttId)
        val standings = parser.parseGroupStandings(standingsHtml)
        if (standings.isEmpty()) return

        transaction {
            val teamIdByTableId =
                standings.mapNotNull { s ->
                    Teams.select(Teams.id)
                        .where { Teams.clickttId eq s.teamTableId }
                        .firstOrNull()
                        ?.let { s.teamTableId to it[Teams.id] }
                }.toMap()

            upsertStandings(standings, group.dbId, teamIdByTableId)
            updateGroupZones(group.dbId, standings)
        }
    }

    /**
     * Inserts new matches and updates completed ones. Returns DB UUIDs of matches that
     * transitioned from SCHEDULED to COMPLETED in this call.
     */
    private fun upsertMatchesReturningCompleted(
        matches: List<ParsedClickTTMatch>,
        groupId: UUID,
        teamIdByName: Map<String, UUID>,
    ): List<UUID> {
        val newlyCompleted = mutableListOf<UUID>()

        for (match in matches) {
            val homeTeamId = teamIdByName[match.homeTeamName] ?: continue
            val awayTeamId = teamIdByName[match.awayTeamName] ?: continue
            val playedAt = parseMatchDateTime(match.date, match.time)

            // meetingId is globally unique when not null — use it for duplicate detection
            val exists =
                if (match.meetingId != null) {
                    Matches.select(Matches.id)
                        .where { Matches.clickttMatchId eq match.meetingId }
                        .firstOrNull() != null
                } else {
                    Matches.select(Matches.id)
                        .where {
                            (Matches.groupId eq groupId) and
                                (Matches.homeTeamId eq homeTeamId) and
                                (Matches.awayTeamId eq awayTeamId)
                        }
                        .firstOrNull() != null
                }

            if (!exists) {
                Matches.insert {
                    it[Matches.groupId] = groupId
                    it[Matches.homeTeamId] = homeTeamId
                    it[Matches.awayTeamId] = awayTeamId
                    it[Matches.playedAt] = playedAt
                    it[Matches.homeScore] = match.homeScore?.toShort()
                    it[Matches.awayScore] = match.awayScore?.toShort()
                    it[Matches.clickttMatchId] = match.meetingId
                    it[Matches.round] = match.round
                    it[Matches.status] = match.status
                }
            } else if (match.status == MatchStatus.COMPLETED && match.meetingId != null) {
                // Match just finished — fill in score, meeting ID, and round
                val updated =
                    Matches.update({
                        (Matches.groupId eq groupId) and
                            (Matches.homeTeamId eq homeTeamId) and
                            (Matches.awayTeamId eq awayTeamId) and
                            (Matches.status eq MatchStatus.SCHEDULED)
                    }) {
                        it[Matches.homeScore] = match.homeScore?.toShort()
                        it[Matches.awayScore] = match.awayScore?.toShort()
                        it[Matches.clickttMatchId] = match.meetingId
                        it[Matches.round] = match.round
                        it[Matches.status] = MatchStatus.COMPLETED
                    }
                if (updated > 0) {
                    Matches.select(Matches.id)
                        .where { Matches.clickttMatchId eq match.meetingId }
                        .firstOrNull()
                        ?.get(Matches.id)
                        ?.let { newlyCompleted.add(it) }
                }
            }
        }

        return newlyCompleted
    }

    /** Identifies a group for incremental refresh operations. */
    data class GroupRef(val dbId: UUID, val clickttId: Int, val championship: String)

    /** The parsed pages for one group, carried from the concurrent fetch into the DB-write loop. */
    private data class GroupPageData(
        val group: ParsedClickTTGroup,
        val standings: List<ParsedClickTTStanding>,
        val participants: List<ParsedClickTTParticipant>,
        val matches: List<ParsedClickTTMatch>,
    )

    private fun parseMatchDateTime(
        date: String,
        time: String?,
    ): OffsetDateTime? =
        try {
            val raw = if (time != null) "$date $time" else date
            val fmt =
                if (time != null) {
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                } else {
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                }
            LocalDateTime.parse(raw, fmt).atZone(ZoneId.of("Europe/Zurich")).toOffsetDateTime()
        } catch (e: Exception) {
            null
        }

    companion object {
        /** Converts season "2025/2026" and federation "MTTV" → championship "MTTV 25/26" */
        fun toChampionship(
            federation: String,
            season: String,
        ): String {
            val parts = season.split("/")
            val short = "${parts[0].takeLast(2)}/${parts[1].takeLast(2)}"
            return "$federation $short"
        }
    }
}
