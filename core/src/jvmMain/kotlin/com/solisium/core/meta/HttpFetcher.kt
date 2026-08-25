package com.solisium.core.meta

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun interface HttpFetcher {
    fun get(url: String): String
}

class JvmHttpFetcher(
    private val userAgent: String = USER_AGENT,
    private val timeout: Duration = Duration.ofSeconds(12),
) : HttpFetcher {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} for $url")
        }
        return response.body()
    }

    companion object {
        const val USER_AGENT = "SolisiumAutopilot/0.1 (read-only personal companion)"
    }
}
