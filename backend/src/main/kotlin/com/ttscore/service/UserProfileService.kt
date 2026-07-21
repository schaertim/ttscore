package com.ttscore.service

import com.ttscore.database.Follows
import com.ttscore.database.Players
import com.ttscore.database.UserProfiles
import com.ttscore.database.dbQuery
import com.ttscore.model.FollowTargetType
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

    /**
     * Sets (or replaces) the home player for a user, moving the "own player" notify
     * flag along with it: the previous home player's notify is turned off (if any) and
     * the new one's is turned on — the free-tier follow cap and Pro gate on notify both
     * already carve out an exception for the home player, so enabling it here is never
     * blocked. Gives new users something in their feed and a working taste of push
     * notifications without an extra manual step, without leaving a stale player
     * notifying forever once they switch.
     */
    suspend fun setHomePlayer(
        userId: String,
        playerId: UUID,
    ) {
        val previousPlayerId =
            dbQuery {
                val previous =
                    UserProfiles.select(UserProfiles.homePlayerId)
                        .where { UserProfiles.userId eq userId }
                        .firstOrNull()
                        ?.get(UserProfiles.homePlayerId)

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
                previous
            }

        if (previousPlayerId != null && previousPlayerId != playerId) {
            setPlayerFollowNotify(userId, previousPlayerId, notify = false)
        }
        setPlayerFollowNotify(userId, playerId, notify = true)
    }

    /**
     * Turns a player follow's notify flag on/off, following the player first if
     * [notify] is true and no follow exists yet. Turning off never creates a follow —
     * nothing to turn off if the user never followed the player in the first place.
     */
    private suspend fun setPlayerFollowNotify(
        userId: String,
        playerId: UUID,
        notify: Boolean,
    ) = dbQuery {
        val existing =
            Follows.selectAll()
                .where {
                    (Follows.userId eq userId) and
                        (Follows.targetType eq FollowTargetType.PLAYER) and
                        (Follows.targetId eq playerId)
                }
                .firstOrNull()

        if (existing != null) {
            Follows.update({ Follows.id eq existing[Follows.id] }) {
                it[Follows.notify] = notify
            }
        } else if (notify) {
            Follows.insert {
                it[Follows.userId] = userId
                it[targetType] = FollowTargetType.PLAYER
                it[targetId] = playerId
                it[Follows.notify] = true
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    /**
     * Sets the Pro entitlement expiry for a user (from the Stripe webhook). A future timestamp
     * grants Pro; null or a past one revokes it. Upserts the profile row so a webhook can never
     * lose a payment just because the profile was created lazily.
     */
    suspend fun setProUntil(
        userId: String,
        proUntil: OffsetDateTime?,
    ) = dbQuery {
        val updated =
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[UserProfiles.proUntil] = proUntil
            }
        if (updated == 0) {
            UserProfiles.insert {
                it[UserProfiles.userId] = userId
                it[UserProfiles.proUntil] = proUntil
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    /** Stores the Stripe customer id for a user (set on first checkout). Upserts the profile row. */
    suspend fun linkStripeCustomer(
        userId: String,
        stripeCustomerId: String,
    ) = dbQuery {
        val updated =
            UserProfiles.update({ UserProfiles.userId eq userId }) {
                it[UserProfiles.stripeCustomerId] = stripeCustomerId
            }
        if (updated == 0) {
            UserProfiles.insert {
                it[UserProfiles.userId] = userId
                it[UserProfiles.stripeCustomerId] = stripeCustomerId
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    /** Returns the user's Stripe customer id, or null if they have never checked out. */
    suspend fun getStripeCustomerId(userId: String): String? =
        dbQuery {
            UserProfiles.select(UserProfiles.stripeCustomerId)
                .where { UserProfiles.userId eq userId }
                .firstOrNull()
                ?.get(UserProfiles.stripeCustomerId)
        }

    /** Resolves a Stripe customer id back to our user id (for subscription webhooks). */
    suspend fun findUserIdByStripeCustomer(stripeCustomerId: String): String? =
        dbQuery {
            UserProfiles.select(UserProfiles.userId)
                .where { UserProfiles.stripeCustomerId eq stripeCustomerId }
                .firstOrNull()
                ?.get(UserProfiles.userId)
        }

    /** Returns the user's home player id, or null if none is set / no profile exists. */
    suspend fun getHomePlayerId(userId: String): UUID? =
        dbQuery {
            UserProfiles.select(UserProfiles.homePlayerId)
                .where { UserProfiles.userId eq userId }
                .firstOrNull()
                ?.get(UserProfiles.homePlayerId)
        }

    /**
     * Clears the home player for a user (no-op if no profile exists) and turns off
     * notify on that player's follow, if any — the follow itself is left alone.
     */
    suspend fun removeHomePlayer(userId: String) {
        val previousPlayerId =
            dbQuery {
                val previous =
                    UserProfiles.select(UserProfiles.homePlayerId)
                        .where { UserProfiles.userId eq userId }
                        .firstOrNull()
                        ?.get(UserProfiles.homePlayerId)
                UserProfiles.update({ UserProfiles.userId eq userId }) {
                    it[homePlayerId] = null
                }
                previous
            }

        if (previousPlayerId != null) {
            setPlayerFollowNotify(userId, previousPlayerId, notify = false)
        }
    }
}
