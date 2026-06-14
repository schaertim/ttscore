package com.ttscore.service

import com.ttscore.database.Games
import com.ttscore.database.PlayerElos
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Provisional ("live") ELO. The federation rates matches monthly, so matches played since the last
 * rating run carry no official delta yet. We recompute those deltas ourselves — batch-scored: every
 * pending delta uses each player's last official rating (never updated mid-period) — and roll them
 * into an up-to-date ELO that also feeds the live classification.
 *
 * Pending = a singles game with a decisive result whose official delta for the player is still null.
 * A recency window guards against ancient null-delta rows (delta-fill gaps) skewing the live ELO; it
 * is a deliberately generous superset of the ~1-month rating period, not a precise period boundary.
 */
object LiveEloService {
    private val swissZone = ZoneId.of("Europe/Zurich")

    private fun pendingCutoff(): OffsetDateTime = OffsetDateTime.now(swissZone).minusMonths(3)

    fun isWithinPendingWindow(playedAt: OffsetDateTime?): Boolean =
        playedAt != null && !playedAt.isBefore(pendingCutoff())

    data class PendingGame(
        val opponentId: UUID?,
        val playerIsHome: Boolean,
        val result: GameResult,
        val playedAt: OffsetDateTime?,
    )

    /** Latest official ELO snapshot per player. Call inside a transaction. */
    fun baseElos(playerIds: Collection<UUID>): Map<UUID, Int> {
        if (playerIds.isEmpty()) return emptyMap()
        return PlayerElos
            .select(PlayerElos.playerId, PlayerElos.eloValue)
            .where { (PlayerElos.playerId inList playerIds) and (PlayerElos.isProvisional eq false) }
            .orderBy(PlayerElos.recordedAt to SortOrder.DESC)
            .toList()
            .groupBy { it[PlayerElos.playerId] }
            .mapValues { it.value.first()[PlayerElos.eloValue] }
    }

    /** Decisive, recent singles games for the player that are not yet officially rated. */
    fun pendingGamesFor(playerId: UUID): List<PendingGame> =
        Games
            .select(
                Games.homePlayer1Id,
                Games.awayPlayer1Id,
                Games.result,
                Games.playedAt,
            )
            .where {
                (Games.gameType eq GameType.SINGLES) and
                    (Games.playedAt greaterEq pendingCutoff()) and
                    (Games.result neq GameResult.NOT_PLAYED) and
                    (
                        ((Games.homePlayer1Id eq playerId) and Games.homePlayer1EloDelta.isNull()) or
                            ((Games.awayPlayer1Id eq playerId) and Games.awayPlayer1EloDelta.isNull())
                    )
            }
            .orderBy(Games.playedAt to SortOrder.ASC)
            .map { row ->
                val isHome = row[Games.homePlayer1Id] == playerId
                PendingGame(
                    opponentId = if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id],
                    playerIsHome = isHome,
                    result = row[Games.result],
                    playedAt = row[Games.playedAt],
                )
            }

    /** Provisional delta for the player in one game, given both players' official base ELOs. */
    fun provisionalDelta(
        playerBase: Int,
        opponentBase: Int,
        playerIsHome: Boolean,
        result: GameResult,
    ): Double {
        val (homeDelta, awayDelta) =
            EloCalculationService.calculateGameDeltas(
                if (playerIsHome) playerBase else opponentBase,
                if (playerIsHome) opponentBase else playerBase,
                result,
            )
        return if (playerIsHome) homeDelta else awayDelta
    }

    /**
     * Up-to-date ELO = official ELO + provisional deltas of pending games. Pending games whose
     * opponent has no rating are skipped. Returns null when the player has no official ELO.
     * Call inside a transaction.
     */
    fun liveEloFor(
        playerId: UUID,
        officialElo: Int?,
    ): Int? {
        officialElo ?: return null
        val pending = pendingGamesFor(playerId)
        if (pending.isEmpty()) return officialElo
        val opponentBases = baseElos(pending.mapNotNull { it.opponentId }.toSet())
        var sum = 0.0
        for (g in pending) {
            val opponentBase = g.opponentId?.let { opponentBases[it] } ?: continue
            sum += provisionalDelta(officialElo, opponentBase, g.playerIsHome, g.result)
        }
        return officialElo + Math.round(sum).toInt()
    }
}
