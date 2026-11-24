package com.ciberstore.demo.data.sse

/**
 * Parser **puro** de Server-Sent Events (sem rede, sem plataforma).
 *
 * O contrato v1 entrega o stream em dois blocos:
 *
 * ```
 * event: pool
 * data: {"recommendations":[...]}
 *
 * event: justificativa
 * data: {"recommendations":[...]}
 * ```
 *
 * Manter o parser isolado permite testa-lo offline no `commonTest` e usar a
 * mesma implementacao no JVM (Ktor streaming) e no wasm (`fetch`).
 */
data class SseEvent(val name: String, val data: String)

class SseParser {
    private var eventName = ""
    private val dataLines = mutableListOf<String>()

    /**
     * Consome uma linha. Devolve um evento quando o bloco fecha
     * (linha em branco) ou quando um novo `event:` comeca.
     */
    fun feed(line: String): SseEvent? {
        val trimmed = line.trimEnd('\r')
        return when {
            trimmed.isEmpty() -> flush()
            trimmed.startsWith(":") -> null // comentario / keep-alive
            trimmed.startsWith("event:") -> {
                val anterior = flush()
                eventName = trimmed.removePrefix("event:").trim()
                anterior
            }
            trimmed.startsWith("data:") -> {
                dataLines += trimmed.removePrefix("data:").trim()
                null
            }
            else -> null
        }
    }

    /** Fecha o bloco corrente (inclusive no fim do stream). */
    fun flush(): SseEvent? {
        if (dataLines.isEmpty()) {
            eventName = ""
            return null
        }
        val evento = SseEvent(eventName.ifBlank { "message" }, dataLines.joinToString("\n"))
        eventName = ""
        dataLines.clear()
        return evento
    }

    /** Atalho para corpo ja buffered (wasm / testes). */
    fun parseAll(body: String): List<SseEvent> = buildList {
        body.lineSequence().forEach { linha -> feed(linha)?.let(::add) }
        flush()?.let(::add)
    }
}
