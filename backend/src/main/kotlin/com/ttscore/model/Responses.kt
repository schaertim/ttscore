package com.ttscore.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val homePlayerId: String?,
    val homePlayerName: String?,
    /** True when [proUntil] is in the future — the frontend renders gates off this. */
    val isPro: Boolean = false,
    /** ISO-8601 expiry of the current Pro entitlement, or null if never Pro. */
    val proUntil: String? = null,
)

/** Machine-readable reason for a 403, so the frontend can show the right upsell. */
@Serializable
data class ReasonResponse(
    val reason: String,
)

@Serializable
data class SetHomePlayerRequest(
    val playerId: String,
)

/** Body for `POST /billing/checkout`. `plan` is "monthly" or "yearly". */
@Serializable
data class CheckoutRequest(
    val plan: String,
)

/** A Stripe-hosted URL (Checkout Session or billing portal) for the frontend to redirect to. */
@Serializable
data class BillingUrlResponse(
    val url: String,
)

// ── Career tab (Pro) ── all-time, league singles, classification-based (no historical ELO) ──

/** One classification observation: a (season, half) and the class in effect then. */
@Serializable
data class CareerClassPoint(
    val seasonName: String,
    val half: String, // "first" | "second"
    val classification: String,
)

/** Club + league the player was registered in for a given season. */
@Serializable
data class CareerSeasonEntry(
    val seasonName: String,
    val clubName: String?,
    val leagueName: String?,
)

@Serializable
data class CareerTotals(
    val matches: Int,
    val wins: Int,
    val losses: Int,
    val seasonsPlayed: Int,
    val firstYear: Int?,
    val lastYear: Int?,
    val opponentsFaced: Int,
    val clubsCount: Int,
)

@Serializable
data class CareerMilestones(
    val debutSeason: String?,
    val debutOpponentName: String?,
    val peakClass: String?,
    val peakClassSeason: String?,
    val longestWinStreak: Int,
    val bestWinOpponentName: String?,
    val bestWinOpponentClass: String?,
    val bestSeasonName: String?,
    val bestSeasonWins: Int,
    val bestSeasonGames: Int,
)

/** A frequent opponent across the whole career (drives the rivalries list). */
@Serializable
data class CareerRival(
    val opponentId: String,
    val opponentName: String,
    val opponentClass: String?,
    val meetings: Int,
    val wins: Int,
    val losses: Int,
)

@Serializable
data class CareerResponse(
    val classProgression: List<CareerClassPoint>,
    val seasons: List<CareerSeasonEntry>,
    val totals: CareerTotals,
    val milestones: CareerMilestones,
    val rivalries: List<CareerRival>,
)

@Serializable
data class TeamSummaryResponse(
    val id: String,
    val name: String,
    val groupName: String,
    val position: Int,
    val record: String,
    val points: Int,
    val lastResults: List<String>,
)

@Serializable
data class TeamPlayerResponse(
    val id: String,
    val fullName: String,
    val licenceNr: String?,
    val classification: String?,
    val wins: Int,
    val losses: Int,
)

@Serializable
data class GroupResponse(
    val id: String,
    val name: String,
    val federation: String,
    val season: String,
    val promotionSpots: Int?,
    val relegationSpots: Int?,
    val teamCount: Int,
    val roundsPlayed: Int,
    val totalRounds: Int,
)

@Serializable
data class StandingResponse(
    val teamId: String,
    val team: String,
    val position: Int,
    val played: Int,
    val won: Int,
    val lost: Int,
    val drawn: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val points: Int,
)

@Serializable
data class MatchResponse(
    val id: String,
    val homeTeamId: String,
    val awayTeamId: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val round: String?,
    val status: MatchStatus,
    val playedAt: String?,
)

@Serializable
data class MatchDetailResponse(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val round: String?,
    val playedAt: String?,
    val status: MatchStatus,
    val games: List<GameResponse>,
)

@Serializable
data class GameResponse(
    val id: String,
    val orderInMatch: Int?,
    val competitionName: String?,
    val gameType: GameType,
    val homePlayerId: String? = null,
    val homePlayer2Id: String? = null,
    val awayPlayerId: String? = null,
    val awayPlayer2Id: String? = null,
    val homePlayerName: String?,
    val homePlayer2Name: String? = null,
    val awayPlayerName: String?,
    val awayPlayer2Name: String? = null,
    val homePlayerClassification: String? = null,
    val homePlayer2Classification: String? = null,
    val awayPlayerClassification: String? = null,
    val awayPlayer2Classification: String? = null,
    val homeSets: Int?,
    val awaySets: Int?,
    val result: GameResult,
    val homePlayer1EloDelta: Double? = null,
    val awayPlayer1EloDelta: Double? = null,
    val sets: List<SetResponse>,
)

@Serializable
data class SetResponse(
    val setNumber: Int,
    val homePoints: Int,
    val awayPoints: Int,
)

@Serializable
data class SeasonResponse(
    val id: String,
    val name: String,
)

@Serializable
data class FederationResponse(
    val id: String,
    val name: String,
)

@Serializable
data class PlayerResponse(
    val id: String,
    val fullName: String,
    val licenceNr: String?,
    val currentClubName: String? = null,
    val classification: String? = null,
    /** Class derived from the up-to-date ELO (may differ from the official [classification]). */
    val liveClassification: String? = null,
    /** Latest officially-rated ELO. */
    val currentElo: Int? = null,
    /** Up-to-date ELO including provisional deltas of matches not yet officially rated. */
    val liveElo: Int? = null,
    val isSyncing: Boolean = false,
)

@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)

@Serializable
data class EloEntryResponse(
    val eloValue: Int,
    val recordedAt: String,
)

@Serializable
data class StatsResponse(
    val registeredPlayers: Long,
    val matchesLast24h: Long,
)

/**
 * A follow: an entity the user is interested in. [notify] is the bell (push
 * subscription), off by default.
 */
@Serializable
data class FollowResponse(
    val id: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val notify: Boolean,
)

@Serializable
data class FollowCheckResponse(
    val following: Boolean,
    val followId: String?,
    val notify: Boolean,
)

@Serializable
data class FollowRequest(
    val targetType: String,
    val targetId: String,
)

/** Body for PATCH /follows/{id} — toggles the notify (bell) flag. */
@Serializable
data class FollowNotifyRequest(
    val notify: Boolean,
)

@Serializable
data class PushSubscriptionRequest(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
)

@Serializable
data class PushUnsubscribeRequest(
    val endpoint: String,
)

@Serializable
data class NextMatchResponse(
    val matchId: String,
    val homeTeam: String,
    val awayTeam: String,
    val playerTeamId: String,
    val playerTeamName: String,
    val playedAt: String?,
    val round: String?,
    val groupId: String,
    val groupName: String,
)

@Serializable
data class ClassHistoryEntryResponse(
    val classification: String,
    val seasonName: String,
)

@Serializable
data class LeagueContextResponse(
    val teamId: String,
    val teamName: String,
    val groupId: String,
    val groupName: String,
    val position: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val scheduledMatchCount: Int,
)

@Serializable
data class WinRateResponse(
    val wins: Int,
    val games: Int,
)

@Serializable
data class SetScoreBucketResponse(
    val playerSets: Int,
    val opponentSets: Int,
    val count: Int,
)

@Serializable
data class OpponentBucketResponse(
    /** Exact class label (e.g. "B12"), or the sentinel "HIGHER" / "LOWER" for aggregated tiers. */
    val label: String,
    val wins: Int,
    val games: Int,
    /** For HIGHER/LOWER sentinels: the boundary class closest to the player's own class. */
    val nearClass: String? = null,
    /** For HIGHER/LOWER sentinels: the boundary class furthest from the player's own class. */
    val farClass: String? = null,
)

@Serializable
data class MonthlyFormResponse(
    val month: String,
    val wins: Int,
    val losses: Int,
)

@Serializable
data class CompetitionStatResponse(
    val name: String,
    val wins: Int,
    val games: Int,
    val isTournament: Boolean,
)

/**
 * Aggregated stats for a player's current season (league + tournament singles). Counts are raw
 * wins/games pairs — the client renders percentages. Deliberately ELO-free: rating lives on the
 * Overview tab. Opponent buckets keep classes near the player's own class separate and aggregate
 * the far-higher / far-lower ones into "HIGHER" / "LOWER".
 */
@Serializable
data class PlayerSeasonStatsResponse(
    val seasonName: String,
    val totalGames: Int,
    val overall: WinRateResponse,
    /** Last 10 decided games, oldest → newest; true = win. */
    val recentForm: List<Boolean>,
    val opponentBuckets: List<OpponentBucketResponse>,
    val setDistribution: List<SetScoreBucketResponse>,
    val setsWon: Int,
    val setsLost: Int,
    val deuceSetsWon: Int,
    val deuceSetsTotal: Int,
    val tightGameWins: Int,
    val tightGames: Int,
    val comebackWins: Int,
    /** Games where the player was trailing in sets at any point (regardless of outcome). */
    val comeFromBehindGames: Int,
    /** Subset of [comeFromBehindGames] that the player won. */
    val comeFromBehindWins: Int,
    val monthly: List<MonthlyFormResponse>,
    val longestWinStreak: Int,
    val currentWinStreak: Int,
    val bestWinOpponentName: String?,
    val bestWinOpponentClass: String?,
    val competitions: List<CompetitionStatResponse>,
)

/**
 * Everything needed to render a head-to-head comparison between two players. Both players' season
 * stats are current-season (mirrors [PlayerSeasonStatsResponse]); the direct [record] and [games]
 * are all-time, since that is what a "rivalry" means to users.
 */
@Serializable
data class HeadToHeadResponse(
    val playerA: PlayerResponse,
    val playerB: PlayerResponse,
    val statsA: PlayerSeasonStatsResponse,
    val statsB: PlayerSeasonStatsResponse,
    val record: H2HRecordResponse,
    /** Direct encounters, newest first. */
    val games: List<H2HGameResponse>,
)

@Serializable
data class H2HRecordResponse(
    val aWins: Int,
    val bWins: Int,
    val games: Int,
)

@Serializable
data class H2HGameResponse(
    val gameId: String,
    val matchId: String?,
    val playedAt: String?,
    val competitionName: String?,
    val aSets: Int?,
    val bSets: Int?,
    val aWon: Boolean,
    val sets: List<SetResponse>,
)

@Serializable
data class PlayerGameResponse(
    val matchId: String?,
    val gameId: String,
    val playedAt: String?,
    val homeTeam: String?,
    val awayTeam: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val round: String?,
    val status: MatchStatus?,
    val competitionName: String?,
    val playerSide: String,
    val opponentId: String?,
    val opponentName: String?,
    val opponentClassification: String?,
    val homeSets: Int?,
    val awaySets: Int?,
    val result: GameResult,
    val eloDelta: Double?,
    /** True when [eloDelta] is our own provisional estimate (match not yet officially rated). */
    val eloDeltaProvisional: Boolean = false,
    val sets: List<SetResponse>,
)
