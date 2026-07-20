package com.ttscore.scraper.knob

import com.ttscore.database.Games
import com.ttscore.database.Groups
import com.ttscore.database.Matches
import com.ttscore.database.Players
import com.ttscore.database.Seasons
import com.ttscore.database.dbQuery
import com.ttscore.service.ClassificationService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fills classification for knob-era players from their "Follow-your-player" profile page.
 *
 * The per-match bracket the match scraper used to read is on a *women's* ladder for women's-only
 * divisions, which mis-maps to a wildly-too-high men's class. The profile page's class column is
 * always on the men's ladder, listed per (division, season), which is exactly the grain of our own
 * matches — so for each of a player's games we look up the class for that game's (gruppe, season)
 * and record it into the half the game's date falls in. Runs after match scraping (games must
 * already exist) and fetches each player's profile exactly once.
 */
class KnobPlayerClassScraper(
    private val client: KnobClient,
    private val parser: KnobParser,
) {
    private val logger = LoggerFactory.getLogger(KnobPlayerClassScraper::class.java)

    suspend fun run() {
        val players = playersWithKnobId()
        logger.info("KnobPlayerClassScraper: ${players.size} players with a knob id")

        val processed = AtomicInteger(0)
        val updated = AtomicInteger(0)
        players.mapConcurrent(SCRAPE_CONCURRENCY) { (playerId, knobId) ->
            try {
                val profile = parser.parsePlayerProfileClasses(client.fetchPlayerProfile(knobId))
                if (profile.isNotEmpty()) updated.addAndGet(recordClassesFor(playerId, profile))
            } catch (e: Exception) {
                logger.warn("KnobPlayerClassScraper: failed for player $playerId (knobId=$knobId): ${e.message}")
            }
            val done = processed.incrementAndGet()
            if (done % 200 == 0) logger.info("KnobPlayerClassScraper progress: $done / ${players.size}")
        }

        logger.info("KnobPlayerClassScraper complete — ${updated.get()} season-half classes written")
    }

    private suspend fun playersWithKnobId(): List<Pair<UUID, Int>> =
        dbQuery {
            Players.select(Players.id, Players.knobId)
                .where { Players.knobId.isNotNull() }
                .map { it[Players.id] to it[Players.knobId]!! }
        }

    /**
     * Looks up each of the player's knob-league games by its (gruppe, season) in [profile] and
     * records the resulting men's-ladder class into the half its date falls in. Dedupes to at most
     * one write per (season, half) — later games win, so a division played across the Jan boundary
     * settles on its most recent class. Returns the number of season-half classes written.
     */
    private suspend fun recordClassesFor(
        playerId: UUID,
        profile: Map<Pair<Int, String>, String>,
    ): Int =
        dbQuery {
            val byHalf = mutableMapOf<Pair<UUID, ClassificationService.Half>, String>()
            (Games innerJoin Matches innerJoin Groups innerJoin Seasons)
                .select(Groups.knobGruppe, Seasons.id, Seasons.name, Games.playedAt)
                .where {
                    ((Games.homePlayer1Id eq playerId) or (Games.awayPlayer1Id eq playerId)) and
                        Groups.knobGruppe.isNotNull()
                }
                .forEach { row ->
                    val gruppe = row[Groups.knobGruppe] ?: return@forEach
                    val playedAt = row[Games.playedAt] ?: return@forEach
                    val className = profile[gruppe to row[Seasons.name]] ?: return@forEach
                    val half = ClassificationService.halfOf(ClassificationService.localDateOf(playedAt))
                    byHalf[row[Seasons.id] to half] = className
                }

            byHalf.forEach { (key, className) ->
                ClassificationService.recordClass(playerId, key.first, key.second, className)
            }
            byHalf.size
        }

    companion object {
        fun create(): KnobPlayerClassScraper = KnobPlayerClassScraper(KnobClient(), KnobParser())
    }
}
