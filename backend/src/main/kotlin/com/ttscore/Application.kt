package com.ttscore

import com.ttscore.database.configureDatabase
import com.ttscore.jobs.MatchPollJob
import com.ttscore.jobs.NightlyGroupSyncJob
import com.ttscore.plugins.configureAuthentication
import com.ttscore.plugins.configureCors
import com.ttscore.plugins.configureRouting
import com.ttscore.plugins.configureSerialization
import com.ttscore.scraper.clicktt.ClickTTSeasonScraper
import com.ttscore.scraper.clicktt.ClickTTSyncService
import com.ttscore.scraper.knob.BackfillScraper
import com.ttscore.service.SeasonService
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
    runBackfill()
}

private fun Application.runBackfill() {
    val logger = LoggerFactory.getLogger("Backfill")
    launch {
        try {
            logger.info("Backfill starting — knob 2024/2025")
            BackfillScraper.create().runForSeason("2024/2025")

            logger.info("Backfill — clicktt 2025/2026")
            ClickTTSeasonScraper.create().run("2025/2026")

            logger.info("Backfill — portrait sync (ELO + tournament games)")
            val seasonId = SeasonService.getCurrentSeasonId()
            if (seasonId != null) {
                ClickTTSyncService.runPortraitBackfill(seasonId)
            } else {
                logger.warn("Backfill — no current season found, skipping portrait sync")
            }

            logger.info("Backfill complete")
        } catch (e: Exception) {
            logger.error("Backfill failed: ${e.message}", e)
        }
    }
}

private fun Application.scheduleJobs() {
    val logger = LoggerFactory.getLogger("Jobs")
    val swissZone = ZoneId.of("Europe/Zurich")

    // MatchPollJob — every 5 minutes
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

    // NightlyGroupSyncJob — every day at 03:00 Swiss time
    launch {
        val nightlyJob = NightlyGroupSyncJob.create()
        while (true) {
            val now = ZonedDateTime.now(swissZone)
            val next3am = now.toLocalDate().atStartOfDay(swissZone).plusHours(3)
                .let { if (it.isAfter(now)) it else it.plusDays(1) }
            val delayMs = next3am.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
            delay(delayMs)
            try {
                val season = SeasonService.getCurrentSeason()
                if (season != null) {
                    nightlyJob.run(season.first, season.second)
                } else {
                    logger.warn("NightlyGroupSyncJob: no current season found, skipping")
                }
            } catch (e: Exception) {
                logger.error("NightlyGroupSyncJob failed: ${e.message}", e)
            }
        }
    }
}
