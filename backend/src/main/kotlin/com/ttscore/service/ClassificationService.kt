package com.ttscore.service

import com.ttscore.database.PlayerClassifications
import com.ttscore.database.Seasons
import com.ttscore.database.dbQuery
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Official classification handling. A Swiss season runs Jul 1 → Jun 30 and the reclassification
 * happens on Jan 1, in the middle of the season — so every season carries two class values:
 *   - first half  = Jul–Dec (the class set at the previous Jan 1)
 *   - second half = Jan–Jun (the class set at this Jan 1)
 *
 * Match detail pages print the class in effect at match time, so a match observation already belongs
 * to a specific (season, half) by its date. The portrait page's "Klassierung" gives only the current
 * class and is used as a fill-if-null fallback for the current half.
 */
object ClassificationService {
    private val swissZone = ZoneId.of("Europe/Zurich")

    enum class Half { FIRST, SECOND }

    fun halfOf(date: LocalDate): Half = if (date.monthValue >= 7) Half.FIRST else Half.SECOND

    /** Canonical "YYYY/YYYY+1" name of the season that contains [date]. */
    fun seasonNameOf(date: LocalDate): String {
        val y = date.year
        return if (date.monthValue >= 7) "$y/${y + 1}" else "${y - 1}/$y"
    }

    fun localDateOf(at: OffsetDateTime): LocalDate = at.atZoneSameInstant(swissZone).toLocalDate()

    private fun column(half: Half): Column<String?> =
        if (half == Half.FIRST) PlayerClassifications.firstHalfClass else PlayerClassifications.secondHalfClass

    // -------------------------------------------------------------------------
    // ELO → classification mapping (Swiss TT Klassierungstabelle, men's table)
    // -------------------------------------------------------------------------

    // Each class covers [minElo, next class's minElo). Women are intentionally shown their
    // men's-scale class, so this single table is applied to every player. Descending by minElo.
    private val ELO_THRESHOLDS: List<Pair<Int, String>> =
        listOf(
            1990 to "A22",
            1890 to "A21",
            1790 to "A20",
            1680 to "A19",
            1565 to "A18",
            1490 to "A17",
            1435 to "A16",
            1360 to "B15",
            1320 to "B14",
            1280 to "B13",
            1240 to "B12",
            1200 to "B11",
            1150 to "C10",
            1100 to "C9",
            1050 to "C8",
            990 to "C7",
            930 to "C6",
            860 to "D5",
            780 to "D4",
            700 to "D3",
            630 to "D2",
            Int.MIN_VALUE to "D1",
        )

    /** The classification a given ELO corresponds to right now (the "live" class). */
    fun fromElo(elo: Int): String = ELO_THRESHOLDS.first { elo >= it.first }.second

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    /**
     * Records the class printed on a match into the half its date falls in (last write wins).
     * Must be called inside an existing transaction (the match scrapers wrap their work in one).
     */
    fun recordMatchClass(
        playerId: UUID,
        seasonId: UUID,
        playedAt: OffsetDateTime?,
        className: String?,
    ) {
        if (className.isNullOrBlank() || playedAt == null) return
        val half = halfOf(localDateOf(playedAt))
        // Setting only the one half column means ON CONFLICT updates only that column,
        // leaving the other half untouched.
        PlayerClassifications.upsert(PlayerClassifications.playerId, PlayerClassifications.seasonId) {
            it[PlayerClassifications.playerId] = playerId
            it[PlayerClassifications.seasonId] = seasonId
            it[column(half)] = className
        }
    }

    /**
     * Fallback for players without a match observation in the current half (e.g. tournament-only
     * players): fills the current half of [seasonId] from the portrait's current class, but never
     * overwrites a class already derived from a match.
     */
    suspend fun fillCurrentClassIfAbsent(
        playerId: UUID,
        seasonId: UUID,
        className: String?,
    ) {
        if (className.isNullOrBlank()) return
        val half = halfOf(LocalDate.now(swissZone))
        val col = column(half)
        dbQuery {
            val existing =
                PlayerClassifications
                    .selectAll()
                    .where {
                        (PlayerClassifications.playerId eq playerId) and
                            (PlayerClassifications.seasonId eq seasonId)
                    }
                    .firstOrNull()

            if (existing == null) {
                PlayerClassifications.insert {
                    it[PlayerClassifications.playerId] = playerId
                    it[PlayerClassifications.seasonId] = seasonId
                    it[col] = className
                }
            } else if (existing[col] == null) {
                PlayerClassifications.update({
                    (PlayerClassifications.playerId eq playerId) and (PlayerClassifications.seasonId eq seasonId)
                }) {
                    it[col] = className
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reads (call inside an existing transaction)
    // -------------------------------------------------------------------------

    /** The latest known class per player — most recent season, preferring the second (later) half. */
    fun currentClasses(playerIds: Collection<UUID>): Map<UUID, String?> {
        if (playerIds.isEmpty()) return emptyMap()
        return (PlayerClassifications innerJoin Seasons)
            .select(
                PlayerClassifications.playerId,
                Seasons.name,
                PlayerClassifications.firstHalfClass,
                PlayerClassifications.secondHalfClass,
            )
            .where { PlayerClassifications.playerId inList playerIds }
            .orderBy(Seasons.name to SortOrder.DESC)
            .toList()
            .groupBy { it[PlayerClassifications.playerId] }
            .mapValues { (_, rows) ->
                val latest = rows.first()
                latest[PlayerClassifications.secondHalfClass] ?: latest[PlayerClassifications.firstHalfClass]
            }
    }

    /** Class for a specific season + half, per player. */
    fun classesForSeasonHalf(
        playerIds: Collection<UUID>,
        seasonId: UUID,
        half: Half,
    ): Map<UUID, String?> {
        if (playerIds.isEmpty()) return emptyMap()
        val col = column(half)
        return PlayerClassifications
            .select(PlayerClassifications.playerId, col)
            .where {
                (PlayerClassifications.playerId inList playerIds) and
                    (PlayerClassifications.seasonId eq seasonId)
            }
            .associate { it[PlayerClassifications.playerId] to it[col] }
    }

    /**
     * Both half-classes per (player, season name) — for historical per-game lookups that span
     * multiple seasons. Use [classOf] to pick the right half for a given date.
     */
    fun classBadges(
        playerIds: Collection<UUID>,
        seasonNames: Collection<String>,
    ): Map<Pair<UUID, String>, Pair<String?, String?>> {
        if (playerIds.isEmpty() || seasonNames.isEmpty()) return emptyMap()
        return (PlayerClassifications innerJoin Seasons)
            .select(
                PlayerClassifications.playerId,
                Seasons.name,
                PlayerClassifications.firstHalfClass,
                PlayerClassifications.secondHalfClass,
            )
            .where { (PlayerClassifications.playerId inList playerIds) and (Seasons.name inList seasonNames) }
            .associate {
                (it[PlayerClassifications.playerId] to it[Seasons.name]) to
                    (it[PlayerClassifications.firstHalfClass] to it[PlayerClassifications.secondHalfClass])
            }
    }

    /** Picks the half-appropriate class out of a [classBadges] entry for the given date. */
    fun classOf(
        badges: Map<Pair<UUID, String>, Pair<String?, String?>>,
        playerId: UUID,
        date: LocalDate,
    ): String? {
        val pair = badges[playerId to seasonNameOf(date)] ?: return null
        return if (halfOf(date) == Half.FIRST) pair.first else pair.second
    }
}
