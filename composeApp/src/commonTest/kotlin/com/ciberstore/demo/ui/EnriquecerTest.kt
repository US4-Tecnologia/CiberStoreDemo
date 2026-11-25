package com.ciberstore.demo.ui

import com.ciberstore.demo.data.dto.SuggestionItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regra de ouro da UI (M3): a tela renderiza no `pool` e **se enriquece** na
 * `justificativa` -- nao substitui.
 *
 * Achado de 25/11/2026 no browser: o LLM devolveu 1 candidato valido e a tela
 * passou a mostrar 1 recomendacao, descartando as 4 do pool deterministico.
 */
class EnriquecerTest {
    private fun item(cod: Int, motivo: String = "motivo padrao") =
        SuggestionItem(cod, "PRODUTO $cod", reason = motivo)

    @Test
    fun `justificativa de um item nao descarta o pool`() {
        val pool = listOf(item(1), item(2), item(3), item(4), item(5))
        val resultado = enriquecer(pool, listOf(item(2, "explicado pelo llm")))
        assertEquals(5, resultado.size)
        assertEquals(listOf(1, 2, 3, 4, 5), resultado.map { it.codProd })
    }

    @Test
    fun `item explicado ganha o motivo do llm`() {
        val pool = listOf(item(1), item(2))
        val resultado = enriquecer(pool, listOf(item(2, "explicado pelo llm")))
        assertEquals("motivo padrao", resultado[0].reason)
        assertEquals("explicado pelo llm", resultado[1].reason)
    }

    @Test
    fun `justificativa vazia mantem o pool`() {
        val pool = listOf(item(1), item(2))
        assertEquals(pool, enriquecer(pool, emptyList()))
    }

    @Test
    fun `sem pool usa a lista do llm`() {
        val justificativa = listOf(item(9, "do llm"))
        assertEquals(justificativa, enriquecer(emptyList(), justificativa))
    }
}
