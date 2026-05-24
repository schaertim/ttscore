package com.ttfeed.routes

import com.ttfeed.model.PushSubscriptionRequest
import com.ttfeed.model.PushUnsubscribeRequest
import com.ttfeed.service.PushService
import com.ttfeed.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pushRoutes() {
    route("/push") {
        /** Returns the VAPID public key so the browser can create a push subscription. */
        get("/vapid-public-key") {
            call.respond(mapOf("publicKey" to PushService.getPublicKey()))
        }

        authenticate("auth-jwt") {
            /** Saves a browser push subscription for the current user. */
            post("/subscriptions") {
                val body = call.receive<PushSubscriptionRequest>()
                PushService.saveSubscription(call.userId(), body.endpoint, body.p256dh, body.auth)
                call.respond(HttpStatusCode.Created)
            }

            /** Removes a push subscription (e.g. when the user disables notifications). */
            delete("/subscriptions") {
                val body = call.receive<PushUnsubscribeRequest>()
                PushService.removeSubscription(call.userId(), body.endpoint)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
