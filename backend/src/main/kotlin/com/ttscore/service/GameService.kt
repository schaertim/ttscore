package com.ttscore.service

import com.ttscore.database.GameSets
import com.ttscore.database.Games
import com.ttscore.database.Players
import com.ttscore.database.dbQuery
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.scraper.clicktt.model.ParsedClickTTSet
import com.ttscore.util.accentFold
import com.ttscore.util.clickTtNameToDb
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
     * OR tournament/cup — for the given Swiss day.
     *
     * Matching is done *locally*: we load the player's not-yet-rated singles games on the day and map
     * this Elo-Protokoll line to one of them. The opponent on each row was linked by click-tt
     * person-id at insert time, so it is already the correct person; the Protokoll only gives the
     * opponent's *name*, so we identify the right row by comparing that name against the rows'
     * stored opponents. This sidesteps the duplicate-name problem (e.g. two players both named
     * "Hess Matthias") that a global name→id lookup hits — a player faces only one same-named
     * opponent on a given day. Win/loss disambiguates the rare case of two same-named opponents.
     *
     * The Elo-Protokoll is the only page that carries deltas, so this is the sole writer of them and
     * it never inserts: if no row can be matched it does nothing rather than guess. That is what
     * keeps the sync from ever producing duplicate game rows or mis-assigning a delta.
     *
     * Returns true if a row was updated.
     */
    suspend fun updateGameEloDelta(
        playerId: UUID,
        opponentName: String,
        dayStart: OffsetDateTime,
        eloDelta: Double,
        competition: String? = null,
    ): Boolean =
        dbQuery {
            val dayEnd = dayStart.plusDays(1)
            // The delta's sign is authoritative for win/loss (no draws in TT singles); the Protokoll
            // win-icon is unreliable. Used only to break ties between same-named same-day opponents.
            val won = eloDelta > 0

            val candidates =
                Games
                    .select(Games.id, Games.homePlayer1Id, Games.awayPlayer1Id, Games.result)
                    .where {
                        (Games.gameType eq GameType.SINGLES) and
                            (Games.playedAt greaterEq dayStart) and
                            (Games.playedAt less dayEnd) and
                            (
                                ((Games.homePlayer1Id eq playerId) and Games.homePlayer1EloDelta.isNull()) or
                                    ((Games.awayPlayer1Id eq playerId) and Games.awayPlayer1EloDelta.isNull())
                            )
                    }
                    .map { row ->
                        val isHome = row[Games.homePlayer1Id] == playerId
                        CandidateGame(
                            gameId = row[Games.id],
                            playerIsHome = isHome,
                            opponentId = if (isHome) row[Games.awayPlayer1Id] else row[Games.homePlayer1Id],
                            won = row[Games.result] == (if (isHome) GameResult.HOME else GameResult.AWAY),
                        )
                    }
            if (candidates.isEmpty()) return@dbQuery false

            // Stored names of the candidates' (already correctly linked) opponents, for local matching.
            val opponentIds = candidates.mapNotNull { it.opponentId }.toSet()
            val nameById =
                if (opponentIds.isEmpty()) {
                    emptyMap()
                } else {
                    Players
                        .select(Players.id, Players.fullName)
                        .where { Players.id inList opponentIds }
                        .associate { it[Players.id] to accentFold(it[Players.fullName]) }
                }

            // Protokoll opponent looks like "Hess, Matthias (C10)" — drop the class, normalise to the
            // DB's "Lastname Firstname" form, then accent-fold for a robust comparison.
            val target = accentFold(clickTtNameToDb(opponentName.substringBefore("(").trim()))
            val byName = candidates.filter { it.opponentId?.let { id -> nameById[id] } == target }

            val chosen =
                when {
                    byName.size == 1 -> byName.single()
                    // Two identically-named same-day opponents (vanishingly rare): break the tie on result.
                    byName.size > 1 -> byName.singleOrNull { it.won == won }
                    // No name match (opponent unresolved on the row, or stored under a name variant):
                    // fall back only when there is a single unrated game that day — never guess between many.
                    else -> candidates.singleOrNull()
                } ?: return@dbQuery false

            val updated =
                Games.update({ Games.id eq chosen.gameId }) {
                    if (chosen.playerIsHome) {
                        it[Games.homePlayer1EloDelta] = eloDelta
                    } else {
                        it[Games.awayPlayer1EloDelta] = eloDelta
                    }
                    if (competition != null) it[Games.competitionName] = competition
                }
            updated > 0
        }

    private data class CandidateGame(
        val gameId: UUID,
        val playerIsHome: Boolean,
        val opponentId: UUID?,
        val won: Boolean,
    )

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
