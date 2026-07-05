package com.ttscore.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    // Comma-separated frontend origins (scheme + host + optional port), env-overridable per
    // environment via CORS_ALLOWED_ORIGINS. Defaults to the local SvelteKit dev server.
    val allowedOrigins = environment.config
        .propertyOrNull("cors.allowedOrigins")?.getString()
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("http://localhost:5173")

    install(CORS) {
        allowedOrigins.forEach { origin ->
            val schemeSep = origin.indexOf("://")
            if (schemeSep >= 0) {
                allowHost(origin.substring(schemeSep + 3), schemes = listOf(origin.substring(0, schemeSep)))
            } else {
                allowHost(origin)
            }
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)

        allowCredentials = true
    }
}
