package com.ciberstore.demo.data

import com.ciberstore.demo.data.dto.ProductDetail
import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.SuggestionItem

/**
 * Cache local do app. A API **nunca** e pre-requisito para a tela funcionar:
 * toda leitura passa por aqui e, quando a API falha, o app continua servindo
 * a ultima resposta boa.
 *
 * A implementacao de referencia e em memoria; troque por Room/DataStore no
 * app real -- a interface e o ponto de troca.
 */
interface Cache {
    fun busca(q: String): List<ProductItem>
    fun salvaBusca(q: String, itens: List<ProductItem>)

    fun detalhe(codProd: Int): ProductDetail?
    fun salvaDetalhe(detalhe: ProductDetail)

    fun sugestoes(chave: String): List<SuggestionItem>
    fun salvaSugestoes(chave: String, itens: List<SuggestionItem>)
}

class InMemoryCache : Cache {
    private val buscas = mutableMapOf<String, List<ProductItem>>()
    private val detalhes = mutableMapOf<Int, ProductDetail>()
    private val sugestoes = mutableMapOf<String, List<SuggestionItem>>()

    override fun busca(q: String): List<ProductItem> = buscas[q].orEmpty()

    override fun salvaBusca(q: String, itens: List<ProductItem>) {
        buscas[q] = itens
    }

    override fun detalhe(codProd: Int): ProductDetail? = detalhes[codProd]

    override fun salvaDetalhe(detalhe: ProductDetail) {
        detalhes[detalhe.codProd] = detalhe
    }

    override fun sugestoes(chave: String): List<SuggestionItem> = sugestoes[chave].orEmpty()

    override fun salvaSugestoes(chave: String, itens: List<SuggestionItem>) {
        sugestoes[chave] = itens
    }
}
