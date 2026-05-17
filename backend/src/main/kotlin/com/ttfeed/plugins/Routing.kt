package com.ttfeed.plugins

import com.ttfeed.routes.*
import com.ttfeed.service.PushService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val vapidConfig = environment.config.config("vapid")
    PushService.init(
        publicKey = vapidConfig.property("publicKey").getString(),
        privateKey = vapidConfig.property("privateKey").getString(),
        subject    = vapidConfig.property("subject").getString(),
    )

    routing {
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
            favoriteRoutes()
            pushRoutes()
        }
    }
}
