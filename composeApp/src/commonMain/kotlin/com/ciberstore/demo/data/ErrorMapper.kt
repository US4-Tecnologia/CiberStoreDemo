package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.ApiErrorBody
import kotlinx.serialization.json.Json

/**
 * Traduz o HTTP do servidor em uma **acao** do app.
 *
 * Regra central do contrato: nem todo erro pode ser repetido. Um `403 blocked`
 * repetido em loop vira abuso de cota; um `401` repetido nunca vai passar.
 * Portado do `cliente-kotlin/` de referencia, sem Retrofit/OkHttp (M2).
 */
sealed interface ApiAction {
    data class Ok(val traceId: String?) : ApiAction

    /** Falha nao transitoria: avisar o operador, **nao repetir**. */
    data class AvisarOperador(val code: String, val message: String) : ApiAction

    /** Bloqueado pelo guard de seguranca: **nao repetir**. */
    data class Bloqueado(val message: String) : ApiAction

    /** Recurso inexistente: tratar como "produto saiu do catalogo". */
    data class NaoEncontrado(val message: String) : ApiAction

    /** Bug do app: payload fora do schema. **Nao repetir.** */
    data class PayloadInvalido(val message: String) : ApiAction

    /** Limite de requisicoes: repetir depois de [retryAfterSeconds]. */
    data class RateLimited(val retryAfterSeconds: Long) : ApiAction

    /** API fora do ar ou degradada: usar o cache local. */
    data class UsarCacheLocal(val message: String) : ApiAction
}

object ErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    /** Cabecalhos minimos necessarios; desacoplado de qualquer engine HTTP. */
    data class HttpInfo(
        val code: Int,
        val retryAfter: String? = null,
        val traceId: String? = null,
    )

    fun map(info: HttpInfo, body: String? = null): ApiAction {
        if (info.code in 200..299) return ApiAction.Ok(info.traceId)
        val parsed = parseError(body)
        return when (info.code) {
            401 -> ApiAction.AvisarOperador(
                parsed?.code ?: "unauthorized",
                parsed?.message ?: "chave de API invalida ou ausente",
            )

            403 -> ApiAction.Bloqueado(
                parsed?.message ?: "consulta bloqueada pelo guard de seguranca",
            )

            404 -> ApiAction.NaoEncontrado(
                parsed?.message ?: "recurso nao encontrado",
            )

            422 -> ApiAction.PayloadInvalido(
                parsed?.message ?: "payload fora do schema esperado",
            )

            429 -> ApiAction.RateLimited(info.retryAfter?.toLongOrNull() ?: 60L)

            503 -> ApiAction.UsarCacheLocal(
                parsed?.message ?: "servico indisponivel",
            )

            else -> ApiAction.UsarCacheLocal(parsed?.message ?: "HTTP ${info.code}")
        }
    }

    fun map(code: Int, retryAfter: String? = null, body: String? = null): ApiAction =
        map(HttpInfo(code, retryAfter), body)

    private fun parseError(body: String?) =
        if (body.isNullOrBlank()) {
            null
        } else {
            runCatching { json.decodeFromString<ApiErrorBody>(body).error }.getOrNull()
        }
}
