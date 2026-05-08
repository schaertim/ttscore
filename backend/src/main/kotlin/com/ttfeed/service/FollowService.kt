package com.ttfeed.service

import com.ttfeed.database.Follows
import com.ttfeed.database.Groups
import com.ttfeed.database.Players
import com.ttfeed.database.Teams
import com.ttfeed.database.dbQuery
import com.ttfeed.model.FollowCheckResponse
import com.ttfeed.model.FollowResponse
import com.ttfeed.model.FollowTargetType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.OffsetDateTime
import java.util.UUID

object FollowService {

    /** Returns all notification subscriptions for a user. */
    suspend fun getFollows(userId: String): List<FollowResponse> = dbQuery {
        Follows.selectAll()
            .where { Follows.userId eq userId }
            .orderBy(Follows.createdAt)
            .mapNotNull { row ->
                val type = row[Follows.targetType]
                val targetId = row[Follows.targetId]
                val name = resolveTargetName(type, targetId) ?: return@mapNotNull null
                FollowResponse(
                    id = row[Follows.id].toString(),
                    targetType = type.name.lowercase(),
                    targetId = targetId.toString(),
                    targetName = name,
                )
            }
    }

    /** Returns whether the user is subscribed to notifications for the given target. */
    suspend fun check(userId: String, targetType: FollowTargetType, targetId: UUID): FollowCheckResponse =
        dbQuery {
            val row = Follows.selectAll()
                .where {
                    (Follows.userId eq userId) and
                        (Follows.targetType eq targetType) and
                        (Follows.targetId eq targetId)
                }
                .firstOrNull()
            FollowCheckResponse(
                notifying = row != null,
                notifyId = row?.get(Follows.id)?.toString(),
            )
        }

    /**
     * Subscribes the user to notifications for the target.
     * Idempotent — returns the existing row if already subscribed.
     * Returns null if the target entity does not exist.
     */
    suspend fun follow(userId: String, targetType: FollowTargetType, targetId: UUID): FollowResponse? = dbQuery {
        val existing = Follows.selectAll()
            .where {
                (Follows.userId eq userId) and
                    (Follows.targetType eq targetType) and
                    (Follows.targetId eq targetId)
            }
            .firstOrNull()

        val name = resolveTargetName(targetType, targetId) ?: return@dbQuery null

        if (existing != null) {
            return@dbQuery FollowResponse(
                id = existing[Follows.id].toString(),
                targetType = targetType.name.lowercase(),
                targetId = targetId.toString(),
                targetName = name,
            )
        }

        val id = Follows.insert {
            it[Follows.userId] = userId
            it[Follows.targetType] = targetType
            it[Follows.targetId] = targetId
            it[createdAt] = OffsetDateTime.now()
        }[Follows.id]

        FollowResponse(
            id = id.toString(),
            targetType = targetType.name.lowercase(),
            targetId = targetId.toString(),
            targetName = name,
        )
    }

    /** Unsubscribes. Returns false if not found or not owned by the user. */
    suspend fun unfollow(userId: String, followId: UUID): Boolean = dbQuery {
        Follows.deleteWhere {
            (Follows.id eq followId) and (Follows.userId eq userId)
        } > 0
    }

    // ─────────────────────────────────────────────────────────────────────

    internal fun resolveTargetName(type: FollowTargetType, targetId: UUID): String? =
        when (type) {
            FollowTargetType.PLAYER ->
                Players.selectAll().where { Players.id eq targetId }.firstOrNull()?.get(Players.fullName)
            FollowTargetType.TEAM ->
                Teams.selectAll().where { Teams.id eq targetId }.firstOrNull()?.get(Teams.name)
            FollowTargetType.DIVISION_GROUP ->
                Groups.selectAll().where { Groups.id eq targetId }.firstOrNull()?.get(Groups.name)
        }
}
