package com.ciberstore.demo.ui

import com.ciberstore.demo.data.ApiClient
import com.ciberstore.demo.data.Repository
import com.ciberstore.demo.data.SseTransport
import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionItem
import com.ciberstore.demo.data.dto.SuggestionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private class FakeSse(
    private val eventos: List<Pair<String, SsePayload>>,
) : SseTransport {
    override suspend fun consume(
        request: SuggestionRequest,
        onEvent: suspend (event: String, payload: SsePayload) -> Unit,
    ) {
        eventos.forEach { (nome, payload) -> onEvent(nome, payload) }
    }
}

private fun produtoJson(ean: String = "7895860040265"): String =
    """{"query":"TINTA","total":1,"has_more":false,"results":[
        {"cod_prod":124,"produto":"TINTA ACR 18L DUCRYL","categoria":"TINTA ACRILICA",
         "ean":"$ean","marca":"RENNER","ref":"7400.45"}]}""".trimIndent()

private fun sugestao(cod: Int = 149830, ean: String = "7895860100983") = SuggestionItem(
    codProd = cod,
    product = "PINCEL 2 VERNIZ STAIN PVA",
    reason = "essencial para retoques",
    ean = ean,
    marca = "RENNER",
    ref = "7400.99",
)

private fun viewModel(
    modoDemo: Boolean = false,
    sse: SseTransport = FakeSse(
        listOf(
            "pool" to SsePayload(recommendations = listOf(sugestao())),
            "done" to SsePayload(),
        )
    ),
): StoreViewModel {
    val engine = MockEngine { respond(produtoJson(), headers = JSON_HEADERS) }
    val client = HttpClient(engine)
    val api = ApiClient("http://127.0.0.1:8099", null, client)
    return StoreViewModel(Repository(api, sse), modoDemo)
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoreViewModelTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sugestao adicionada ao carrinho pelo vendedor`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.adicionarSugestao(sugestao())
        val carrinho = vm.state.value.carrinho
        assertEquals(1, carrinho.size)
        assertEquals(149830, carrinho[0].codProd)
        // a identidade do produto viaja junto (o app do cliente casa por EAN)
        assertEquals("7895860100983", carrinho[0].ean)
        assertEquals("RENNER", carrinho[0].marca)
    }

    @Test
    fun `sugestao repetida nao entra duas vezes no carrinho`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.adicionarSugestao(sugestao())
        vm.adicionarSugestao(sugestao())
        assertEquals(1, vm.state.value.carrinho.size)
    }

    @Test
    fun `modo demo abre com carrinho de exemplo e nas sugestoes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel(modoDemo = true)
        // a UI dispara o modo demo (LaunchedEffect) -- aqui, direto
        vm.carregarDemo()
        advanceUntilIdle()
        val estado = vm.state.value
        assertEquals(1, estado.carrinho.size)
        assertEquals(124, estado.carrinho[0].codProd)
        assertEquals("7895860040265", estado.carrinho[0].ean)
        assertEquals(Tela.SUGESTOES, estado.tela)
    }

    @Test
    fun `sem modo demo o carrinho comeca vazio`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.carrinho.isEmpty())
    }
}
