package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionRequest

/**
 * Transporte do SSE `POST /api/v1/sugestoes/stream` (duas fases: `pool`,
 * `justificativa`, `done`). A implementacao concreta e escolhida por
 * plataforma -- no web o `EventSource` nao serve porque o stream e **POST**.
 *
 * Manter a interface em `commonMain` permite trocar o transporte do wasm por
 * `fetch` + `ReadableStream` sem tocar no [Repository] nem na UI.
 */
interface SseTransport {
    suspend fun consume(
        request: SuggestionRequest,
        onEvent: suspend (event: String, payload: SsePayload) -> Unit,
    )
}

class SseException(val action: ApiAction, code: Int) : Exception("falha no stream SSE: HTTP $code: $action")
