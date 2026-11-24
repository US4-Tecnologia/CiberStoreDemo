package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.ProductDetail
import com.ciberstore.demo.data.dto.ProductSearchResponse
import com.ciberstore.demo.data.dto.StatusResponse
import com.ciberstore.demo.data.dto.SuggestionRequest
import com.ciberstore.demo.data.dto.SuggestionResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/** Erro ja traduzido em [ApiAction] -- a UI decide a acao, nao o codigo HTTP. */
class ApiException(
    val action: ApiAction,
    val code: Int,
    val body: String? = null,
) : Exception("HTTP $code: $action")

/**
 * Cliente REST do contrato v1. Usa `bodyAsText()` + kotlinx.serialization
 * em vez do plugin de content-negotiation: menos dependencias e o mesmo
 * tratamento de erro em todas as plataformas.
 *
 * `HttpClient()` resolve o engine por plataforma: OkHttp (android), CIO
 * (desktop) e o engine wasm-js (web). Nenhum timeout de leitura e instalado
 * de proposito -- ver [com.ciberstore.demo.data.SseTransport].
 */
class ApiClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val client: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun io.ktor.client.request.HttpRequestBuilder.comum() {
        header("X-Client-Trace-Id", TraceId.current)
        if (!apiKey.isNullOrBlank()) header("X-API-Key", apiKey)
    }

    private suspend fun HttpResponse.textoOuErro(): String {
        val corpo = bodyAsText()
        if (!status.isSuccess()) {
            val info = ErrorMapper.HttpInfo(
                code = status.value,
                retryAfter = headers["Retry-After"],
                traceId = headers["X-Client-Trace-Id"],
            )
            throw ApiException(ErrorMapper.map(info, corpo), status.value, corpo)
        }
        return corpo
    }

    suspend fun buscar(
        q: String = "",
        limit: Int = 20,
        cursor: String? = null,
    ): ProductSearchResponse {
        val texto = client.get("$baseUrl/api/v1/produtos") {
            comum()
            parameter("q", q)
            parameter("limit", limit)
            if (cursor != null) parameter("cursor", cursor)
        }.textoOuErro()
        return json.decodeFromString(texto)
    }

    suspend fun detalhe(codProd: Int): ProductDetail {
        val texto = client.get("$baseUrl/api/v1/produtos/$codProd") {
            comum()
        }.textoOuErro()
        return json.decodeFromString(texto)
    }

    suspend fun sugestoes(request: SuggestionRequest): SuggestionResponse {
        val texto = client.post("$baseUrl/api/v1/sugestoes") {
            comum()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SuggestionRequest.serializer(), request))
        }.textoOuErro()
        return json.decodeFromString(texto)
    }

    suspend fun status(): StatusResponse {
        val texto = client.get("$baseUrl/api/v1/status") {
            comum()
        }.textoOuErro()
        return json.decodeFromString(texto)
    }

    fun close() = client.close()
}
