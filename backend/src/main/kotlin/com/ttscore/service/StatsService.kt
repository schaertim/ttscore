package com.ttscore.service

import com.ttscore.database.Matches
import com.ttscore.database.PlayerSeasons
import com.ttscore.database.Seasons
import com.ttscore.database.dbQuery
import com.ttscore.model.MatchStatus
import com.ttscore.model.StatsResponse
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.countDistinct
import java.time.OffsetDateTime
import java.time.ZoneId

object StatsService {
    suspend fun getStats(seasonName: String): StatsResponse =
        dbQuery {
            val distinctPlayerCount = PlayerSeasons.playerId.countDistinct()
            val activePlayers =
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
                activePlayers = activePlayers,
                matchesLast24h = matchesLast24h,
            )
        }
}
