package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionRequest
import com.ciberstore.demo.data.sse.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json

/**
 * Transporte SSE sobre Ktor, usado por Android e Desktop.
 *
 * O segredo e `bodyAsChannel()`: entrega as linhas **assim que chegam**, o
 * que preserva a regra de ouro do contrato -- a UI renderiza no `pool`
 * (~ms) e nao espera a `justificativa` do LLM (ate 30 s).
 *
 * Nenhum timeout de leitura e configurado no `HttpClient`: o cliente de
 * referencia em `cliente-kotlin/` morria no meio do stream com
 * `SocketTimeoutException` porque o OkHttp aplica `readTimeout` entre
 * leituras (achado da integracao de 18/11/2026).
 */
class KtorSseTransport(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val client: HttpClient = HttpClient(),
) : SseTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun consume(
        request: SuggestionRequest,
        onEvent: suspend (event: String, payload: SsePayload) -> Unit,
    ) {
        client.preparePost("$baseUrl/api/v1/sugestoes/stream") {
            header("X-Client-Trace-Id", TraceId.current)
            if (!apiKey.isNullOrBlank()) header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SuggestionRequest.serializer(), request))
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val corpo = response.bodyAsText()
                val info = ErrorMapper.HttpInfo(
                    code = response.status.value,
                    retryAfter = response.headers["Retry-After"],
                )
                throw SseException(ErrorMapper.map(info, corpo), response.status.value)
            }

            val parser = SseParser()
            val channel = response.bodyAsChannel()

            suspend fun emitir(nome: String, dados: String) {
                val payload = runCatching {
                    json.decodeFromString<SsePayload>(dados)
                }.getOrNull() ?: return
                onEvent(nome, payload)
            }

            while (true) {
                val linha = channel.readUTF8Line() ?: break
                parser.feed(linha)?.let { emitir(it.name, it.data) }
            }
            parser.flush()?.let { emitir(it.name, it.data) }
        }
    }
}
