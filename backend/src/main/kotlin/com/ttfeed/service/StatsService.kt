package com.ttfeed.service

import com.ttfeed.database.Matches
import com.ttfeed.database.PlayerSeasons
import com.ttfeed.database.Seasons
import com.ttfeed.database.dbQuery
import com.ttfeed.model.MatchStatus
import com.ttfeed.model.StatsResponse
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.countDistinct
import java.time.OffsetDateTime
import java.time.ZoneId

object StatsService {
    suspend fun getStats(seasonName: String): StatsResponse =
        dbQuery {
            val distinctPlayerCount = PlayerSeasons.playerId.countDistinct()
            val registeredPlayers =
                PlayerSeasons
                    .join(Seasons, JoinType.INNER, PlayerSeasons.seasonId, Seasons.id)
                    .select(distinctPlayerCount)
                    .where { Seasons.name eq seasonName }
                    .first()[distinctPlayerCount]

            val cutoff = OffsetDateTime.now(ZoneId.of("Europe/Zurich")).minusHours(24)
            val matchesLast24h =
                Matches
                    .select(Matches.id)
                    .where {
                        (Matches.status eq MatchStatus.COMPLETED) and
                            (Matches.playedAt greaterEq cutoff)
                    }
                    .count()

            StatsResponse(
                registeredPlayers = registeredPlayers,
                matchesLast24h = matchesLast24h,
            )
        }
}
