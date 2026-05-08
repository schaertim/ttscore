package com.ttfeed.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

fun Application.configureAuthentication() {
    val supabaseUrl = environment.config.property("supabase.url").getString()
    val issuer = "$supabaseUrl/auth/v1"

    // Read the EC P-256 public key coordinates from config.
    // These come from: Supabase dashboard → Project Settings → API → JWT Keys → current key → Key Details.
    val keyX = environment.config.property("supabase.jwtKeyX").getString()
    val keyY = environment.config.property("supabase.jwtKeyY").getString()

    val algorithm = Algorithm.ECDSA256(buildECPublicKey(keyX, keyY), null)

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withClaimPresence("sub")
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject
                if (!subject.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired token")
            }
        }
    }
}

/**
 * Reconstructs a P-256 ECPublicKey from the base64url-encoded x and y
 * coordinates found in a JWK (as shown in Supabase's Key Details dialog).
 */
private fun buildECPublicKey(xBase64: String, yBase64: String): ECPublicKey {
    val decoder = Base64.getUrlDecoder()
    val x = BigInteger(1, decoder.decode(xBase64))
    val y = BigInteger(1, decoder.decode(yBase64))

    val params = java.security.AlgorithmParameters.getInstance("EC").also {
        it.init(ECGenParameterSpec("secp256r1")) // P-256
    }
    val ecParams = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
    val keySpec = ECPublicKeySpec(ECPoint(x, y), ecParams)

    return KeyFactory.getInstance("EC").generatePublic(keySpec) as ECPublicKey
}
