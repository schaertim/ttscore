package com.ttscore.scraper.knob

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * How many knob.ch page fetches may be in flight at once. knob has no rate limiting, so this
 * is bounded only to keep memory, open sockets, and CPU (HTML parsing) sane — not for politeness.
 * Kept at/under CIO's default per-host connection cap (100) so no engine tuning is required.
 */
const val SCRAPE_CONCURRENCY = 24

/**
 * Runs [transform] over every element with at most [concurrency] coroutines in flight at once,
 * preserving input order in the result. Intended for the network+parse stage of scraping — the
 * caller is responsible for keeping DB writes serialized (do them after this returns), since the
 * backfill runs against a non-pooled Exposed connection.
 */
suspend fun <T, R> List<T>.mapConcurrent(
    concurrency: Int,
    transform: suspend (T) -> R,
): List<R> =
    coroutineScope {
        val gate = Semaphore(concurrency)
        map { item -> async { gate.withPermit { transform(item) } } }.awaitAll()
    }
