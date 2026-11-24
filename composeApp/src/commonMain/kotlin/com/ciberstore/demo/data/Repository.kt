package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.ProductDetail
import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.ProductSearchResponse
import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.StatusResponse
import com.ciberstore.demo.data.dto.SuggestionItem
import com.ciberstore.demo.data.dto.SuggestionRequest
import com.ciberstore.demo.data.dto.SuggestionResponse

/**
 * Camada de dados: paginacao por cursor, cache local e degradacao.
 *
 * Regra que guia tudo: **a API nunca e pre-requisito para a tela funcionar**.
 * Toda leitura passa pelo [Cache] e, quando a API falha, o app continua
 * servindo a ultima resposta boa. A UI renderiza no `pool` do SSE sem
 * esperar a justificativa do LLM.
 *
 * Portado do `cliente-kotlin/` de referencia para `commonMain` (M2).
 */
class Repository(
    private val api: ApiClient,
    private val sse: SseTransport,
    private val cache: Cache = InMemoryCache(),
) {
    // ------------------------------------------------------------- busca

    /** Uma pagina por vez -- para lista com scroll infinito. */
    suspend fun buscarPagina(
        q: String = "",
        limit: Int = 20,
        cursor: String? = null,
    ): ProductSearchResponse = try {
        api.buscar(q, limit, cursor)
    } catch (e: Exception) {
        val emCache = cache.busca(q)
        ProductSearchResponse(
            query = q,
            limit = limit,
            total = emCache.size,
            hasMore = false,
            results = emCache,
        )
    }

    /**
     * Busca paginada consumindo o cursor ate o fim.
     * @param maxPaginas teto de seguranca contra cursor repetido.
     */
    suspend fun buscarTudo(
        q: String = "",
        limit: Int = 20,
        maxPaginas: Int = 50,
    ): List<ProductItem> {
        val out = mutableListOf<ProductItem>()
        var cursor: String? = null
        var paginas = 0
        var pagina: ProductSearchResponse
        do {
            pagina = try {
                api.buscar(q, limit, cursor)
            } catch (e: Exception) {
                return cache.busca(q).ifEmpty { out }
            }
            out += pagina.results
            cursor = pagina.nextCursor
            paginas++
        } while (pagina.hasMore && cursor != null && paginas < maxPaginas)
        cache.salvaBusca(q, out)
        return out
    }

    // ------------------------------------------------------------ detalhe

    suspend fun detalhe(codProd: Int): ProductDetail? = try {
        api.detalhe(codProd).also { cache.salvaDetalhe(it) }
    } catch (e: Exception) {
        cache.detalhe(codProd)
    }

    // --------------------------------------------------------- sugestoes

    /** Recomendacoes deterministicas (pool), sem LLM. Resposta imediata. */
    suspend fun sugestoes(request: SuggestionRequest): SuggestionResponse = try {
        val resp = api.sugestoes(request)
        cache.salvaSugestoes(chave(request), resp.recommendations)
        resp
    } catch (e: Exception) {
        val emCache = cache.sugestoes(chave(request))
        SuggestionResponse(
            status = "success",
            codProd = request.codProd,
            recommendations = emCache,
        )
    }

    /**
     * SSE de duas fases. [onPool] e a fonte da verdade da tela e chega em
     * ~ms; [onJustificativa] enriquece depois (LLM); [onDone] fecha e traz
     * `fallback_used`, `quota_exhausted` e `errors`.
     *
     * Em caso de falha, degrada para o cache e reporta a [ApiAction] para a
     * UI decidir (usar cache / avisar / nao repetir).
     */
    suspend fun streamSugestoes(
        request: SuggestionRequest,
        onPool: suspend (List<SuggestionItem>) -> Unit,
        onJustificativa: suspend (List<SuggestionItem>) -> Unit = {},
        onDone: suspend (SsePayload) -> Unit = {},
        onErro: suspend (ApiAction) -> Unit = {},
    ) {
        val chave = chave(request)
        try {
            sse.consume(request) { evento, payload ->
                when (evento) {
                    "pool" -> {
                        cache.salvaSugestoes(chave, payload.recommendations)
                        onPool(payload.recommendations)
                    }

                    "justificativa" -> onJustificativa(payload.recommendations)
                    "done" -> onDone(payload)
                }
            }
        } catch (e: SseException) {
            degradar(chave, onPool, onErro, e.action)
        } catch (e: Exception) {
            degradar(
                chave, onPool, onErro,
                ApiAction.UsarCacheLocal(e.message ?: "falha no stream"),
            )
        }
    }

    private suspend fun degradar(
        chave: String,
        onPool: suspend (List<SuggestionItem>) -> Unit,
        onErro: suspend (ApiAction) -> Unit,
        action: ApiAction,
    ) {
        val emCache = cache.sugestoes(chave)
        if (emCache.isNotEmpty()) onPool(emCache)
        onErro(action)
    }

    // ------------------------------------------------------------- status

    suspend fun status(): StatusResponse? = try {
        api.status()
    } catch (e: Exception) {
        null
    }

    fun chave(request: SuggestionRequest): String =
        "${request.codProd}:${request.cartProducts.sorted().joinToString(",")}"
}
