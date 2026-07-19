package com.ttscore.database

import com.ttscore.model.FollowTargetType
import com.ttscore.model.GameResult
import com.ttscore.model.GameType
import com.ttscore.model.MatchStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import org.postgresql.util.PGobject

object Seasons : Table("season") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 9)
    override val primaryKey = PrimaryKey(id)
}

/** Ledger of one-time scraper backfills — see V17 migration and BackfillLedger. */
object ScraperBackfill : Table("scraper_backfill") {
    val key = text("key")
    val completedAt = timestampWithTimeZone("completed_at")
    override val primaryKey = PrimaryKey(key)
}

object Federations : Table("federation") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 50)
    override val primaryKey = PrimaryKey(id)
}

object Groups : Table("division_group") {
    val id = uuid("id").autoGenerate()
    val federationId = uuid("federation_id").references(Federations.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    val name = varchar("name", 50)
    val knobGruppe = integer("knob_gruppe").nullable() // null for click-tt groups
    val clickttId = integer("clicktt_id").nullable() // null for knob groups
    val promotionSpots = short("promotion_spots").nullable()
    val relegationSpots = short("relegation_spots").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Standings : Table("standing") {
    val id = uuid("id").autoGenerate()
    val groupId = uuid("group_id").references(Groups.id)
    val teamId = uuid("team_id").references(Teams.id)
    val position = short("position")
    val played = short("played")
    val won = short("won")
    val drawn = short("drawn")
    val lost = short("lost")
    val gamesFor = short("games_for")
    val gamesAgainst = short("games_against")
    val points = short("points")
    override val primaryKey = PrimaryKey(id)
}

object Clubs : Table("club") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 100)
    val clickttId = integer("clicktt_id").nullable().uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

object Teams : Table("team") {
    val id = uuid("id").autoGenerate()
    val clubId = uuid("club_id").references(Clubs.id)
    val groupId = uuid("group_id").references(Groups.id)
    val name = varchar("name", 100)

    // knobId is NOT globally unique — team IDs are reused across seasons, unique only within a group
    val knobId = integer("knob_id").nullable()
    val clickttId = integer("clicktt_id").nullable() // teamtable= param, globally unique in click-tt
    override val primaryKey = PrimaryKey(id)
}

object Players : Table("player") {
    val id = uuid("id").autoGenerate()
    val licenceNr = varchar("licence_nr", 20).nullable().uniqueIndex()
    val knobId = integer("knob_id").nullable().uniqueIndex()
    val clickttId = integer("clicktt_id").nullable().uniqueIndex()
    val fullName = varchar("full_name", 100)

    /** "MALE" or "FEMALE" — populated from the click-tt club members page */
    val sex = varchar("sex", 6).nullable()

    /** STT age/eligibility category ("Aktive", "O40", "U19", …) — from click-tt club page */
    val category = varchar("category", 20).nullable()

    /** ISO 3-letter country code ("SUI", "GER", …) — from click-tt club page */
    val nationality = varchar("nationality", 3).nullable()

    /** How the click-tt link was established (see PlayerService.MatchMethod); null = knob-only. */
    val matchMethod = varchar("match_method", 20).nullable()
    override val primaryKey = PrimaryKey(id)
}

object PlayerSeasons : Table("player_season") {
    val id = uuid("id").autoGenerate()
    val playerId = uuid("player_id").references(Players.id)
    val teamId = uuid("team_id").references(Teams.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    override val primaryKey = PrimaryKey(id)
}

/**
 * Official federation classification, kept separate from team membership because it is a property
 * of the player (not the team) and tournament-only players have no team row. A season straddles the
 * Jan-1 reclassification, so each season holds two values: first half = Jul-Dec, second = Jan-Jun.
 */
object PlayerClassifications : Table("player_classification") {
    val id = uuid("id").autoGenerate()
    val playerId = uuid("player_id").references(Players.id)
    val seasonId = uuid("season_id").references(Seasons.id)
    val firstHalfClass = varchar("first_half_class", 5).nullable()
    val secondHalfClass = varchar("second_half_class", 5).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(playerId, seasonId)
    }
}

object PlayerElos : Table("player_elo") {
    val id = uuid("id").autoGenerate()
    val playerId = uuid("player_id").references(Players.id)
    val seasonId = uuid("season_id").references(Seasons.id).nullable()
    val eloValue = integer("elo_value")
    val recordedAt = timestampWithTimeZone("recorded_at")
    val isProvisional = bool("is_provisional")
    override val primaryKey = PrimaryKey(id)
}

object Matches : Table("match") {
    val id = uuid("id").autoGenerate()
    val groupId = uuid("group_id").references(Groups.id)
    val homeTeamId = uuid("home_team_id").references(Teams.id)
    val awayTeamId = uuid("away_team_id").references(Teams.id)

    // round stores the raw round text from knob — may be a number ("8") or label ("Viertelfinal")
    val round = varchar("round", 50).nullable()
    val playedAt = timestampWithTimeZone("played_at").nullable()
    val homeScore = short("home_score").nullable()
    val awayScore = short("away_score").nullable()

    // knobMatchId is NOT globally unique — match IDs are reused across seasons, unique only within a group
    val knobMatchId = integer("knob_match_id").nullable()
    val clickttMatchId = integer("clicktt_match_id").nullable() // meeting= param, globally unique in click-tt
    val status = enumerationByName("status", 20, MatchStatus::class)
    override val primaryKey = PrimaryKey(id)
}

object Games : Table("game") {
    val id = uuid("id").autoGenerate()
    val matchId = uuid("match_id").references(Matches.id).nullable()
    val competitionName = varchar("competition_name", 255).nullable()
    val gameType = enumerationByName("game_type", 20, GameType::class)
    val orderInMatch = short("order_in_match").nullable()
    val homePlayer1Id = uuid("home_player1_id").references(Players.id).nullable()
    val homePlayer2Id = uuid("home_player2_id").references(Players.id).nullable() // doubles only
    val awayPlayer1Id = uuid("away_player1_id").references(Players.id).nullable()
    val awayPlayer2Id = uuid("away_player2_id").references(Players.id).nullable() // doubles only
    val homePlayer1EloDelta = double("home_player1_elo_delta").nullable()
    val awayPlayer1EloDelta = double("away_player1_elo_delta").nullable()

    // Position in the player's click-tt Elo-Protokoll (0 = topmost row), stored per side like the
    // deltas above. A deterministic tiebreaker for same-day games, which share a played_at.
    val homePlayer1EloOrder = integer("home_player1_elo_order").nullable()
    val awayPlayer1EloOrder = integer("away_player1_elo_order").nullable()
    val homeSets = short("home_sets").nullable()
    val awaySets = short("away_sets").nullable()
    val result = enumerationByName("result", 20, GameResult::class)
    val playedAt = timestampWithTimeZone("played_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object GameSets : Table("game_set") {
    val id = uuid("id").autoGenerate()
    val gameId = uuid("game_id").references(Games.id)
    val setNumber = short("set_number")
    val homePoints = short("home_points")
    val awayPoints = short("away_points")
    override val primaryKey = PrimaryKey(id)
}

/** Shared helper — maps the native PG follow_target_type enum to/from our Kotlin enum. */
private fun targetTypeColumn(table: Table) =
    table.customEnumeration(
        name = "target_type",
        sql = "follow_target_type",
        fromDb = { value -> FollowTargetType.valueOf(value.toString().uppercase()) },
        toDb = { value ->
            PGobject().apply {
                type = "follow_target_type"
                this.value = value.name.lowercase()
            }
        },
    )

/**
 * A follow: the user is interested in this entity (drives the home feed + search).
 * [notify] is the opt-in push subscription (the bell) — off by default, so a follow
 * without notify is a silent bookmark. Enforces the invariant notify ⊆ follow.
 */
object Follows : Table("follow") {
    val id = uuid("id").autoGenerate()
    val userId = text("user_id")
    val targetType = targetTypeColumn(this)
    val targetId = uuid("target_id")
    val notify = bool("notify").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserProfiles : Table("user_profile") {
    val userId = text("user_id")
    val homePlayerId = uuid("home_player_id").references(Players.id).nullable()

    /** Pro entitlement expiry. Null or past = free; a future timestamp = active Pro. */
    val proUntil = timestampWithTimeZone("pro_until").nullable()

    /** Stripe customer id, set on first checkout. Used for the billing portal + webhook lookup. */
    val stripeCustomerId = text("stripe_customer_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Web Push subscriptions for push notifications. */
object PushSubscriptions : Table("push_subscription") {
    val id = uuid("id").autoGenerate()
    val userId = text("user_id")
    val endpoint = text("endpoint").uniqueIndex()
    val p256dh = text("p256dh")
    val auth = text("auth")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
