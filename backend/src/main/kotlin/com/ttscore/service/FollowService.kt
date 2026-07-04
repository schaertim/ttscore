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

/** Outcome of a [FollowService.follow] attempt. */
sealed interface FollowResult {
    data class Ok(val follow: FollowResponse) : FollowResult

    data object TargetNotFound : FollowResult

    data object LimitReached : FollowResult
}

object FollowService {
    /** Free accounts may follow this many entities beyond their home player. */
    private const val FREE_FOLLOW_LIMIT = 3

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
     * Follows the target (notify defaults off). Idempotent — an existing follow is
     * returned as [FollowResult.Ok] and never counts against the cap. For non-Pro
     * users a *new* follow that is not the home player is rejected with
     * [FollowResult.LimitReached] once [FREE_FOLLOW_LIMIT] such follows exist.
     * [FollowResult.TargetNotFound] if the entity does not exist.
     */
    suspend fun follow(
        userId: String,
        targetType: FollowTargetType,
        targetId: UUID,
        isPro: Boolean,
        homePlayerId: UUID?,
    ): FollowResult =
        dbQuery {
            val existing =
                Follows.selectAll()
                    .where {
                        (Follows.userId eq userId) and
                            (Follows.targetType eq targetType) and
                            (Follows.targetId eq targetId)
                    }
                    .firstOrNull()

            val name =
                resolveTargetName(targetType, targetId) ?: return@dbQuery FollowResult.TargetNotFound

            if (existing != null) {
                return@dbQuery FollowResult.Ok(
                    FollowResponse(
                        id = existing[Follows.id].toString(),
                        targetType = targetType.name.lowercase(),
                        targetId = targetId.toString(),
                        targetName = name,
                        notify = existing[Follows.notify],
                    ),
                )
            }

            val isHomePlayer =
                targetType == FollowTargetType.PLAYER && homePlayerId != null && targetId == homePlayerId
            if (!isPro && !isHomePlayer && nonHomeFollowCount(userId, homePlayerId) >= FREE_FOLLOW_LIMIT) {
                return@dbQuery FollowResult.LimitReached
            }

            val id =
                Follows.insert {
                    it[Follows.userId] = userId
                    it[Follows.targetType] = targetType
                    it[Follows.targetId] = targetId
                    it[Follows.notify] = false
                    it[createdAt] = OffsetDateTime.now()
                }[Follows.id]

            FollowResult.Ok(
                FollowResponse(
                    id = id.toString(),
                    targetType = targetType.name.lowercase(),
                    targetId = targetId.toString(),
                    targetName = name,
                    notify = false,
                ),
            )
        }

    /**
     * Counts a user's follows that do *not* point at their home player — the set the
     * free-tier cap applies to. Must run inside an existing transaction.
     */
    private fun nonHomeFollowCount(
        userId: String,
        homePlayerId: UUID?,
    ): Long {
        val total = Follows.selectAll().where { Follows.userId eq userId }.count()
        if (homePlayerId == null) return total
        val followsHome =
            Follows.selectAll()
                .where {
                    (Follows.userId eq userId) and
                        (Follows.targetType eq FollowTargetType.PLAYER) and
                        (Follows.targetId eq homePlayerId)
                }
                .any()
        return if (followsHome) total - 1 else total
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

    enum class SetNotifyResult { OK, NOT_FOUND, PRO_REQUIRED }

    /**
     * Toggles the notify (bell) flag on an existing follow. Non-Pro users may only
     * enable notifications for their own home player; any other target returns
     * [SetNotifyResult.PRO_REQUIRED]. Turning notifications *off* is always allowed.
     */
    suspend fun setNotify(
        userId: String,
        followId: UUID,
        notify: Boolean,
        isPro: Boolean,
        homePlayerId: UUID?,
    ): SetNotifyResult =
        dbQuery {
            val row =
                Follows.selectAll()
                    .where { (Follows.id eq followId) and (Follows.userId eq userId) }
                    .firstOrNull()
                    ?: return@dbQuery SetNotifyResult.NOT_FOUND

            if (notify && !isPro) {
                val isHomePlayer =
                    row[Follows.targetType] == FollowTargetType.PLAYER &&
                        homePlayerId != null &&
                        row[Follows.targetId] == homePlayerId
                if (!isHomePlayer) return@dbQuery SetNotifyResult.PRO_REQUIRED
            }

            Follows.update({ (Follows.id eq followId) and (Follows.userId eq userId) }) {
                it[Follows.notify] = notify
            }
            SetNotifyResult.OK
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
