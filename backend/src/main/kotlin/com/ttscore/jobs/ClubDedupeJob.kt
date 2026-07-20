package com.ttscore.jobs

import com.ttscore.database.Clubs
import com.ttscore.database.Groups
import com.ttscore.database.PlayerSeasons
import com.ttscore.database.Teams
import com.ttscore.database.dbQuery
import com.ttscore.util.clubNamesSimilar
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Merges the duplicate club rows that knob's inconsistent naming leaves behind — the same real club
 * spelled differently between seasons (e.g. "Pinguin ZH" → "Pinguin Zürich"), which name-based
 * resolution stores as two rows.
 *
 * knob's `clubid` can't be trusted for this (it is numbered per federation, so the same number is a
 * different club in another region). Instead this verifies with data we DO trust — the players —
 * and only merges when all three hold, which makes it safe against fusing genuinely different clubs:
 *
 *   1. Names are similar (share a significant token) — a cheap pre-filter.
 *   2. The two clubs NEVER play in the same season — a real club isn't listed under two names at
 *      once, so this excludes distinct same-town clubs (e.g. "Buchs ZH" vs "Buchs Zürich", which
 *      coexist every season).
 *   3. They share a high fraction of the smaller club's players — a rename carries the roster
 *      across the boundary; distinct clubs share ~none.
 *
 * Merging a club is cheap: only team.club_id references it (follows point at teams, which keep their
 * ids), so it is a team repoint + delete.
 */
class ClubDedupeJob {
    private val logger = LoggerFactory.getLogger(ClubDedupeJob::class.java)

    private data class ClubInfo(
        val id: UUID,
        val name: String,
        val hasClicktt: Boolean,
        val seasons: MutableSet<UUID>,
        val players: MutableSet<UUID>,
    )

    suspend fun run() {
        val clubs = loadClubs()
        logger.info("ClubDedupeJob: ${clubs.size} clubs loaded")

        // Union-find over qualifying pairs, so a club spelled 3 ways collapses to one group.
        // Transitivity is safe here: every edge already passed all three checks pairwise.
        val parent = IntArray(clubs.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != c) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }

        for (i in clubs.indices) {
            for (j in i + 1 until clubs.size) {
                val a = clubs[i]
                val b = clubs[j]
                if (!clubNamesSimilar(a.name, b.name)) continue
                if (a.seasons.any { it in b.seasons }) continue // coexist → different clubs

                val shared = a.players.count { it in b.players }
                val smaller = minOf(a.players.size, b.players.size)
                if (smaller == 0) continue
                val overlap = shared.toDouble() / smaller

                // Audit line for every name-similar, non-coexisting candidate — so the thresholds
                // can be eyeballed in the logs whether or not the pair ends up merged.
                logger.info(
                    "  candidate '${a.name}' ~ '${b.name}': $shared/$smaller shared players " +
                        "(${"%.0f".format(overlap * 100)}%)",
                )

                if (shared >= MIN_SHARED_PLAYERS && overlap >= MIN_OVERLAP) {
                    val ra = find(i)
                    val rb = find(j)
                    if (ra != rb) parent[ra] = rb
                }
            }
        }

        val groups = clubs.indices.groupBy { find(it) }.values.filter { it.size > 1 }
        var merged = 0
        for (group in groups) {
            val members = group.map { clubs[it] }
            // Keep the click-tt-linked row (current, click-tt-named); else the one with most players.
            val canonical =
                members.maxWith(
                    compareBy({ it.hasClicktt }, { it.players.size }, { it.name }),
                )
            for (dup in members.filter { it.id != canonical.id }) {
                try {
                    mergeClub(canonical.id, dup.id)
                    logger.info("  merged '${dup.name}' → '${canonical.name}'")
                    merged++
                } catch (e: Exception) {
                    logger.warn("  merge '${dup.name}' → '${canonical.name}' failed: ${e.message}")
                }
            }
        }

        logger.info("ClubDedupeJob complete — $merged duplicate club rows merged into ${groups.size} clubs")
    }

    private suspend fun loadClubs(): List<ClubInfo> =
        dbQuery {
            val infos =
                Clubs.select(Clubs.id, Clubs.name, Clubs.clickttId)
                    .associate { row ->
                        row[Clubs.id] to
                            ClubInfo(
                                id = row[Clubs.id],
                                name = row[Clubs.name],
                                hasClicktt = row[Clubs.clickttId] != null,
                                seasons = mutableSetOf(),
                                players = mutableSetOf(),
                            )
                    }

            (Teams innerJoin Groups)
                .select(Teams.clubId, Groups.seasonId)
                .forEach { infos[it[Teams.clubId]]?.seasons?.add(it[Groups.seasonId]) }

            (Teams innerJoin PlayerSeasons)
                .select(Teams.clubId, PlayerSeasons.playerId)
                .forEach { infos[it[Teams.clubId]]?.players?.add(it[PlayerSeasons.playerId]) }

            infos.values.toList()
        }

    private suspend fun mergeClub(
        canonicalId: UUID,
        dupId: UUID,
    ) = dbQuery {
        Teams.update({ Teams.clubId eq dupId }) { it[Teams.clubId] = canonicalId }
        Clubs.deleteWhere { Clubs.id eq dupId }
    }

    companion object {
        private const val MIN_OVERLAP = 0.5
        private const val MIN_SHARED_PLAYERS = 3

        fun create(): ClubDedupeJob = ClubDedupeJob()
    }
}
