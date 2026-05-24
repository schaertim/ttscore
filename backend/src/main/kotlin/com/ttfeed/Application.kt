package com.ttfeed

import com.ttfeed.database.configureDatabase
import com.ttfeed.plugins.configureAuthentication
import com.ttfeed.plugins.configureCors
import com.ttfeed.plugins.configureRouting
import com.ttfeed.plugins.configureSerialization
import com.ttfeed.scraper.clicktt.ClickTTSeasonScraper
import com.ttfeed.scraper.knob.BackfillScraper
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

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
}
