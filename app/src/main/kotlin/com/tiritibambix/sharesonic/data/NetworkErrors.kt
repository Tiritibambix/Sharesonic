package com.tiritibambix.sharesonic.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Translates raw network/SSL exceptions into short, actionable, user-facing messages.
 *
 * Velvet servers are entirely self-hosted (see CLAUDE.md — "Velvet server URL" is
 * user-supplied in Settings, with no fixed domain or scheme). The most common support
 * issue we see is a **scheme/port mismatch**: the user enters "https://" for a port
 * that only speaks plain HTTP (or vice versa). That surfaces as a cryptic OkHttp /
 * Conscrypt exception such as "Unable to parse TLS packet header" — meaningless to a
 * non-technical user and previously shown to them verbatim via [Result.Error.message].
 *
 * This maps the exception shapes we actually encounter to short, actionable hints so
 * users can self-diagnose ("try http:// instead of https://") without needing a
 * stack trace from the developer.
 */
fun friendlyNetworkErrorMessage(e: Throwable): String {
    val raw = e.message ?: ""
    return when {
        // Classic "client attempted a TLS handshake against a plain-HTTP endpoint" —
        // OkHttp/Conscrypt receives plaintext HTTP bytes where it expected a TLS
        // ServerHello. This is what "https://" against an http-only port/server looks like.
        (e is SSLException || e is SSLHandshakeException) &&
            raw.contains("parse TLS packet header", ignoreCase = true) ->
            "Couldn't establish a secure (HTTPS) connection: this address/port doesn't " +
            "seem to speak HTTPS. If you entered \"https://\", try \"http://\" instead " +
            "(or ask your server admin which scheme and port to use)."

        e is SSLPeerUnverifiedException ||
            (e is SSLHandshakeException && raw.contains("certificate", ignoreCase = true)) ->
            "The server's SSL certificate couldn't be verified. Self-signed " +
            "certificates aren't supported — ask your server admin for a valid " +
            "certificate, or try \"http://\" if the server also offers a plain HTTP port."

        e is SSLException || e is SSLHandshakeException ->
            "Secure (HTTPS) connection failed${if (raw.isNotBlank()) " ($raw)" else ""}. " +
            "Double-check the server address, or try \"http://\" if this address only " +
            "serves plain HTTP."

        e is UnknownHostException ->
            "Couldn't resolve the server address${if (raw.isNotBlank()) " (\"$raw\")" else ""} " +
            "— check the URL for typos and make sure your device can reach it."

        e is ConnectException ->
            "Couldn't reach the server — check the address and port, and make sure " +
            "it's running and reachable from this network."

        e is SocketTimeoutException ->
            "Connection timed out — the server didn't respond. Check the address and " +
            "that it's reachable from this network."

        raw.contains("CLEARTEXT communication", ignoreCase = true) ->
            "This server only accepts plain HTTP, and the connection was blocked for " +
            "security reasons. Please update Sharesonic to the latest version, which " +
            "allows plain HTTP connections to self-hosted servers."

        else -> raw.ifBlank { "Network error" }
    }
}
