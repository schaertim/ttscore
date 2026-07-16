package com.ttscore

import com.ttscore.database.configureDatabase
import com.ttscore.jobs.BackfillLedger
import com.ttscore.jobs.ClickTtIdBackfillJob
import com.ttscore.jobs.ClickTtReverseLookupJob
import com.ttscore.jobs.MatchPollJob
import com.ttscore.jobs.SeasonSyncJob
import com.ttscore.plugins.configureAuthentication
import com.ttscore.plugins.configureCors
import com.ttscore.plugins.configureRouting
import com.ttscore.plugins.configureSerialization
import com.ttscore.scraper.clicktt.ClickTTSeasonScraper
import com.ttscore.scraper.knob.BackfillScraper
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import java.security.Security
import java.time.ZoneId
import java.time.ZonedDateTime

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    Security.addProvider(BouncyCastleProvider())
    configureDatabase()
    configureSerialization()
    configureAuthentication()
    configureRouting()
    configureCors()

    val currentSeason = environment.config.property("scraper.currentSeason").getString()

    if (environment.config.property("jobs.enabled").getString().toBoolean()) {
       runBackfill(currentSeason)
       scheduleJobs(currentSeason)
    }
}

/**
 * One-time seeding of a fresh database, plus an immediate seed of the current season.
 *
 * The historical backfills run at most once ever (guarded by [BackfillLedger]) so restarts
 * never re-trigger the expensive knob history scrape. Their order matters: knob creates the
 * historical players, the id backfill links click-tt person IDs onto them, and only then can
 * the click-tt season seed resolve players in match details.
 *
 * The current-season seed is ledgered per season, so it fires exactly once — on the first
 * boot for a given scraper.currentSeason — while the nightly SeasonSyncJob owns it thereafter.
 */
private fun Application.runBackfill(currentSeason: String) {
    val logger = LoggerFactory.getLogger("Backfill")
    launch {
        try {
            BackfillLedger.runOnce("knob-history-backfill") {
                logger.info("Backfill — knob history (1989→present)")
                BackfillScraper.create().run()
            }
            BackfillLedger.runOnce("clicktt-id-backfill") {
                logger.info("Backfill — click-tt player/club id linking")
                ClickTtIdBackfillJob.create().run()
            }
            BackfillLedger.runOnce("clicktt-season-backfill:$currentSeason") {
                logger.info("Backfill — click-tt season $currentSeason")
                ClickTTSeasonScraper.create().run(currentSeason)
            }

            // Seed the current season once, immediately, so data is available without waiting
            // for the 03:00 run. Keyed by season, so bumping scraper.currentSeason next year
            // triggers exactly one immediate seed on the first boot after the change.
            BackfillLedger.runOnce("clicktt-season-seed:$currentSeason") {
                logger.info("Backfill — seeding current season $currentSeason")
                SeasonSyncJob.create().run(currentSeason)
            }

            logger.info("Backfill complete")
        } catch (e: Exception) {
            logger.error("Backfill failed: ${e.message}", e)
        }
    }
}

private fun Application.scheduleJobs(currentSeason: String) {
    val logger = LoggerFactory.getLogger("Jobs")
    val swissZone = ZoneId.of("Europe/Zurich")

    // MatchPollJob — every 5 minutes: catch newly-finished matches near real time.
    launch {
        val pollJob = MatchPollJob.create()
        while (true) {
            delay(5 * 60 * 1000L)
            try {
                pollJob.run()
            } catch (e: Exception) {
                logger.error("MatchPollJob failed: ${e.message}", e)
            }
        }
    }

    // SeasonSyncJob — every day at 03:00 Swiss time: discover new groups + refresh known
    // ones + scrape any missing match details for the current season.
    launch {
        val seasonSync = SeasonSyncJob.create()
        while (true) {
            val now = ZonedDateTime.now(swissZone)
            val next3am = now.toLocalDate().atStartOfDay(swissZone).plusHours(3)
                .let { if (it.isAfter(now)) it else it.plusDays(1) }
            val delayMs = next3am.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
            delay(delayMs)
            try {
                seasonSync.run(currentSeason)
            } catch (e: Exception) {
                logger.error("SeasonSyncJob failed: ${e.message}", e)
            }
        }
    }

    // ClickTtIdBackfillJob (roster-driven) then ClickTtReverseLookupJob (Elo-Filter, for players no
    // longer on any club roster) — weekly, in sequence so the reverse pass only handles whatever the
    // roster pass leaves unlinked. Both idempotent; independent try/catch so one can't skip the other.
    launch {
        val idBackfill = ClickTtIdBackfillJob.create()
        val reverseLookup = ClickTtReverseLookupJob.create()
        while (true) {
            delay(7 * 24 * 60 * 60 * 1000L)
            try {
                idBackfill.run()
            } catch (e: Exception) {
                logger.error("ClickTtIdBackfillJob failed: ${e.message}", e)
            }
            try {
                reverseLookup.run()
            } catch (e: Exception) {
                logger.error("ClickTtReverseLookupJob failed: ${e.message}", e)
            }
        }
    }
}
