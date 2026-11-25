package com.ciberstore.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ciberstore.demo.data.ApiAction
import com.ciberstore.demo.data.Repository
import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.SsePayload
import com.ciberstore.demo.data.dto.SuggestionItem
import com.ciberstore.demo.data.dto.SuggestionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Tela { BUSCA, CARRINHO, SUGESTOES }

data class StoreUiState(
    val tela: Tela = Tela.BUSCA,
    val query: String = "",
    val produtos: List<ProductItem> = emptyList(),
    val carrinho: List<ProductItem> = emptyList(),
    val sugestoes: List<SuggestionItem> = emptyList(),
    val carregandoBusca: Boolean = false,
    val justificativaPendente: Boolean = false,
    val fallbackUsado: Boolean = false,
    val quotaEsgotada: Boolean = false,
    val mensagem: String? = null,
)

/**
 * Estado do demo de carrinho. ViewModel do CMP (decisao do solicitante em
 * 24/11), com `viewModelScope` para sobreviver a recomposicoes.
 *
 * Regra de ouro do contrato v1: a tela de sugestoes **renderiza no `pool`**
 * (deterministico, ~ms) e so enriquece quando a `justificativa` do LLM
 * chega. Nunca espera o LLM para mostrar a lista.
 */
class StoreViewModel(
    private val repository: Repository,
    val modoDemo: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    fun onQueryChange(valor: String) = _state.update { it.copy(query = valor) }

    fun irPara(tela: Tela) {
        _state.update { it.copy(tela = tela, mensagem = null) }
        if (tela == Tela.SUGESTOES) carregarSugestoes()
    }

    fun buscar() {
        val q = _state.value.query
        viewModelScope.launch {
            _state.update { it.copy(carregandoBusca = true, mensagem = null) }
            val itens = repository.buscarTudo(q = q, limit = 20)
            _state.update {
                it.copy(
                    carregandoBusca = false,
                    produtos = itens,
                    mensagem = if (itens.isEmpty()) "Nenhum produto encontrado" else null,
                )
            }
        }
    }

    fun adicionar(produto: ProductItem) {
        _state.update { atual ->
            if (atual.carrinho.any { it.codProd == produto.codProd } || atual.carrinho.size >= 10) {
                atual.copy(mensagem = if (atual.carrinho.size >= 10) "Carrinho cheio (max. 10)" else "Item ja no carrinho")
            } else {
                atual.copy(carrinho = atual.carrinho + produto, mensagem = null)
            }
        }
    }

    fun remover(codProd: Int) = _state.update {
        it.copy(carrinho = it.carrinho.filterNot { p -> p.codProd == codProd })
    }

    /**
     * Acao do **vendedor**: adiciona a sugestao ao carrinho.
     *
     * O agente nunca mexe no carrinho -- quem pergunta ao cliente e decide
     * comprar e o vendedor (decisao D1 do plano de integracao padrao com os
     * clientes). O app so executa o que o vendedor toca.
     */
    fun adicionarSugestao(sugestao: SuggestionItem) {
        adicionar(
            ProductItem(
                codProd = sugestao.codProd,
                produto = sugestao.product.ifBlank { sugestao.produto },
                ean = sugestao.ean,
                marca = sugestao.marca,
                ref = sugestao.ref,
            )
        )
    }

    /**
     * Produto de exemplo do modo demo (o mesmo dos testes e da evidencia de
     * 25/11/2026). Fixo de proposito: o modo demo **nao depende de rede** para
     * montar o carrinho -- so a justificativa (SSE) vai ao servidor.
     */
    private val produtoDemo = ProductItem(
        codProd = 124,
        produto = "TINTA ACR 18L DUCRYL",
        setor = "TINTAS",
        categoria = "TINTA ACRILICA",
        tipo = "TINTAS P/ALVENARIA",
        ean = "7895860040265",
        marca = "RENNER",
        ref = "7400.45",
    )

    /**
     * Modo demo: ja abre com um carrinho de exemplo e as sugestoes pedidas.
     *
     * Chamado pela UI (`LaunchedEffect`), nao no `init` do ViewModel: efeito de
     * tela pertence a tela, e o `init` com `viewModelScope` derrubava o app
     * no Wasm (`RuntimeError: illegal cast` no boot, achado de 25/11/2026).
     */
    fun carregarDemo() {
        viewModelScope.launch {
            _state.update {
                it.copy(produtos = listOf(produtoDemo), carrinho = listOf(produtoDemo))
            }
            irPara(Tela.SUGESTOES)
        }
    }

    private fun carregarSugestoes() {
        val carrinho = _state.value.carrinho
        if (carrinho.isEmpty()) {
            _state.update { it.copy(mensagem = "Adicione um produto ao carrinho primeiro") }
            return
        }
        // Produto-base = ultimo item colocado; os demais viram contexto.
        val base = carrinho.last()
        val request = SuggestionRequest(
            codProd = base.codProd,
            cartProducts = carrinho.dropLast(1).map { it.codProd },
            explicar = true,
        )
        viewModelScope.launch {
            _state.update {
                it.copy(
                    sugestoes = emptyList(),
                    justificativaPendente = true,
                    fallbackUsado = false,
                    quotaEsgotada = false,
                    mensagem = null,
                )
            }
            repository.streamSugestoes(
                request = request,
                onPool = { pool -> _state.update { it.copy(sugestoes = pool) } },
                onJustificativa = { explicadas ->
                    // Enriquece o pool: o LLM pode explicar 1 dos N candidatos.
                    // Substituir a lista inteira perderia as recomendacoes que o
                    // LLM nao mencionou (achado de 25/11/2026 no browser).
                    _state.update { atual ->
                        atual.copy(sugestoes = enriquecer(atual.sugestoes, explicadas))
                    }
                },
                onDone = { done -> aplicarDone(done) },
                onErro = { acao -> aplicarErro(acao) },
            )
        }
    }

    private fun aplicarDone(done: SsePayload) {
        _state.update {
            it.copy(
                justificativaPendente = false,
                fallbackUsado = done.fallbackUsed,
                quotaEsgotada = done.quotaExhausted,
                mensagem = done.errors.firstOrNull(),
            )
        }
    }

    private fun aplicarErro(acao: ApiAction) {
        val texto = when (acao) {
            is ApiAction.Bloqueado -> "Consulta bloqueada: ${acao.message}"
            is ApiAction.AvisarOperador -> acao.message
            is ApiAction.NaoEncontrado -> "Produto nao encontrado no catalogo"
            is ApiAction.PayloadInvalido -> "Payload invalido: ${acao.message}"
            is ApiAction.RateLimited -> "Limite de requisicoes. Tente em ${acao.retryAfterSeconds}s"
            is ApiAction.UsarCacheLocal -> "API indisponivel — exibindo ultimo resultado em cache"
            is ApiAction.Ok -> null
        }
        _state.update {
            it.copy(justificativaPendente = false, fallbackUsado = true, mensagem = texto)
        }
    }
}

/**
 * Regra de ouro da UI (M3): a tela **renderiza no `pool`** e **se enriquece**
 * na `justificativa`. Enriquecer nao e substituir -- se o LLM explica 1 dos 5
 * candidatos, os outros 4 continuam na tela.
 *
 * Em 25/11/2026 a tela mostrava 1 recomendacao (o LLM tinha devolvido 1
 * candidato valido) porque o `pool` com 5 itens era descartado. Este teste
 * trava a regra.
 */
internal fun enriquecer(
    pool: List<SuggestionItem>,
    justificativa: List<SuggestionItem>,
): List<SuggestionItem> {
    if (justificativa.isEmpty()) return pool
    if (pool.isEmpty()) return justificativa
    val porCod = justificativa.associateBy { it.codProd }
    return pool.map { item -> porCod[item.codProd] ?: item }
}
