package com.solisium.core.meta

import com.solisium.core.domain.BuildAdvice
import com.solisium.core.json.JsonParser
import com.solisium.core.json.JsonValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Optional local Ollama narrator. Receives engine JSON only. Any failure is silent:
 * the deterministic briefing stays on screen.
 */
class OllamaNarrator(
    private val endpoint: String = System.getenv("SOLISIUM_OLLAMA_URL")
        ?: "http://127.0.0.1:11434/api/generate",
    private val model: String = System.getenv("SOLISIUM_OLLAMA_MODEL") ?: "llama3.2",
    private val timeout: Duration = Duration.ofSeconds(8),
) {
    fun explain(advice: BuildAdvice): String? {
        val facts = buildString {
            appendLine("Goal: ${advice.goalLabel}")
            appendLine("Warehouse build: ${advice.snapshotBuild ?: "unknown"}")
            appendLine(advice.scoringNote)
            advice.briefing.forEach { appendLine("- $it") }
            advice.slots.take(6).forEach { slot ->
                val top = slot.recommended.firstOrNull()?.let { "${it.name} score ${it.score}" } ?: "none"
                val you = slot.equipped?.let { "${it.name} score ${it.score}" } ?: "none equipped"
                appendLine("Slot ${slot.slot}: you=$you recommended=$top")
            }
        }
        val prompt = """
            You are a read-only Throne and Liberty companion.
            Rephrase the facts below in 4 short sentences.
            Do not invent stats, DPS, percentages, or items that are not listed.
            If something is missing, say it is missing.
            Facts:
            $facts
        """.trimIndent()
        val body = """{"model":${jsonString(model)},"prompt":${jsonString(prompt)},"stream":false,"options":{"num_predict":220}}"""
        return runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            val request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return null
            val parsed = JsonParser.parse(response.body())
            val text = when (val value = parsed.child("response")) {
                is JsonValue.Str -> value.value.trim()
                else -> parsed.str("response")
            }
            text?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
