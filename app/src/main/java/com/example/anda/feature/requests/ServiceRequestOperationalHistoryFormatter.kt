package com.example.anda.feature.requests

import com.example.anda.data.requests.ServiceRequestStatus
import java.text.SimpleDateFormat
import java.util.Locale

object ServiceRequestOperationalHistoryFormatter {

    data class OperationalEvent(
        val stamp: String,
        val stampEpochMs: Long?,
        val eventLabel: String,
        val type: String,
        val docId: String,
        val status: String
    )

    data class Result(
        val historyLines: List<String>,
        val freeNotes: String,
        val latestEvent: OperationalEvent? = null
    )

    fun parse(notes: String): Result {
        if (notes.isBlank()) return Result(emptyList(), "", latestEvent = null)

        val history = mutableListOf<String>()
        val freeNotes = mutableListOf<String>()
        var latestEvent: OperationalEvent? = null

        notes.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                if (!line.startsWith("[ANDA-LINK]")) {
                    freeNotes += line
                    return@forEach
                }
                val event = parseOperationalEvent(line)
                latestEvent = event
                history += formatHistoryLine(event)
            }

        return Result(
            historyLines = history,
            freeNotes = freeNotes.joinToString("\n"),
            latestEvent = latestEvent
        )
    }

    fun latestActionSummary(notes: String): String? {
        val event = parse(notes).latestEvent ?: return null
        return buildString {
            append(event.eventLabel)
            if (event.type.isNotBlank()) {
                append(" • ")
                append(event.type)
            }
            if (event.docId.isNotBlank()) {
                append(" • ")
                append(event.docId)
            }
        }
    }

    fun latestActionEpochMs(notes: String): Long? {
        return parse(notes).latestEvent?.stampEpochMs
    }

    private fun parseOperationalEvent(raw: String): OperationalEvent {
        val stamp = raw.substringAfter("[ANDA-LINK][", missingDelimiterValue = "")
            .substringBefore("]")
            .ifBlank { "Sem horário" }
        val stampEpochMs = parseStampEpoch(stamp)
        val type = raw.extractValue("type=")
        val status = raw.extractValue("status=")
        val docId = raw.extractDocId()
        val event = raw.extractEvent()

        val eventLabel = when (event) {
            "DRAFT" -> "Rascunho salvo"
            "SIGNED" -> "Assinatura local"
            "PDF" -> "PDF exportado"
            else -> "Evento operacional"
        }

        return OperationalEvent(
            stamp = stamp,
            stampEpochMs = stampEpochMs,
            eventLabel = eventLabel,
            type = type,
            docId = docId,
            status = ServiceRequestStatus.normalize(status)
        )
    }

    private fun formatHistoryLine(event: OperationalEvent): String {
        return buildString {
            append(event.stamp)
            append(" • ")
            append(event.eventLabel)
            if (event.type.isNotBlank()) {
                append(" • ")
                append(event.type)
            }
            if (event.docId.isNotBlank()) {
                append(" • ")
                append(event.docId)
            }
            if (event.status.isNotBlank()) {
                append(" • status ")
                append(event.status)
            }
        }
    }

    private fun String.extractValue(prefix: String): String {
        return substringAfter(prefix, missingDelimiterValue = "")
            .substringBefore(' ')
            .trim()
    }

    private fun String.extractDocId(): String {
        return substringAfter("doc=", missingDelimiterValue = "")
            .substringBefore(';')
            .trim()
    }

    private fun String.extractEvent(): String {
        return substringAfter(';', missingDelimiterValue = "")
            .substringBefore(' ')
            .trim()
    }

    private fun parseStampEpoch(stamp: String): Long? {
        return runCatching {
            STAMP_FMT.get()!!.parse(stamp)?.time
        }.getOrNull()
    }

    /** ThreadLocal so concurrent coroutine calls on different threads are safe. */
    private val STAMP_FMT = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    }
}

