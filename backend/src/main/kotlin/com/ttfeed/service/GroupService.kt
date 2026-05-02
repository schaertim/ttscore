package com.ttfeed.service

import com.ttfeed.database.*
import com.ttfeed.model.GroupResponse
import com.ttfeed.model.MatchStatus
import com.ttfeed.util.toUuidOrNull
import org.jetbrains.exposed.sql.*

object GroupService {
    suspend fun getAll(
        league: String?,
        season: String?,
    ): List<GroupResponse> =
        dbQuery {
            var query =
                (Groups innerJoin Federations innerJoin Seasons)
                    .select(
                        Groups.id,
                        Groups.name,
                        Federations.name,
                        Seasons.name,
                        Groups.promotionSpots,
                        Groups.relegationSpots,
                    )
            if (league != null) query = query.andWhere { Federations.name eq league }
            if (season != null) query = query.andWhere { Seasons.name eq season }
            val rows = query.toList()
            val groupIds = rows.map { it[Groups.id] }

            val teamCounts =
                Teams
                    .select(Teams.groupId, Teams.id.count())
                    .where { Teams.groupId inList groupIds }
                    .groupBy(Teams.groupId)
                    .associate { it[Teams.groupId] to it[Teams.id.count()].toInt() }

            val totalRoundsMap =
                Matches
                    .select(Matches.groupId, Matches.round.countDistinct())
                    .where { (Matches.groupId inList groupIds) and Matches.round.isNotNull() }
                    .groupBy(Matches.groupId)
                    .associate { it[Matches.groupId] to it[Matches.round.countDistinct()].toInt() }

            val playedRoundsMap =
                Matches
                    .select(Matches.groupId, Matches.round.countDistinct())
                    .where {
                        (Matches.groupId inList groupIds) and
                            Matches.round.isNotNull() and
                            (Matches.status eq MatchStatus.COMPLETED)
                    }
                    .groupBy(Matches.groupId)
                    .associate { it[Matches.groupId] to it[Matches.round.countDistinct()].toInt() }

            rows.map { row ->
                val gid = row[Groups.id]
                row.toGroupResponse(
                    teamCount = teamCounts[gid] ?: 0,
                    roundsPlayed = playedRoundsMap[gid] ?: 0,
                    totalRounds = totalRoundsMap[gid] ?: 0,
                )
            }
        }

    suspend fun getById(groupId: String): GroupResponse? {
        val uuid = groupId.toUuidOrNull() ?: return null
        return dbQuery {
            val row =
                (Groups innerJoin Federations innerJoin Seasons)
                    .select(
                        Groups.id, Groups.name,
                        Federations.name, Seasons.name,
                        Groups.promotionSpots, Groups.relegationSpots,
                    )
                    .where { Groups.id eq uuid }
                    .firstOrNull() ?: return@dbQuery null

            val teamCount =
                Teams
                    .select(Teams.id.count())
                    .where { Teams.groupId eq uuid }
                    .first()[Teams.id.count()].toInt()

            val totalRounds =
                Matches
                    .select(Matches.round.countDistinct())
                    .where { (Matches.groupId eq uuid) and Matches.round.isNotNull() }
                    .first()[Matches.round.countDistinct()].toInt()

            val roundsPlayed =
                Matches
                    .select(Matches.round.countDistinct())
                    .where {
                        (Matches.groupId eq uuid) and
                            Matches.round.isNotNull() and
                            (Matches.status eq MatchStatus.COMPLETED)
                    }
                    .first()[Matches.round.countDistinct()].toInt()

            row.toGroupResponse(
                teamCount = teamCount,
                roundsPlayed = roundsPlayed,
                totalRounds = totalRounds,
            )
        }
    }

    private fun ResultRow.toGroupResponse(
        teamCount: Int,
        roundsPlayed: Int,
        totalRounds: Int,
    ) = GroupResponse(
        id = this[Groups.id].toString(),
        name = this[Groups.name],
        federation = this[Federations.name],
        season = this[Seasons.name],
        promotionSpots = this[Groups.promotionSpots]?.toInt(),
        relegationSpots = this[Groups.relegationSpots]?.toInt(),
        teamCount = teamCount,
        roundsPlayed = roundsPlayed,
        totalRounds = totalRounds,
    )
}
