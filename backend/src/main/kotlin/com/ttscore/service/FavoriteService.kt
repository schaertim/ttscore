package com.ttscore.service

import com.ttscore.database.Clubs
import com.ttscore.database.Favorites
import com.ttscore.database.PlayerElos
import com.ttscore.database.PlayerSeasons
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.Teams
import com.ttscore.database.dbQuery
import com.ttscore.model.FavoriteCheckResponse
import com.ttscore.model.FavoriteResponse
import com.ttscore.model.FollowTargetType
import com.ttscore.model.PlayerResponse
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.OffsetDateTime
import java.util.UUID

object FavoriteService {
    /** Returns all starred bookmarks for a user. */
    suspend fun getFavorites(userId: String): List<FavoriteResponse> =
        dbQuery {
            Favorites.selectAll()
                .where { Favorites.userId eq userId }
                .orderBy(Favorites.createdAt)
                .mapNotNull { row ->
                    val type = row[Favorites.targetType]
                    val targetId = row[Favorites.targetId]
                    val name = FollowService.resolveTargetName(type, targetId) ?: return@mapNotNull null
                    FavoriteResponse(
                        id = row[Favorites.id].toString(),
                        targetType = type.name.lowercase(),
                        targetId = targetId.toString(),
                        targetName = name,
                    )
                }
        }

    /** Returns whether the user has starred the given target. */
    suspend fun check(
        userId: String,
        targetType: FollowTargetType,
        targetId: UUID,
    ): FavoriteCheckResponse =
        dbQuery {
            val row =
                Favorites.selectAll()
                    .where {
                        (Favorites.userId eq userId) and
                            (Favorites.targetType eq targetType) and
                            (Favorites.targetId eq targetId)
                    }
                    .firstOrNull()
            FavoriteCheckResponse(
                favorited = row != null,
                favoriteId = row?.get(Favorites.id)?.toString(),
            )
        }

    /**
     * Stars a target. Idempotent.
     * Returns null if the target entity does not exist.
     */
    suspend fun favorite(
        userId: String,
        targetType: FollowTargetType,
        targetId: UUID,
    ): FavoriteResponse? =
        dbQuery {
            val existing =
                Favorites.selectAll()
                    .where {
                        (Favorites.userId eq userId) and
                            (Favorites.targetType eq targetType) and
                            (Favorites.targetId eq targetId)
                    }
                    .firstOrNull()

            val name = FollowService.resolveTargetName(targetType, targetId) ?: return@dbQuery null

            if (existing != null) {
                return@dbQuery FavoriteResponse(
                    id = existing[Favorites.id].toString(),
                    targetType = targetType.name.lowercase(),
                    targetId = targetId.toString(),
                    targetName = name,
                )
            }

            val id =
                Favorites.insert {
                    it[Favorites.userId] = userId
                    it[Favorites.targetType] = targetType
                    it[Favorites.targetId] = targetId
                    it[createdAt] = OffsetDateTime.now()
                }[Favorites.id]

            FavoriteResponse(
                id = id.toString(),
                targetType = targetType.name.lowercase(),
                targetId = targetId.toString(),
                targetName = name,
            )
        }

    /**
     * Returns full [PlayerResponse] objects for all players the user has starred.
     * Uses the same batch-join pattern as [PlayerService.search] â€” two queries, no N+1.
     */
    suspend fun getFavoritePlayers(userId: String): List<PlayerResponse> =
        dbQuery {
            val playerIds =
                Favorites.selectAll()
                    .where {
                        (Favorites.userId eq userId) and
                            (Favorites.targetType eq FollowTargetType.PLAYER)
                    }
                    .map { it[Favorites.targetId] }

            if (playerIds.isEmpty()) return@dbQuery emptyList()

            val basePlayers =
                Players
                    .select(Players.id, Players.fullName, Players.licenceNr)
                    .where { Players.id inList playerIds }
                    .toList()

            val playerStats =
                (PlayerSeasons innerJoin Teams innerJoin Clubs innerJoin Seasons)
                    .select(PlayerSeasons.playerId, PlayerSeasons.klass, Clubs.name)
                    .where { PlayerSeasons.playerId inList playerIds }
                    .orderBy(Seasons.name to SortOrder.DESC)
                    .toList()
                    .groupBy { it[PlayerSeasons.playerId] }
                    .mapValues { it.value.first() }

            val eloByPlayer =
                PlayerElos
                    .select(PlayerElos.playerId, PlayerElos.eloValue)
                    .where { PlayerElos.playerId inList playerIds }
                    .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
                    .toList()
                    .groupBy { it[PlayerElos.playerId] }
                    .mapValues { it.value.first()[PlayerElos.eloValue] }

            basePlayers.map { row ->
                val id = row[Players.id]
                val stats = playerStats[id]
                PlayerResponse(
                    id = id.toString(),
                    fullName = row[Players.fullName],
                    licenceNr = row[Players.licenceNr],
                    currentClubName = stats?.get(Clubs.name),
                    klass = stats?.get(PlayerSeasons.klass),
                    currentElo = eloByPlayer[id],
                )
            }
        }

    /** Removes a star. Returns false if not found or not owned by the user. */
    suspend fun unfavorite(
        userId: String,
        favoriteId: UUID,
    ): Boolean =
        dbQuery {
            Favorites.deleteWhere {
                (Favorites.id eq favoriteId) and (Favorites.userId eq userId)
            } > 0
        }
}
