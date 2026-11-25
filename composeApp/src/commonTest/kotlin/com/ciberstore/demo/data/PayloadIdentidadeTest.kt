package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionItem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decodificacao de payload **real** da API v1.1 (com a identidade de produto).
 *
 * Achado de 25/11/2026: o app web (Wasm) quebrava ao processar a resposta do
 * SSE com `RuntimeError: illegal cast`. O desktop (JVM) nao. Este teste roda
 * nos dois targets (commonTest) para travar o comportamento.
 */
class PayloadIdentidadeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val itemReal = """
        {"cod_prod":149830,"product":"PINCEL 2\" 771 VERNIZ STAIN PVA",
         "produto":"PINCEL 2\" 771 VERNIZ STAIN PVA","cooccurrence":6,"prior":0.9,
         "score":1.21,"source":"both","complements_cod":124,"confidence":0.03,
         "confidence_band":"baixa","ean":"7899389006559","marca":"CONDOR",
         "ref":"971504","reason":"Comprado junto com o item 124 para a obra"}
    """.trimIndent()

    @Test
    fun `decodifica item de sugestao com identidade`() {
        val item = json.decodeFromString<SuggestionItem>(itemReal)
        assertEquals(149830, item.codProd)
        assertEquals("7899389006559", item.ean)
        assertEquals("CONDOR", item.marca)
        assertEquals("971504", item.ref)
        assertEquals(6, item.cooccurrence)
        assertEquals("baixa", item.confidenceBand)
    }

    @Test
    fun `decodifica payload do SSE com identidade`() {
        val payload = json.decodeFromString<SsePayload>(
            """{"request_id":"abc","status":"success","recommendations":[$itemReal]}""",
        )
        assertEquals("abc", payload.requestId)
        assertEquals(1, payload.recommendations.size)
        assertEquals("7899389006559", payload.recommendations[0].ean)
    }
}
