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
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.*

object GameService {
    /**
     * Fills a player's ELO delta on an existing game — league OR tournament/cup — for the given
     * Swiss day. Also fills the competition name, but only for tournament/cup games: league games
     * already have a consistent label from the match scraper, and the Protokoll's own label is a
     * different (if equivalent) abbreviation of the same competition.
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
     * Returns the number of rows updated.
     *
     * The whole Elo-Protokoll is applied in a single transaction rather than one per entry: entries
     * are processed in order and each re-reads the day's still-unrated candidates, so an earlier
     * assignment removes that game from later ones (a transaction reads its own writes) — identical
     * semantics to per-entry calls, without paying a connection round-trip per game.
     */
    suspend fun applyEloDeltas(
        playerId: UUID,
        updates: List<EloDeltaUpdate>,
    ): Int =
        dbQuery {
            updates.count { applyEloDeltaInTx(playerId, it) }
        }

    /** One Elo-Protokoll line: the delta (and metadata) to stamp onto a matching game for [dayStart]. */
    data class EloDeltaUpdate(
        val opponentName: String,
        val dayStart: OffsetDateTime,
        val eloDelta: Double,
        /** Position in the player's Elo-Protokoll — stored as a stable same-day ordering key. */
        val eloOrder: Int,
        val competition: String?,
    )

    private fun Transaction.applyEloDeltaInTx(
        playerId: UUID,
        u: EloDeltaUpdate,
    ): Boolean {
        val dayStart = u.dayStart
        val dayEnd = dayStart.plusDays(1)
        // The delta's sign is authoritative for win/loss (no draws in TT singles); the Protokoll
        // win-icon is unreliable. Used only to break ties between same-named same-day opponents.
        val won = u.eloDelta > 0

        val candidates =
            Games
                .select(Games.id, Games.homePlayer1Id, Games.awayPlayer1Id, Games.result, Games.matchId)
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
                        isLeagueGame = row[Games.matchId] != null,
                    )
                }
        if (candidates.isEmpty()) return false

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
        val target = accentFold(clickTtNameToDb(u.opponentName.substringBefore("(").trim()))
        val byName = candidates.filter { it.opponentId?.let { id -> nameById[id] } == target }

        val chosen =
            when {
                byName.size == 1 -> byName.single()
                // Two identically-named same-day opponents (vanishingly rare): break the tie on result.
                byName.size > 1 -> byName.singleOrNull { it.won == won }
                // No name match (opponent unresolved on the row, or stored under a name variant):
                // fall back only when there is a single unrated game that day — never guess between many.
                else -> candidates.singleOrNull()
            } ?: return false

        val updated =
            Games.update({ Games.id eq chosen.gameId }) {
                if (chosen.playerIsHome) {
                    it[Games.homePlayer1EloDelta] = u.eloDelta
                    it[Games.homePlayer1EloOrder] = u.eloOrder
                } else {
                    it[Games.awayPlayer1EloDelta] = u.eloDelta
                    it[Games.awayPlayer1EloOrder] = u.eloOrder
                }
                // League games already carry a consistent "$groupName | $home : $away" label from the
                // match scraper (same for both singles and doubles rows of the same match). The
                // Elo-Protokoll's own free-text label is a *different* abbreviation of the same
                // competition (e.g. "1. L-Herren" vs "HE 1. Liga") and only ever covers singles, so
                // applying it here used to leave a league match's doubles row (never touched by this
                // path) permanently disagreeing with its own singles rows. Only tournament/cup games —
                // which have no better source for this label — take it from the Protokoll.
                if (u.competition != null && !chosen.isLeagueGame) it[Games.competitionName] = u.competition
            }
        return updated > 0
    }

    private data class CandidateGame(
        val gameId: UUID,
        val playerIsHome: Boolean,
        val opponentId: UUID?,
        val won: Boolean,
        val isLeagueGame: Boolean,
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
     * Elo-Protokoll via [applyEloDeltas], the only page that has them.
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
            insertTournamentGameIfAbsentInTx(
                playerId,
                opponentId,
                playedAt,
                competition,
                result,
                homeSets,
                awaySets,
                sets,
            )
        }

    /** One tournament/cup game parsed off a player's page, ready to insert. */
    data class TournamentGameInsert(
        val playerId: UUID,
        val opponentId: UUID?,
        val playedAt: OffsetDateTime,
        val competition: String?,
        val result: GameResult,
        val homeSets: Int,
        val awaySets: Int,
        val sets: List<ParsedClickTTSet>,
    )

    /**
     * Inserts a whole page's worth of tournament/cup games in a single transaction rather than one
     * per game: a portrait backfill can touch dozens of tournament games per player, and paying a
     * separate connection round-trip (duplicate-check select + insert) for each one dominated sync
     * time. Semantics are unchanged — each game's duplicate check still runs before its own insert.
     *
     * Returns the number of newly inserted games.
     */
    suspend fun insertTournamentGamesIfAbsent(games: List<TournamentGameInsert>): Int =
        dbQuery {
            games.count {
                insertTournamentGameIfAbsentInTx(
                    it.playerId,
                    it.opponentId,
                    it.playedAt,
                    it.competition,
                    it.result,
                    it.homeSets,
                    it.awaySets,
                    it.sets,
                )
            }
        }

    private fun Transaction.insertTournamentGameIfAbsentInTx(
        playerId: UUID,
        opponentId: UUID?,
        playedAt: OffsetDateTime,
        competition: String?,
        result: GameResult,
        homeSets: Int,
        awaySets: Int,
        sets: List<ParsedClickTTSet>,
    ): Boolean {
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

        if (Games.select(Games.id).where(duplicateCondition).count() > 0) return false

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
        return true
    }
}
