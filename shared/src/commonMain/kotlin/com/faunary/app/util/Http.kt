package com.faunary.app.util

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/** GET [url] and return the body; throws on network errors and non-2xx answers. */
suspend fun HttpClient.getText(url: String): String {
    val response = get(url) {
        timeout {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
    }
    if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
    return response.bodyAsText()
}
