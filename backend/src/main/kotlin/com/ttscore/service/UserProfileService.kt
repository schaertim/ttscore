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
                    val proUntil = row[UserProfiles.proUntil]
                    UserProfileResponse(
                        homePlayerId = row[UserProfiles.homePlayerId]?.toString(),
                        homePlayerName = row.getOrNull(Players.fullName),
                        isPro = isProAt(proUntil),
                        proUntil = proUntil?.toString(),
                    )
                }
                ?: UserProfileResponse(homePlayerId = null, homePlayerName = null)
        }

    /** True if the user currently holds an active Pro entitlement (`pro_until` in the future). */
    suspend fun isPro(userId: String): Boolean =
        dbQuery {
            val until =
                UserProfiles.select(UserProfiles.proUntil)
                    .where { UserProfiles.userId eq userId }
                    .firstOrNull()
                    ?.get(UserProfiles.proUntil)
            isProAt(until)
        }

    private fun isProAt(proUntil: OffsetDateTime?): Boolean =
        proUntil != null && proUntil.isAfter(OffsetDateTime.now())

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

    /** Returns the user's home player id, or null if none is set / no profile exists. */
    suspend fun getHomePlayerId(userId: String): UUID? =
        dbQuery {
            UserProfiles.select(UserProfiles.homePlayerId)
                .where { UserProfiles.userId eq userId }
                .firstOrNull()
                ?.get(UserProfiles.homePlayerId)
        }

    /** Clears the home player for a user. No-op if no profile exists. */
    suspend fun removeHomePlayer(userId: String) =
        dbQuery {
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[homePlayerId] = null
            }
        }
}
