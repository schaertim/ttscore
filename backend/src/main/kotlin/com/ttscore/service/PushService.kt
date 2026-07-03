package com.ttscore.service

import com.ttscore.database.Follows
import com.ttscore.database.PushSubscriptions
import com.ttscore.database.UserProfiles
import com.ttscore.database.dbQuery
import com.ttscore.model.FollowTargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.Subscription
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

object PushService {
    private val logger = LoggerFactory.getLogger(PushService::class.java)
    private lateinit var vapidPublicKey: String
    private lateinit var webPushService: nl.martijndwars.webpush.PushService

    fun init(
        publicKey: String,
        privateKey: String,
        subject: String,
    ) {
        vapidPublicKey = publicKey
        webPushService = nl.martijndwars.webpush.PushService(publicKey, privateKey, subject)
    }

    fun getPublicKey(): String = vapidPublicKey

    /** Upsert a browser push subscription for the user (idempotent on endpoint). */
    suspend fun saveSubscription(
        userId: String,
        endpoint: String,
        p256dh: String,
        auth: String,
    ) = dbQuery {
        val existing =
            PushSubscriptions.selectAll()
                .where { PushSubscriptions.endpoint eq endpoint }
                .firstOrNull()

        if (existing == null) {
            PushSubscriptions.insert {
                it[PushSubscriptions.userId] = userId
                it[PushSubscriptions.endpoint] = endpoint
                it[PushSubscriptions.p256dh] = p256dh
                it[PushSubscriptions.auth] = auth
                it[createdAt] = OffsetDateTime.now()
            }
        }
    }

    /** Remove a push subscription by endpoint (browser unsubscribe or token expiry). */
    suspend fun removeSubscription(
        userId: String,
        endpoint: String,
    ) = dbQuery {
        PushSubscriptions.deleteWhere {
            (PushSubscriptions.userId eq userId) and (PushSubscriptions.endpoint eq endpoint)
        }
    }

    /** Prune a subscription by its (unique) endpoint after the push service reports it dead. */
    private suspend fun removeDeadSubscription(endpoint: String) =
        dbQuery {
            PushSubscriptions.deleteWhere { PushSubscriptions.endpoint eq endpoint }
        }

    /**
     * Sends a push notification to every subscriber who follows the given target.
     * Failures for individual subscriptions are logged and swallowed so one bad
     * endpoint does not stop the rest.
     */
    suspend fun sendToFollowers(
        targetType: FollowTargetType,
        targetId: UUID,
        title: String,
        body: String,
        url: String,
    ) {
        val userIds =
            dbQuery {
                Follows.selectAll()
                    .where {
                        (Follows.targetType eq targetType) and
                            (Follows.targetId eq targetId) and
                            (Follows.notify eq true)
                    }
                    .map { it[Follows.userId] }
            }
        if (userIds.isEmpty()) return

        // Drop users who have globally paused notifications.
        val pausedUserIds =
            dbQuery {
                UserProfiles.selectAll()
                    .where {
                        (UserProfiles.userId inList userIds) and
                            (UserProfiles.notificationsPaused eq true)
                    }
                    .map { it[UserProfiles.userId] }
                    .toSet()
            }
        val activeUserIds = userIds.filterNot { it in pausedUserIds }
        if (activeUserIds.isEmpty()) return

        val subscriptions =
            dbQuery {
                PushSubscriptions.selectAll()
                    .where { PushSubscriptions.userId inList activeUserIds }
                    .map {
                        Triple(
                            it[PushSubscriptions.endpoint],
                            it[PushSubscriptions.p256dh],
                            it[PushSubscriptions.auth],
                        )
                    }
            }
        if (subscriptions.isEmpty()) return

        val payload = Json.encodeToString(mapOf("title" to title, "body" to body, "url" to url))

        withContext(Dispatchers.IO) {
            for ((endpoint, p256dh, auth) in subscriptions) {
                try {
                    val sub = Subscription(endpoint, Subscription.Keys(p256dh, auth))
                    val notification = Notification(sub, payload)
                    val statusCode = webPushService.send(notification).statusLine.statusCode
                    when {
                        // Subscription is permanently gone — prune it so we stop retrying.
                        statusCode == 404 || statusCode == 410 -> {
                            logger.info("Removing dead push subscription (HTTP $statusCode): ${endpoint.take(40)}…")
                            removeDeadSubscription(endpoint)
                        }
                        statusCode !in 200..299 ->
                            logger.warn("Push returned HTTP $statusCode for endpoint ${endpoint.take(40)}…")
                    }
                } catch (e: Exception) {
                    logger.warn("Push failed for endpoint ${endpoint.take(40)}…: ${e.message}")
                }
            }
        }
    }
}
