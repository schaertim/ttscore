package com.ttscore.service

import com.ttscore.database.Players
import com.ttscore.database.UserProfiles
import com.ttscore.database.dbQuery
import com.ttscore.model.UserProfileResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.OffsetDateTime
import java.util.UUID

object UserProfileService {
    /** Returns the profile for a user, or an empty profile if none exists yet. */
    suspend fun getProfile(userId: String): UserProfileResponse =
        dbQuery {
            UserProfiles
                .leftJoin(Players, { UserProfiles.homePlayerId }, { Players.id })
                .selectAll()
                .where { UserProfiles.userId eq userId }
                .firstOrNull()
                ?.let { row ->
                    UserProfileResponse(
                        homePlayerId = row[UserProfiles.homePlayerId]?.toString(),
                        homePlayerName = row.getOrNull(Players.fullName),
                        notificationsPaused = row[UserProfiles.notificationsPaused],
                    )
                }
                ?: UserProfileResponse(homePlayerId = null, homePlayerName = null)
        }

    /** Sets (or replaces) the home player for a user. Creates the profile row if needed. */
    suspend fun setHomePlayer(
        userId: String,
        playerId: UUID,
    ) = dbQuery {
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
    suspend fun removeHomePlayer(userId: String) =
        dbQuery {
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[homePlayerId] = null
            }
        }

    /** Sets the global "pause all notifications" flag. Creates the profile row if needed. */
    suspend fun setNotificationsPaused(
        userId: String,
        paused: Boolean,
    ) = dbQuery {
        val exists = UserProfiles.selectAll().where { UserProfiles.userId eq userId }.any()
        if (exists) {
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[notificationsPaused] = paused
            }
        } else {
            UserProfiles.insert {
                it[UserProfiles.userId] = userId
                it[notificationsPaused] = paused
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }
}
