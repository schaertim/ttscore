package com.ttscore.plugins

import com.ttscore.routes.*
import com.ttscore.service.PushService
import com.ttscore.service.StripeService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val vapidConfig = environment.config.config("vapid")
    PushService.init(
        publicKey = vapidConfig.property("publicKey").getString(),
        privateKey = vapidConfig.property("privateKey").getString(),
        subject = vapidConfig.property("subject").getString(),
    )

    val stripeConfig = environment.config.config("stripe")
    StripeService.init(
        secretKey = stripeConfig.property("secretKey").getString(),
        webhookSecret = stripeConfig.property("webhookSecret").getString(),
        priceMonthly = stripeConfig.property("priceMonthly").getString(),
        priceYearly = stripeConfig.property("priceYearly").getString(),
        frontendUrl = stripeConfig.property("frontendUrl").getString(),
    )

    routing {
        // Liveness probe for Railway healthchecks.
        get("/health") {
            call.respondText("OK")
        }

        route("/api/v1") {
            seasonRoutes()
            federationRoutes()
            groupRoutes()
            teamRoutes()
            matchRoutes()
            playerRoutes()
            statsRoutes()
            userProfileRoutes()
            followRoutes()
            pushRoutes()
            stripeRoutes()
        }
    }
}
