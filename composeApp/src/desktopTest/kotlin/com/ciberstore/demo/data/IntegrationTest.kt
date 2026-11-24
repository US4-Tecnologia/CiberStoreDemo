package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.SuggestionRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integracao **opt-in** com a API real (M3). Desligada por default para a
 * suite continuar 100% offline; ligue com:
 *
 * ```
 * uvicorn app.main:app --port 8099        # no repo SmartAgenteVendasIA
 * gradlew :composeApp:desktopTest -Dsmartorder.integration=true
 * ```
 *
 * Prova que `ApiClient` (Ktor/CIO), `KtorSseTransport` (streaming de 2 fases)
 * e `Repository` funcionam contra o contrato v1 de verdade, nao so com
 * `MockEngine`.
 */
class IntegrationTest {
    private val ligado: Boolean
        get() = System.getProperty("smartorder.integration") == "true"

    private val baseUrl: String
        get() = System.getProperty("smartorder.baseUrl") ?: "http://127.0.0.1:8099"

    @Test
    fun `busca real de produtos`() = runTest {
        if (!ligado) return@runTest
        val client = HttpClient()
        val api = ApiClient(baseUrl, null, client)
        val pagina = api.buscar("TINTA", limit = 5)
        assertTrue(pagina.results.isNotEmpty(), "busca real nao trouxe produtos")
        assertTrue(pagina.results.first().codProd > 0)
        client.close()
    }

    @Test
    fun `status real`() = runTest {
        if (!ligado) return@runTest
        val client = HttpClient()
        val api = ApiClient(baseUrl, null, client)
        val status = api.status()
        assertNotNull(status.dataset)
        client.close()
    }

    @Test
    fun `SSE real entrega pool antes do done`() = runTest {
        if (!ligado) return@runTest
        val client = HttpClient()
        val sse = KtorSseTransport(baseUrl, null, client)
        val api = ApiClient(baseUrl, null, client)
        val repository = Repository(api, sse)

        val ordem = mutableListOf<String>()
        repository.streamSugestoes(
            SuggestionRequest(codProd = 124, explicar = false),
            onPool = { ordem += "pool:${it.size}" },
            onJustificativa = { ordem += "justificativa" },
            onDone = { ordem += "done" },
            onErro = { ordem += "erro:$it" },
        )
        assertTrue(ordem.firstOrNull()?.startsWith("pool:") == true, "pool nao chegou primeiro: $ordem")
        assertTrue(ordem.last() == "done", "stream nao fechou com done: $ordem")
        client.close()
    }
}
