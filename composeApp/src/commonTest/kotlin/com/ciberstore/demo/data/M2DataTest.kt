package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionItem
import com.ciberstore.demo.data.dto.SuggestionRequest
import com.ciberstore.demo.data.sse.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private fun produto(cod: Int) = ProductItem(cod, "PRODUTO $cod")

private fun sugestao(cod: Int) = SuggestionItem(cod, "PRODUTO $cod")

// ------------------------------------------------------------------ SseParser

class SseParserTest {
    @Test
    fun `parseia as tres fases do stream`() {
        val corpo = """
            event: pool
            data: {"recommendations":[]}

            event: justificativa
            data: {"recommendations":[]}

            event: done
            data: {"quota_exhausted":false}
        """.trimIndent()

        val eventos = SseParser().parseAll(corpo)
        assertEquals(listOf("pool", "justificativa", "done"), eventos.map { it.name })
        assertTrue(eventos[2].data.contains("quota_exhausted"))
    }

    @Test
    fun `ignora comentarios e linhas desconhecidas`() {
        val parser = SseParser()
        assertNull(parser.feed(": keep-alive"))
        assertNull(parser.feed("xxx: qualquer coisa"))
        assertNull(parser.feed("event: pool"))
        assertNull(parser.feed("data: {\"ok\":true}"))
        assertEquals("pool", parser.feed("")?.name)
    }

    @Test
    fun `fecha bloco quando novo event comeca sem linha em branco`() {
        val parser = SseParser()
        assertNull(parser.feed("event: pool"))
        assertNull(parser.feed("data: {\"a\":1}"))
        assertEquals("pool", parser.feed("event: done")?.name)
        assertNull(parser.feed("data: {\"b\":2}"))
        assertEquals("done", parser.flush()?.name)
    }

    @Test
    fun `join de multiplas linhas data`() {
        val eventos = SseParser().parseAll("event: pool\ndata: {\"a\":\ndata: 1}\n")
        assertEquals("{\"a\":\n1}", eventos.single().data)
    }
}

// ---------------------------------------------------------------- ErrorMapper

class ErrorMapperTest {
    @Test
    fun `200 e Ok`() {
        assertTrue(ErrorMapper.map(200) is ApiAction.Ok)
    }

    @Test
    fun `erros nao transitorios nao devem ser repetidos`() {
        assertTrue(ErrorMapper.map(401) is ApiAction.AvisarOperador)
        assertTrue(ErrorMapper.map(403) is ApiAction.Bloqueado)
        assertTrue(ErrorMapper.map(404) is ApiAction.NaoEncontrado)
        assertTrue(ErrorMapper.map(422) is ApiAction.PayloadInvalido)
    }

    @Test
    fun `429 respeita Retry-After`() {
        assertEquals(ApiAction.RateLimited(12), ErrorMapper.map(429, retryAfter = "12"))
        assertEquals(ApiAction.RateLimited(60), ErrorMapper.map(429))
    }

    @Test
    fun `503 e demais erros usam cache local`() {
        assertTrue(ErrorMapper.map(503) is ApiAction.UsarCacheLocal)
        assertTrue(ErrorMapper.map(500) is ApiAction.UsarCacheLocal)
    }

    @Test
    fun `le o corpo de erro padronizado do contrato`() {
        val acao = ErrorMapper.map(
            403,
            body = """{"error":{"code":"blocked","message":"consulta bloqueada"}}""",
        )
        assertEquals("consulta bloqueada", (acao as ApiAction.Bloqueado).message)
    }
}

// ---------------------------------------------------------------------- Cache

class CacheTest {
    @Test
    fun `salva e recupera busca detalhe e sugestoes`() {
        val cache = InMemoryCache()
        assertTrue(cache.busca("tinta").isEmpty())

        cache.salvaBusca("tinta", listOf(produto(1)))
        assertEquals(listOf(produto(1)), cache.busca("tinta"))

        cache.salvaSugestoes("1:", listOf(sugestao(2)))
        assertEquals(listOf(sugestao(2)), cache.sugestoes("1:"))

        assertNull(cache.detalhe(9))
    }
}

// ------------------------------------------------------------------- TraceId

class TraceIdTest {
    @Test
    fun `ids sao nao vazios distintos e rotacao troca o corrente`() {
        val a = TraceId.novo()
        val b = TraceId.novo()
        assertTrue(a.isNotBlank())
        assertTrue(a != b)
        val anterior = TraceId.current
        val novo = TraceId.rotate()
        assertTrue(novo != anterior)
        assertEquals(novo, TraceId.current)
    }
}

// ------------------------------------------------------------------ Repository

private class FakeSseTransport(
    private val eventos: List<Pair<String, SsePayload>>,
) : SseTransport {
    override suspend fun consume(
        request: SuggestionRequest,
        onEvent: suspend (event: String, payload: SsePayload) -> Unit,
    ) {
        eventos.forEach { (nome, payload) -> onEvent(nome, payload) }
    }
}

private class FailingSseTransport(private val erro: Exception) : SseTransport {
    override suspend fun consume(
        request: SuggestionRequest,
        onEvent: suspend (event: String, payload: SsePayload) -> Unit,
    ) {
        throw erro
    }
}

private fun apiClient(engine: MockEngine) = ApiClient("http://127.0.0.1:8099", null, HttpClient(engine))

private val sseVazio = FakeSseTransport(emptyList())

class RepositoryTest {
    @Test
    fun `busca paginada devolve os resultados`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"query":"tinta","total":1,"results":[{"cod_prod":1,"produto":"TINTA"}]}""",
                headers = JSON_HEADERS,
            )
        }
        val repo = Repository(apiClient(engine), sseVazio)
        val pagina = repo.buscarPagina("tinta")
        assertEquals(1, pagina.results.size)
        assertFalse(pagina.hasMore)
    }

    @Test
    fun `falha de rede degrada para o cache`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val cache = InMemoryCache()
        cache.salvaBusca("tinta", listOf(produto(7)))
        val repo = Repository(apiClient(engine), sseVazio, cache)
        val pagina = repo.buscarPagina("tinta")
        assertEquals(listOf(produto(7)), pagina.results)
        assertFalse(pagina.hasMore)
    }

    @Test
    fun `buscarTudo percorre o cursor ate hasMore false`() = runTest {
        var chamadas = 0
        val engine = MockEngine { request ->
            chamadas++
            val temCursor = request.url.parameters.contains("cursor")
            if (temCursor) {
                respond(
                    content = """{"has_more":false,"results":[{"cod_prod":2,"produto":"B"}]}""",
                    headers = JSON_HEADERS,
                )
            } else {
                respond(
                    content = """{"has_more":true,"next_cursor":"abc","results":[{"cod_prod":1,"produto":"A"}]}""",
                    headers = JSON_HEADERS,
                )
            }
        }
        val repo = Repository(apiClient(engine), sseVazio)
        val itens = repo.buscarTudo("tinta")
        assertEquals(listOf(1, 2), itens.map { it.codProd })
        assertEquals(listOf("A", "B"), itens.map { it.produto })
        assertEquals(2, chamadas)
    }

    @Test
    fun `stream propaga pool justificativa e done na ordem`() = runTest {
        val eventos = listOf(
            "pool" to SsePayload(recommendations = listOf(sugestao(1))),
            "justificativa" to SsePayload(recommendations = listOf(sugestao(2))),
            "done" to SsePayload(quotaExhausted = true),
        )
        val repo = Repository(apiClient(MockEngine { respond("{}", headers = JSON_HEADERS) }), FakeSseTransport(eventos))
        val ordem = mutableListOf<String>()
        var quota = false
        repo.streamSugestoes(
            SuggestionRequest(codProd = 1, explicar = true),
            onPool = { ordem += "pool:${it.first().codProd}" },
            onJustificativa = { ordem += "justificativa:${it.first().codProd}" },
            onDone = { quota = it.quotaExhausted },
        )
        assertEquals(listOf("pool:1", "justificativa:2"), ordem)
        assertTrue(quota)
    }

    @Test
    fun `falha no stream degrada para o cache e reporta a acao`() = runTest {
        val cache = InMemoryCache()
        cache.salvaSugestoes("1:", listOf(sugestao(42)))
        val repo = Repository(
            apiClient(MockEngine { respond("{}", headers = JSON_HEADERS) }),
            FailingSseTransport(SseException(ApiAction.UsarCacheLocal("offline"), 503)),
            cache,
        )
        var poolRecebido: List<SuggestionItem> = emptyList()
        var acao: ApiAction? = null
        repo.streamSugestoes(
            SuggestionRequest(codProd = 1),
            onPool = { poolRecebido = it },
            onErro = { acao = it },
        )
        assertEquals(listOf(sugestao(42)), poolRecebido)
        assertNotNull(acao)
        assertTrue(acao is ApiAction.UsarCacheLocal)
    }

    @Test
    fun `chave do cache ignora a ordem do carrinho`() = runTest {
        val repo = Repository(apiClient(MockEngine { respond("{}", headers = JSON_HEADERS) }), sseVazio)
        assertEquals(
            repo.chave(SuggestionRequest(1, cartProducts = listOf(3, 2))),
            repo.chave(SuggestionRequest(1, cartProducts = listOf(2, 3))),
        )
    }
}
