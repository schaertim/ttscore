package com.ttscore.service

import com.ttscore.database.GameSets
import com.ttscore.database.Games
import com.ttscore.database.dbQuery
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.scraper.clicktt.model.ParsedClickTTSet
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.*

object GameService {
    /**
     * Fills a player's ELO delta (and refreshes the competition name) on an existing game — league
     * OR tournament/cup — for the given Swiss day. Tries the player as home first, then as away,
     * matching only rows whose delta is still null so that multiple games against the same opponent
     * on the same day each get rated once.
     *
     * The Elo-Protokoll is the only page that carries deltas, so this is the sole writer of them and
     * it never inserts: if no row matches (game unrated, or opponent unresolved) it simply does
     * nothing. That is what keeps the sync from ever producing duplicate game rows.
     *
     * Returns true if a row was updated.
     */
    suspend fun updateGameEloDelta(
        playerId: UUID,
        opponentId: UUID?,
        dayStart: OffsetDateTime,
        eloDelta: Double,
        competition: String? = null,
    ): Boolean =
        dbQuery {
            val dayEnd = dayStart.plusDays(1)

            val homeBase =
                (Games.homePlayer1Id eq playerId) and
                    (Games.playedAt greaterEq dayStart) and
                    (Games.playedAt less dayEnd) and
                    (Games.homePlayer1EloDelta.isNull())
            val homeCondition =
                if (opponentId != null) {
                    homeBase and (Games.awayPlayer1Id eq opponentId)
                } else {
                    homeBase
                }

            val homeUpdate =
                Games.update({ homeCondition }) {
                    it[Games.homePlayer1EloDelta] = eloDelta
                    if (competition != null) it[Games.competitionName] = competition
                }
            if (homeUpdate > 0) return@dbQuery true

            val awayBase =
                (Games.awayPlayer1Id eq playerId) and
                    (Games.playedAt greaterEq dayStart) and
                    (Games.playedAt less dayEnd) and
                    (Games.awayPlayer1EloDelta.isNull())
            val awayCondition =
                if (opponentId != null) {
                    awayBase and (Games.homePlayer1Id eq opponentId)
                } else {
                    awayBase
                }

            val awayUpdate =
                Games.update({ awayCondition }) {
                    it[Games.awayPlayer1EloDelta] = eloDelta
                    if (competition != null) it[Games.competitionName] = competition
                }
            awayUpdate > 0
        }

    suspend fun getPlayerIdsFromMatches(matchIds: Set<UUID>): Set<UUID> =
        dbQuery {
            if (matchIds.isEmpty()) return@dbQuery emptySet()
            Games.select(Games.homePlayer1Id, Games.awayPlayer1Id)
                .where { Games.matchId inList matchIds }
                .flatMap { listOfNotNull(it[Games.homePlayer1Id], it[Games.awayPlayer1Id]) }
                .toSet()
        }

    /**
     * Inserts a tournament/cup game (matchId = null) with its set scores, unless an equivalent row
     * already exists. The dedicated TOURNAMENT/Cup season pages are the source of truth for these
     * games (they carry result + sets + opponent person-id); the synced player is always stored as
     * the home side. ELO deltas are intentionally left null — they are filled afterwards from the
     * Elo-Protokoll via [updateGameEloDelta], the only page that has them.
     *
     * Equivalence is checked against existing tournament rows (matchId IS NULL) on the same Swiss
     * day. When the opponent is known we match the player pair in either orientation, which
     * deduplicates both a re-sync of the same player and the mirror row produced when the opponent's
     * own portrait is synced. When the opponent is not in our DB the row can only originate from this
     * player's page, so we match on the player side plus the set score.
     *
     * Returns true if a new game was inserted.
     */
    suspend fun insertTournamentGameIfAbsent(
        playerId: UUID,
        opponentId: UUID?,
        playedAt: OffsetDateTime,
        competition: String?,
        result: GameResult,
        homeSets: Int,
        awaySets: Int,
        sets: List<ParsedClickTTSet>,
    ): Boolean =
        dbQuery {
            val dayStart = playedAt
            val dayEnd = playedAt.plusDays(1)
            val onDay =
                (Games.matchId.isNull()) and
                    (Games.playedAt greaterEq dayStart) and
                    (Games.playedAt less dayEnd)
            val duplicateCondition =
                if (opponentId != null) {
                    onDay and
                        (
                            ((Games.homePlayer1Id eq playerId) and (Games.awayPlayer1Id eq opponentId)) or
                                ((Games.homePlayer1Id eq opponentId) and (Games.awayPlayer1Id eq playerId))
                        )
                } else {
                    onDay and
                        (Games.homePlayer1Id eq playerId) and
                        (Games.awayPlayer1Id.isNull()) and
                        (Games.homeSets eq homeSets.toShort()) and
                        (Games.awaySets eq awaySets.toShort())
                }

            if (Games.select(Games.id).where(duplicateCondition).count() > 0) return@dbQuery false

            val gameId =
                Games.insert {
                    it[Games.matchId] = null
                    it[Games.gameType] = GameType.SINGLES
                    it[Games.homePlayer1Id] = playerId
                    it[Games.awayPlayer1Id] = opponentId
                    it[Games.playedAt] = playedAt
                    it[Games.competitionName] = competition
                    it[Games.homeSets] = homeSets.toShort()
                    it[Games.awaySets] = awaySets.toShort()
                    it[Games.result] = result
                }[Games.id]

            for (set in sets) {
                GameSets.insertIgnore {
                    it[GameSets.gameId] = gameId
                    it[GameSets.setNumber] = set.setNumber.toShort()
                    it[GameSets.homePoints] = set.homePoints.toShort()
                    it[GameSets.awayPoints] = set.awayPoints.toShort()
                }
            }
            true
        }
}
