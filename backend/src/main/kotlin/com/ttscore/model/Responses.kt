package com.ttscore.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val homePlayerId: String?,
    val homePlayerName: String?,
)

@Serializable
data class SetHomePlayerRequest(
    val playerId: String,
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
    val seasonName: String,
)

@Serializable
data class StatsResponse(
    val registeredPlayers: Long,
    val matchesLast24h: Long,
)

/** A notification subscription (bell). */
@Serializable
data class FollowResponse(
    val id: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
)

@Serializable
data class FollowCheckResponse(
    val notifying: Boolean,
    val notifyId: String?,
)

@Serializable
data class FollowRequest(
    val targetType: String,
    val targetId: String,
)

/** A starred bookmark (star). */
@Serializable
data class FavoriteResponse(
    val id: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
)

@Serializable
data class FavoriteCheckResponse(
    val favorited: Boolean,
    val favoriteId: String?,
)

@Serializable
data class FavoriteRequest(
    val targetType: String,
    val targetId: String,
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
