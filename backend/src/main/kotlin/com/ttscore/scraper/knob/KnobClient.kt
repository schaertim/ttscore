package com.ttscore.scraper.knob

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.*
import io.ktor.http.Parameters
import io.ktor.http.parameters
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

    /**
     * knob's player search, keyed by licence number. This is the ONLY page that maps a licence to
     * its player id(s) (`gid`): the licence registry has no gid, and match/profile pages have no
     * licence. A person may span several gids over time — the search returns all of them, which is
     * how we both attach a licence to the right knob player and dedupe multi-gid rows. Needs no
     * session/cookie: a bare POST works.
     */
    suspend fun searchByLicence(licence: String): String =
        submitWithRetry(
            "$baseUrl?search",
            parameters {
                append("f_searchname", "")
                append("f_searchvorname", "")
                append("f_searchliz", licence)
                append("btn_doSearch", "Suchen")
            },
        )

    private suspend fun submitWithRetry(
        url: String,
        form: Parameters,
        maxAttempts: Int = 3,
    ): String {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return client.submitForm(url = url, formParameters = form).bodyAsText(Charsets.ISO_8859_1)
            } catch (e: Exception) {
                lastException = e
                logger.warn("Search attempt ${attempt + 1} failed for $url: ${e.message}")
                delay(500L * (attempt + 1))
            }
        }
        throw lastException!!
    }

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
