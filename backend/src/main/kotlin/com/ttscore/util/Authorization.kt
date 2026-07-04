package com.ttscore.util

import com.ttscore.service.UserProfileService
import io.ktor.server.application.*

/**
 * Whether the calling user currently holds an active Pro entitlement.
 *
 * Returns false for unauthenticated calls instead of throwing, so it is safe to call
 * on optionally-authenticated routes (unauthenticated == not Pro). On protected routes
 * behind `authenticate("auth-jwt")` this is always a real entitlement check.
 */
suspend fun ApplicationCall.isPro(): Boolean {
    val userId = userIdOrNull() ?: return false
    return UserProfileService.isPro(userId)
}
