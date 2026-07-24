package com.ttscore.util

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

/** Returns the `email` claim from the Supabase JWT, or null if absent. */
fun ApplicationCall.userEmailOrNull(): String? = principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

/**
 * Strips diacritics and lowercases a string for accent-insensitive comparison.
 * e.g. "Grégory" → "gregory", "Müller" → "muller"
 */
fun accentFold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .lowercase()

/**
 * Converts a click-tt name ("Lastname, Firstname") to "Lastname Firstname" for DB storage.
 * This matches the knob.ch storage format — lastname first, no comma, NFC-normalised.
 */
fun clickTtNameToDb(raw: String): String {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
    val parts = normalized.split(",", limit = 2)
    return if (parts.size == 2) "${parts[0].trim()} ${parts[1].trim()}" else normalized.trim()
}

/**
 * Candidate `(lastname, firstname)` splits of a knob name ("Lastname Firstname") for driving
 * click-tt's Elo-Filter name search, which takes the two fields separately. knob's stored string
 * doesn't mark which internal space is the lastname/firstname boundary, so a single fixed split
 * point misses either compound surnames ("von Dach Maik", "Mota Rocha Pedro" — the boundary is
 * NOT the first space) or compound first names ("Müller Hans Peter" — the boundary IS the first
 * space). Returns candidates in most-likely-first order — single-word surname (first space) then
 * single-word first name (last space, catching the compound-surname case) — followed by every
 * other split point for 4+-word names. Caller tries each in turn and stops at the first that
 * yields a verified match, so the common case only costs one request.
 */
fun knobNameSplitCandidates(name: String): List<Pair<String, String>> {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.size <= 1) return listOf((words.firstOrNull() ?: "") to "")
    val splitPoints = linkedSetOf(1, words.size - 1)
    splitPoints += 2 until words.size - 1
    return splitPoints.map { i ->
        words.subList(0, i).joinToString(" ") to words.subList(i, words.size).joinToString(" ")
    }
}

/**
 * Accent-folded name tokens for person-name comparison: [accentFold] then split on any
 * non-alphanumeric run (so the click-tt comma in "Lastname, Firstname" and hyphens both split).
 * knob's "Lastname Firstname" and click-tt's "Lastname, Firstname" fold to the same token set,
 * making comparison order- and punctuation-independent.
 */
fun personNameTokens(name: String): Set<String> =
    accentFold(name).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

/**
 * Conservative person-name equality across knob/click-tt spellings. True when the smaller token
 * set is a subset of the larger AND they share at least two tokens (surname + a given name) — this
 * tolerates an added middle name or accent drift while rejecting genuinely different people (e.g.
 * "Guryeva Ekaterina" vs "Knabenhans, Harald" share nothing). When either side has fewer than two
 * tokens, falls back to exact set equality. Biased toward false negatives over false positives:
 * a missed link is far cheaper than mis-linking two different people.
 */
fun personNamesSimilar(
    a: String,
    b: String,
): Boolean {
    val ta = personNameTokens(a)
    val tb = personNameTokens(b)
    if (ta.isEmpty() || tb.isEmpty()) return false
    if (ta.size < 2 || tb.size < 2) return ta == tb
    // Both sides have >= 2 tokens, so a subset relation already implies >= 2 shared tokens.
    val (smaller, larger) = if (ta.size <= tb.size) ta to tb else tb to ta
    return smaller.all { it in larger }
}

/** Classic Levenshtein edit distance between two strings (single-char insert/delete/substitute). */
private fun levenshtein(
    a: String,
    b: String,
): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] =
                if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
        }
    }
    return dp[a.length][b.length]
}

/** All permutations of this list — used on name-token lists, which are always short (<=4). */
private fun <T> List<T>.permutations(): List<List<T>> {
    if (isEmpty()) return listOf(emptyList())
    return indices.flatMap { i ->
        val rest = toMutableList().also { it.removeAt(i) }
        rest.permutations().map { perm -> listOf(this[i]) + perm }
    }
}

/**
 * True when [a] and [b] have the same number of name tokens and there's a one-to-one pairing whose
 * summed Levenshtein distance is at most [maxTotalEditDistance] — catches one-or-two-letter
 * typos/misspellings between knob and click-tt ("Ylan" vs "Ylann", "Chevellaz" vs "Chevallaz",
 * "Bazil" vs "Basil") that [personNamesSimilar]'s exact-token comparison rejects. A *total* budget
 * (not per-token) keeps it tight: a two-word name can't drift by two letters in each word.
 *
 * Deliberately weaker evidence than [personNamesSimilar] — a small edit distance alone doesn't rule
 * out two different people with similarly-spelled names (e.g. "Keller Michael" vs "Keller Michel"
 * is a 1-edit difference but plausibly two different people). Callers MUST additionally confirm via
 * an independent signal (the resolved club, in practice) before trusting a near-match.
 */
fun personNamesNearMatch(
    a: String,
    b: String,
    maxTotalEditDistance: Int = 2,
): Boolean {
    val ta = personNameTokens(a).toList()
    val tb = personNameTokens(b).toList()
    if (ta.isEmpty() || ta.size != tb.size) return false
    return tb.permutations().any { perm ->
        ta.indices.sumOf { i -> levenshtein(ta[i], perm[i]) } <= maxTotalEditDistance
    }
}

/**
 * Significant name tokens for club-name matching: accent-folded words of length >= 4 (drops
 * region/branch codes like "zh" or short connector words). Shared by [ClubDedupeJob] (knob's own
 * spelling drift across seasons) and knob<->click-tt club matching.
 */
fun clubNameTokens(name: String): Set<String> =
    accentFold(name).split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()

/** True when two club names share at least one significant token. */
fun clubNamesSimilar(
    a: String,
    b: String,
): Boolean = clubNameTokens(a).any { it in clubNameTokens(b) }
