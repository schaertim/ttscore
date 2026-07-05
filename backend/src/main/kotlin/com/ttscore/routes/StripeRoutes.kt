package com.ttscore.routes

import com.stripe.exception.SignatureVerificationException
import com.ttscore.model.BillingUrlResponse
import com.ttscore.model.CheckoutRequest
import com.ttscore.service.StripeService
import com.ttscore.service.UserProfileService
import com.ttscore.util.userEmailOrNull
import com.ttscore.util.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StripeRoutes")

fun Route.stripeRoutes() {
    route("/billing") {
        /**
         * Stripe webhook (public, signature-verified). Source of truth for `pro_until`.
         * Must read the raw body — `receiveText()` gives the exact bytes Stripe signed.
         */
        post("/webhook") {
            if (!StripeService.enabled) return@post call.respond(HttpStatusCode.ServiceUnavailable)

            val signature = call.request.header("Stripe-Signature")
            if (signature == null) return@post call.respond(HttpStatusCode.BadRequest, "Missing signature")

            val payload = call.receiveText()
            val event =
                try {
                    StripeService.constructEvent(payload, signature)
                } catch (e: SignatureVerificationException) {
                    logger.warn("Rejected Stripe webhook: invalid signature", e)
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid signature")
                }

            try {
                StripeService.handleEvent(event)
            } catch (e: Exception) {
                // 500 so Stripe retries — never drop a payment because of a transient failure.
                logger.error("Failed handling Stripe event ${event.id} (${event.type})", e)
                return@post call.respond(HttpStatusCode.InternalServerError)
            }
            call.respond(HttpStatusCode.OK)
        }

        authenticate("auth-jwt") {
            /** Starts a subscription checkout. Body: { "plan": "monthly" | "yearly" }. */
            post("/checkout") {
                if (!StripeService.enabled) return@post call.respond(HttpStatusCode.ServiceUnavailable)

                val userId = call.userId()
                val plan = call.receive<CheckoutRequest>().plan
                val existingCustomer = UserProfileService.getStripeCustomerId(userId)
                val url =
                    StripeService.createCheckoutSession(
                        userId = userId,
                        email = call.userEmailOrNull(),
                        existingCustomerId = existingCustomer,
                        plan = plan,
                    ) ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown plan")

                call.respond(BillingUrlResponse(url))
            }

            /** Opens the Stripe billing portal to manage/cancel the subscription. */
            post("/portal") {
                if (!StripeService.enabled) return@post call.respond(HttpStatusCode.ServiceUnavailable)

                val customerId =
                    UserProfileService.getStripeCustomerId(call.userId())
                        ?: return@post call.respond(HttpStatusCode.NotFound, "No Stripe customer")

                call.respond(BillingUrlResponse(StripeService.createBillingPortalSession(customerId)))
            }
        }
    }
}
