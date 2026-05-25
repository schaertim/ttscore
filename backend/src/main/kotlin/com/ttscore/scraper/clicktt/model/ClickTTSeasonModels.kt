package com.ttscore.scraper.clicktt.model

import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus

/**
 * A single group link parsed from the click-tt league overview page.
 * One entry per group (not per division â€” divisions can contain multiple groups).
 */
data class ParsedClickTTGroup(
    val groupId: Int,
    // e.g. "MTTV 25/26"
    val championship: String,
    // e.g. "HE 1. Liga"
    val divisionName: String,
    // e.g. "Herren", "Damen", "Senioren O40"
    val category: String,
)

/**
 * One row from the click-tt group standings table.
 */
data class ParsedClickTTStanding(
    val teamName: String,
    // from teamPortrait?teamtable= href â€” globally unique in click-tt
    val teamTableId: Int,
    val position: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val gamesFor: Int,
    val gamesAgainst: Int,
    val points: Int,
    val isPromotion: Boolean,
    val isRelegation: Boolean,
)

/**
 * One match row parsed from the click-tt Spielplan (meeting schedule).
 * meetingId is null for matches that haven't been played yet.
 */
data class ParsedClickTTMatch(
    val meetingId: Int?,
    // "27.08.2025"
    val date: String,
    // "20:15", null if not listed
    val time: String?,
    // "1", "2", â€¦ as printed in the schedule table
    val round: String?,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: MatchStatus,
)

/**
 * Individual set score within a match detail game.
 */
data class ParsedClickTTSet(
    val setNumber: Int,
    val homePoints: Int,
    val awayPoints: Int,
)

/**
 * One game (singles or doubles) parsed from a click-tt match detail page.
 * Doubles have two person IDs per side; singles have one (the *2 fields are null).
 * Names are in click-tt format: "Lastname, Firstname".
 */
data class ParsedClickTTGame(
    val orderInMatch: Int,
    val gameType: GameType,
    // Home side
    val homePersonId: Int?,
    val homeName: String?,
    val homeKlass: String?,
    // doubles player 2 only
    val homePersonId2: Int?,
    val homeName2: String?,
    // doubles player 2 only
    val homeKlass2: String?,
    // Away side
    val awayPersonId: Int?,
    val awayName: String?,
    val awayKlass: String?,
    // doubles player 2 only
    val awayPersonId2: Int?,
    val awayName2: String?,
    // doubles player 2 only
    val awayKlass2: String?,
    // Result
    val homeSets: Int?,
    val awaySets: Int?,
    val result: GameResult,
    val sets: List<ParsedClickTTSet>,
)

data class ParsedClickTTMatchDetail(
    val meetingId: Int,
    val games: List<ParsedClickTTGame>,
)
