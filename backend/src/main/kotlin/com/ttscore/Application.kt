package com.ttscore

import com.ttscore.database.configureDatabase
import com.ttscore.plugins.configureAuthentication
import com.ttscore.plugins.configureCors
import com.ttscore.plugins.configureRouting
import com.ttscore.plugins.configureSerialization
import com.ttscore.scraper.clicktt.ClickTTSeasonScraper
import com.ttscore.scraper.knob.BackfillScraper
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
