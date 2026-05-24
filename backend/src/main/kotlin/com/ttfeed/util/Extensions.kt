package com.ttfeed.util

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.text.Normalizer
import java.util.UUID

/** Parses a UUID string, returning null on any malformed input instead of throwing. */
fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/** Returns the authenticated user ID from the JWT principal, or null if not authenticated. */
fun ApplicationCall.userIdOrNull(): String? = principal<JWTPrincipal>()?.payload?.subject

/** Returns the authenticated user ID. Throws if called outside an authenticated route. */
fun ApplicationCall.userId(): String = userIdOrNull() ?: error("userId() called on an unauthenticated route")

/**
 * Strips diacritics and lowercases a string for accent-insensitive comparison.
 * e.g. "Grégory" → "gregory", "Müller" → "muller"
 */
fun accentFold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .lowercase()

/**
 * Converts a knob.ch name ("Lastname Firstname") to "Firstname Lastname".
 * Splits on the first space only, so compound first names ("Hans Peter") are preserved.
 */
fun normalizeKnobName(raw: String): String {
    val trimmed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
    val idx = trimmed.indexOf(' ')
    return if (idx == -1) trimmed else "${trimmed.substring(idx + 1)} ${trimmed.substring(0, idx)}"
}

/**
 * Converts a click-tt name ("Lastname, Firstname") to "Firstname Lastname".
 * Only kept for legacy callers — prefer [clickTtNameToDb] for storage.
 */
fun normalizeClickTtName(raw: String): String {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    val parts = normalized.split(",", limit = 2)
    return if (parts.size == 2) "${parts[1].trim()} ${parts[0].trim()}" else normalized.trim()
}

/**
 * Converts a click-tt name ("Lastname, Firstname") to "Lastname Firstname" for DB storage.
 * This matches the knob.ch storage format — lastname first, no comma, NFC-normalised.
 */
fun clickTtNameToDb(raw: String): String {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    val parts = normalized.split(",", limit = 2)
    return if (parts.size == 2) "${parts[0].trim()} ${parts[1].trim()}" else normalized.trim()
}
