package com.ttscore.service

import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Stripe integration for the single "Pro" subscription (monthly / yearly).
 *
 * Flow: the frontend hits [createCheckoutSession] and redirects the user to Stripe. Stripe
 * then drives everything else through signed webhooks ([constructEvent] + [handleEvent]),
 * which are the single source of truth for the `pro_until` entitlement. The billing portal
 * ([createBillingPortalSession]) lets users manage or cancel their subscription.
 *
 * Payment methods (cards + TWINT) are configured in the Stripe dashboard and surfaced
 * automatically by Checkout for CHF — we deliberately don't pin `payment_method_types`.
 */
object StripeService {
    private val logger = LoggerFactory.getLogger(StripeService::class.java)

    private var webhookSecret: String = ""
    private var priceMonthly: String = ""
    private var priceYearly: String = ""
    private var frontendUrl: String = ""

    /** True once a secret key is configured; billing routes 503 until then. */
    var enabled: Boolean = false
        private set

    fun init(
        secretKey: String,
        webhookSecret: String,
        priceMonthly: String,
        priceYearly: String,
        frontendUrl: String,
    ) {
        if (secretKey.isBlank()) {
            logger.warn("Stripe secret key not configured — billing routes are disabled.")
            return
        }
        Stripe.apiKey = secretKey
        this.webhookSecret = webhookSecret
        this.priceMonthly = priceMonthly
        this.priceYearly = priceYearly
        this.frontendUrl = frontendUrl.trimEnd('/')
        enabled = true
        logger.info("Stripe billing enabled.")
    }

    private fun priceIdFor(plan: String): String? =
        when (plan) {
            "monthly" -> priceMonthly.takeIf { it.isNotBlank() }
            "yearly" -> priceYearly.takeIf { it.isNotBlank() }
            else -> null
        }

    /**
     * Creates a subscription Checkout Session and returns its hosted URL, or null for an
     * unknown/unconfigured plan. Carries `user_id` on both the session (`client_reference_id`)
     * and the subscription metadata so every downstream webhook can resolve the user.
     */
    suspend fun createCheckoutSession(
        userId: String,
        email: String?,
        existingCustomerId: String?,
        plan: String,
    ): String? {
        val priceId = priceIdFor(plan) ?: return null
        return withContext(Dispatchers.IO) {
            val builder =
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl("$frontendUrl/pro?checkout=success")
                    .setCancelUrl("$frontendUrl/pro?checkout=cancelled")
                    .setClientReferenceId(userId)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId)
                            .build(),
                    )
                    .setSubscriptionData(
                        SessionCreateParams.SubscriptionData.builder()
                            .putMetadata("user_id", userId)
                            .build(),
                    )

            // Reuse the existing customer on repeat checkouts; otherwise let Stripe create one
            // and prefill the email so the user isn't asked for it again.
            if (existingCustomerId != null) {
                builder.setCustomer(existingCustomerId)
            } else if (!email.isNullOrBlank()) {
                builder.setCustomerEmail(email)
            }

            Session.create(builder.build()).url
        }
    }

    /** Creates a billing-portal session for an existing customer and returns its hosted URL. */
    suspend fun createBillingPortalSession(customerId: String): String =
        withContext(Dispatchers.IO) {
            val params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl("$frontendUrl/account")
                    .build()
            com.stripe.model.billingportal.Session.create(params).url
        }

    /** Verifies the Stripe signature and parses the event, or throws if the signature is invalid. */
    fun constructEvent(
        payload: String,
        signatureHeader: String,
    ): Event = Webhook.constructEvent(payload, signatureHeader, webhookSecret)

    /**
     * Applies a verified webhook to the `pro_until` entitlement. Subscription events are the
     * source of truth: we set `pro_until` from the item-level period end while the subscription
     * is live, and clear it once it lapses. `checkout.session.completed` only records the
     * customer↔user mapping (used by the billing portal + as a webhook-ordering fallback).
     */
    suspend fun handleEvent(event: Event) {
        val stripeObject =
            event.dataObjectDeserializer.getObject().orElseGet {
                event.dataObjectDeserializer.deserializeUnsafe()
            }

        when (event.type) {
            "checkout.session.completed" -> {
                val session = stripeObject as Session
                val userId = session.clientReferenceId
                val customerId = session.customer
                if (userId != null && customerId != null) {
                    UserProfileService.linkStripeCustomer(userId, customerId)
                    logger.info("Linked Stripe customer {} to user {}", customerId, userId)
                }
            }

            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            -> applySubscription(stripeObject as Subscription)

            else -> logger.debug("Ignoring unhandled Stripe event type: {}", event.type)
        }
    }

    private suspend fun applySubscription(subscription: Subscription) {
        val userId =
            subscription.metadata?.get("user_id")
                ?: subscription.customer?.let { UserProfileService.findUserIdByStripeCustomer(it) }
        if (userId == null) {
            logger.warn("Subscription {} has no resolvable user (status={})", subscription.id, subscription.status)
            return
        }

        // Grant Pro through the current paid period while the sub is live (incl. past_due grace);
        // revoke once it is canceled/unpaid/incomplete.
        val proUntil =
            when (subscription.status) {
                "active", "trialing", "past_due" -> periodEndOf(subscription)
                else -> null
            }

        UserProfileService.setProUntil(userId, proUntil)
        logger.info("User {} pro_until set to {} (subscription {} status {})", userId, proUntil, subscription.id, subscription.status)
    }

    /** Item-level current period end (Stripe moved this off the subscription object in Basil). */
    private fun periodEndOf(subscription: Subscription): OffsetDateTime? =
        subscription.items?.data?.firstOrNull()?.currentPeriodEnd
            ?.let { OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }
}
