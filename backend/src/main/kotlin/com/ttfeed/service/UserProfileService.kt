package com.ttfeed.service

import com.ttfeed.database.Players
import com.ttfeed.database.UserProfiles
import com.ttfeed.database.dbQuery
import com.ttfeed.model.UserProfileResponse
import org.jetbrains.exposed.sql.*
import java.time.OffsetDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

object UserProfileService {

    /** Returns the profile for a user, or an empty profile if none exists yet. */
    suspend fun getProfile(userId: String): UserProfileResponse = dbQuery {
        UserProfiles
            .leftJoin(Players, { UserProfiles.homePlayerId }, { Players.id })
            .selectAll()
            .where { UserProfiles.userId eq userId }
            .firstOrNull()
            ?.let { row ->
                UserProfileResponse(
                    homePlayerId = row[UserProfiles.homePlayerId]?.toString(),
                    homePlayerName = row.getOrNull(Players.fullName),
                )
            }
            ?: UserProfileResponse(homePlayerId = null, homePlayerName = null)
    }

    /** Sets (or replaces) the home player for a user. Creates the profile row if needed. */
    suspend fun setHomePlayer(userId: String, playerId: UUID) = dbQuery {
        val exists = UserProfiles.selectAll().where { UserProfiles.userId eq userId }.any()
        if (exists) {
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[homePlayerId] = playerId
            }
        } else {
            UserProfiles.insert {
                it[UserProfiles.userId] = userId
                it[homePlayerId] = playerId
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    /** Clears the home player for a user. No-op if no profile exists. */
    suspend fun removeHomePlayer(userId: String) = dbQuery {
        UserProfiles.update({ UserProfiles.userId eq userId }) {
            it[homePlayerId] = null
        }
    }
}
