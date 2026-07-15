package com.ttscore.scraper.knob

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class KnobClient {
    private val baseUrl = "https://www.knob.ch/ms/index.php"
    private val logger = LoggerFactory.getLogger(KnobClient::class.java)

    private val client =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            followRedirects = true
        }

    suspend fun fetchDivisionPage(
        gruppeId: Int,
        season: String? = null,
        rvid: Int? = null,
    ): String {
        val url =
            buildString {
                append("$baseUrl?gruppe=$gruppeId&listmode=2")
                if (rvid != null) append("&rvid=$rvid")
                if (season != null) append("&ms=${season.replace("/", "")}")
            }
        return fetchWithRetry(url)
    }

    suspend fun fetchMatchDetail(
        gruppeId: Int,
        matchId: Int,
        season: String,
        rvid: Int?,
    ): String {
        val url =
            buildString {
                append("$baseUrl?gruppe=$gruppeId&matchid=$matchId&ms=${season.replace("/", "")}")
                if (rvid != null) append("&rvid=$rvid")
            }
        return fetchWithRetry(url)
    }

    suspend fun fetchOverallPlayers(season: String): String {
        val url = "$baseUrl?overall=5&ms=${season.replace("/", "")}"
        return fetchWithRetry(url)
    }

    /**
     * The player's "Follow-your-player" profile page (all seasons/divisions they played, each with
     * the class on the men's ladder). The source of truth for classification — the per-match bracket
     * is on a women's ladder for women's-only divisions, but this column never is.
     */
    suspend fun fetchPlayerProfile(knobId: Int): String = fetchWithRetry("$baseUrl?gid=$knobId")

    private suspend fun fetchWithRetry(
        url: String,
        maxAttempts: Int = 3,
    ): String {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return client.get(url).bodyAsText(Charsets.ISO_8859_1)
            } catch (e: Exception) {
                lastException = e
                logger.warn("Fetch attempt ${attempt + 1} failed for $url: ${e.message}")
                delay(500L * (attempt + 1))
            }
        }
        throw lastException!!
    }

    fun close() = client.close()
}
