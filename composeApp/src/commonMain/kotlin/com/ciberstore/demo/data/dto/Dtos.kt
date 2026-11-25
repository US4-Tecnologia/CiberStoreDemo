package com.ciberstore.demo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTOs do contrato de API v1.
 *
 * Fonte da verdade: `docs/openapi.yaml` do repo `SmartAgenteVendasIA`.
 * Guia: `docs/integracao-android.md`. Portado do `cliente-kotlin/` de
 * referencia (Android-only) para `commonMain` em 24/11/2026 (M2).
 *
 * Todos os campos tem default: o servidor pode omitir campos novos sem
 * quebrar o app, e o `Json { ignoreUnknownKeys = true }` cobre o inverso.
 */

@Serializable
data class ProductItem(
    @SerialName("cod_prod") val codProd: Int,
    @SerialName("produto") val produto: String,
    @SerialName("setor") val setor: String = "",
    @SerialName("categoria") val categoria: String = "",
    @SerialName("tipo") val tipo: String = "",
    @SerialName("ean") val ean: String = "",
    @SerialName("marca") val marca: String = "",
    @SerialName("ref") val ref: String = "",
    @SerialName("relevance") val relevance: Double? = null,
)

@Serializable
data class ProductSearchResponse(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("query") val query: String = "",
    @SerialName("limit") val limit: Int = 20,
    @SerialName("total") val total: Int = 0,
    /** Cursor opaco: repita na proxima chamada, nunca decodifique. */
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("results") val results: List<ProductItem> = emptyList(),
)

@Serializable
data class ProductPrice(
    @SerialName("preco_medio") val precoMedio: Double? = null,
    @SerialName("preco_atual") val precoAtual: Double? = null,
    @SerialName("preco_min") val precoMin: Double? = null,
    @SerialName("preco_max") val precoMax: Double? = null,
    @SerialName("preco_amostra") val precoAmostra: Int = 0,
    @SerialName("estoque") val estoque: String? = null,
    @SerialName("estoque_disponivel") val estoqueDisponivel: Boolean = false,
)

@Serializable
data class ProductDetail(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("cod_prod") val codProd: Int,
    @SerialName("produto") val produto: String,
    @SerialName("setor") val setor: String = "",
    @SerialName("categoria") val categoria: String = "",
    @SerialName("tipo") val tipo: String = "",
    @SerialName("preco") val preco: ProductPrice = ProductPrice(),
)

@Serializable
data class SuggestionRequest(
    @SerialName("cod_prod") val codProd: Int,
    @SerialName("query") val query: String = "",
    @SerialName("cart_products") val cartProducts: List<Int> = emptyList(),
    /** Segunda fase do SSE: pede a justificativa por IA (consome cota do LLM). */
    @SerialName("explicar") val explicar: Boolean = false,
)

@Serializable
data class SuggestionItem(
    @SerialName("cod_prod") val codProd: Int,
    @SerialName("product") val product: String,
    @SerialName("produto") val produto: String = "",
    @SerialName("reason") val reason: String = "",
    @SerialName("confidence") val confidence: Double = 0.0,
    @SerialName("confidence_band") val confidenceBand: String? = null,
    @SerialName("cooccurrence") val cooccurrence: Int = 0,
    @SerialName("prior") val prior: Double? = null,
    @SerialName("score") val score: Double = 0.0,
    @SerialName("source") val source: String? = null,
    @SerialName("complements_cod") val complementsCod: Int? = null,
    @SerialName("ean") val ean: String = "",
    @SerialName("marca") val marca: String = "",
    @SerialName("ref") val ref: String = "",
)

@Serializable
data class SuggestionResponse(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("status") val status: String = "success",
    @SerialName("cod_prod") val codProd: Int = 0,
    @SerialName("product") val product: String = "",
    @SerialName("categoria") val categoria: String = "",
    @SerialName("cart_products") val cartProducts: List<Int> = emptyList(),
    @SerialName("recommendations") val recommendations: List<SuggestionItem> = emptyList(),
    @SerialName("deterministic_ms") val deterministicMs: Double = 0.0,
    @SerialName("fallback_used") val fallbackUsed: Boolean = false,
    @SerialName("llm_used") val llmUsed: Boolean = false,
)

/**
 * Payload dos eventos SSE. O mesmo envelope serve a `pool`, `justificativa`
 * e `done`, por isso quase tudo e opcional.
 */
@Serializable
data class SsePayload(
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("cod_prod") val codProd: Int? = null,
    @SerialName("product") val product: String? = null,
    @SerialName("categoria") val categoria: String? = null,
    @SerialName("recommendations") val recommendations: List<SuggestionItem> = emptyList(),
    @SerialName("deterministic_ms") val deterministicMs: Double? = null,
    @SerialName("quota_exhausted") val quotaExhausted: Boolean = false,
    @SerialName("llm_used") val llmUsed: Boolean = false,
    @SerialName("cache_hit") val cacheHit: Boolean = false,
    @SerialName("fallback_used") val fallbackUsed: Boolean = false,
    @SerialName("llm_quota_remaining") val llmQuotaRemaining: Int? = null,
    @SerialName("errors") val errors: List<String> = emptyList(),
    @SerialName("error") val error: String? = null,
)

@Serializable
data class StatusResponse(
    @SerialName("schema_version") val schemaVersion: String = "1.0",
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("status") val status: String = "ok",
    @SerialName("service") val service: String = "smartorder-ai",
    @SerialName("version") val version: String = "1.0.0",
    @SerialName("dataset") val dataset: JsonObject? = null,
    @SerialName("llm") val llm: JsonObject? = null,
    @SerialName("disk") val disk: JsonObject? = null,
    @SerialName("auth_required") val authRequired: Boolean = false,
)

@Serializable
data class ApiErrorDetail(
    @SerialName("code") val code: String = "unknown",
    @SerialName("message") val message: String = "",
    @SerialName("details") val details: JsonObject? = null,
)

@Serializable
data class ApiErrorBody(
    @SerialName("error") val error: ApiErrorDetail,
)
