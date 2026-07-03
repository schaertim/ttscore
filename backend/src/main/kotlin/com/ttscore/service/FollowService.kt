package com.ttscore.service

import com.ttscore.database.Clubs
import com.ttscore.database.Follows
import com.ttscore.database.Groups
import com.ttscore.database.PlayerElos
import com.ttscore.database.PlayerSeasons
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.database.dbQuery
import com.ttscore.model.FollowCheckResponse
import com.ttscore.model.FollowResponse
import com.ttscore.model.FollowTargetType
import com.ttscore.model.PlayerResponse
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

object FollowService {
    /** Returns everything the user follows (star), each with its notify (bell) flag. */
    suspend fun getFollows(userId: String): List<FollowResponse> =
        dbQuery {
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
                        notify = row[Follows.notify],
                    )
                }
        }

    /** Returns whether the user follows the given target, plus its notify flag. */
    suspend fun check(
        userId: String,
        targetType: FollowTargetType,
        targetId: UUID,
    ): FollowCheckResponse =
        dbQuery {
            val row =
                Follows.selectAll()
                    .where {
                        (Follows.userId eq userId) and
                            (Follows.targetType eq targetType) and
                            (Follows.targetId eq targetId)
                    }
                    .firstOrNull()
            FollowCheckResponse(
                following = row != null,
                followId = row?.get(Follows.id)?.toString(),
                notify = row?.get(Follows.notify) ?: false,
            )
        }

    /**
     * Follows the target (notify defaults off). Idempotent — returns the existing
     * row (preserving its notify flag) if already followed. Null if the target
     * entity does not exist.
     */
    suspend fun follow(
        userId: String,
        targetType: FollowTargetType,
        targetId: UUID,
    ): FollowResponse? =
        dbQuery {
            val existing =
                Follows.selectAll()
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
                    notify = existing[Follows.notify],
                )
            }

            val id =
                Follows.insert {
                    it[Follows.userId] = userId
                    it[Follows.targetType] = targetType
                    it[Follows.targetId] = targetId
                    it[Follows.notify] = false
                    it[createdAt] = OffsetDateTime.now()
                }[Follows.id]

            FollowResponse(
                id = id.toString(),
                targetType = targetType.name.lowercase(),
                targetId = targetId.toString(),
                targetName = name,
                notify = false,
            )
        }

    /** Unfollows (also drops any notify). Returns false if not found or not owned by the user. */
    suspend fun unfollow(
        userId: String,
        followId: UUID,
    ): Boolean =
        dbQuery {
            Follows.deleteWhere {
                (Follows.id eq followId) and (Follows.userId eq userId)
            } > 0
        }

    /** Toggles the notify (bell) flag on an existing follow. False if not found or not owned. */
    suspend fun setNotify(
        userId: String,
        followId: UUID,
        notify: Boolean,
    ): Boolean =
        dbQuery {
            Follows.update({ (Follows.id eq followId) and (Follows.userId eq userId) }) {
                it[Follows.notify] = notify
            } > 0
        }

    /**
     * Returns full [PlayerResponse] objects for all players the user follows.
     * Uses the same batch-join pattern as [PlayerService.search] — two queries, no N+1.
     */
    suspend fun getFollowedPlayers(userId: String): List<PlayerResponse> =
        dbQuery {
            val playerIds =
                Follows.selectAll()
                    .where {
                        (Follows.userId eq userId) and
                            (Follows.targetType eq FollowTargetType.PLAYER)
                    }
                    .map { it[Follows.targetId] }

            if (playerIds.isEmpty()) return@dbQuery emptyList()

            val basePlayers =
                Players
                    .select(Players.id, Players.fullName, Players.licenceNr)
                    .where { Players.id inList playerIds }
                    .toList()

            val clubByPlayer =
                (PlayerSeasons innerJoin Teams innerJoin Clubs innerJoin Seasons)
                    .select(PlayerSeasons.playerId, Clubs.name)
                    .where { PlayerSeasons.playerId inList playerIds }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .toList()
                    .groupBy { it[PlayerSeasons.playerId] }
                    .mapValues { it.value.first()[Clubs.name] }

            val classByPlayer = ClassificationService.currentClasses(playerIds)

            val eloByPlayer =
                PlayerElos
                    .select(PlayerElos.playerId, PlayerElos.eloValue)
                    .where { (PlayerElos.playerId inList playerIds) and (PlayerElos.isProvisional eq false) }
                    .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
                    .toList()
                    .groupBy { it[PlayerElos.playerId] }
                    .mapValues { it.value.first()[PlayerElos.eloValue] }

            basePlayers.map { row ->
                val id = row[Players.id]
                PlayerResponse(
                    id = id.toString(),
                    fullName = row[Players.fullName],
                    licenceNr = row[Players.licenceNr],
                    currentClubName = clubByPlayer[id],
                    classification = classByPlayer[id],
                    liveClassification = eloByPlayer[id]?.let { ClassificationService.fromElo(it) },
                    currentElo = eloByPlayer[id],
                )
            }
        }

    // ─────────────────────────────────────────────────────────────────────

    internal fun resolveTargetName(
        type: FollowTargetType,
        targetId: UUID,
    ): String? =
        when (type) {
            FollowTargetType.PLAYER ->
                Players.selectAll().where { Players.id eq targetId }.firstOrNull()?.get(Players.fullName)
            FollowTargetType.TEAM ->
                Teams.selectAll().where { Teams.id eq targetId }.firstOrNull()?.get(Teams.name)
            FollowTargetType.DIVISION_GROUP ->
                Groups.selectAll().where { Groups.id eq targetId }.firstOrNull()?.get(Groups.name)
        }
}
