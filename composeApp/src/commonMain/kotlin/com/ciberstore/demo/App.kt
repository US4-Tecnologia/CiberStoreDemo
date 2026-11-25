package com.ciberstore.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ciberstore.demo.data.dto.ProductItem
import com.ciberstore.demo.data.dto.SuggestionItem
import com.ciberstore.demo.ui.StoreUiState
import com.ciberstore.demo.ui.StoreViewModel
import com.ciberstore.demo.ui.Tela

/**
 * Demo de carrinho (M3): busca -> carrinho -> sugestoes via SSE.
 * Compose Multiplatform nos 3 targets (Android / Desktop / Web).
 */
@Composable
fun App(viewModel: StoreViewModel) {
    val state by viewModel.state.collectAsState()
    // Modo demo: o carrinho de exemplo e as sugestoes sao disparados aqui
    // (LaunchedEffect), e nao no init do ViewModel -- efeito de tela pertence
    // a tela, e o init com viewModelScope derrubava o app no Wasm
    // (RuntimeError: illegal cast no boot, achado de 25/11/2026).
    LaunchedEffect(Unit) {
        if (viewModel.modoDemo) viewModel.carregarDemo()
    }
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Cabecalho(state, viewModel)
            Spacer(Modifier.height(12.dp))
            when (state.tela) {
                Tela.BUSCA -> BuscaScreen(state, viewModel)
                Tela.CARRINHO -> CarrinhoScreen(state, viewModel)
                Tela.SUGESTOES -> SugestoesScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun Cabecalho(state: StoreUiState, viewModel: StoreViewModel) {
    Text("CiberStore Demo", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Busca → Carrinho → Sugestões (contrato v1)",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { viewModel.irPara(Tela.BUSCA) }) { Text("Busca") }
        Button(onClick = { viewModel.irPara(Tela.CARRINHO) }) {
            Text("Carrinho (${state.carrinho.size})")
        }
        Button(
            onClick = { viewModel.irPara(Tela.SUGESTOES) },
            enabled = state.carrinho.isNotEmpty(),
        ) { Text("Sugestões") }
    }
    state.mensagem?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BuscaScreen(state: StoreUiState, viewModel: StoreViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Buscar produto (ex.: TINTA)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = viewModel::buscar, enabled = !state.carregandoBusca) { Text("Buscar") }
    }
    if (state.carregandoBusca) {
        CircularProgressIndicator(Modifier.padding(12.dp))
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.produtos, key = { it.codProd }) { produto ->
            LinhaProduto(produto, "Adicionar") { viewModel.adicionar(produto) }
        }
    }
}

@Composable
private fun CarrinhoScreen(state: StoreUiState, viewModel: StoreViewModel) {
    if (state.carrinho.isEmpty()) {
        Text("Carrinho vazio. Volte para a busca e adicione produtos.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.carrinho, key = { it.codProd }) { produto ->
                LinhaProduto(produto, "Remover") { viewModel.remover(produto.codProd) }
            }
        }
        Button(
            onClick = { viewModel.irPara(Tela.SUGESTOES) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Ver sugestões para o carrinho") }
    }
}

@Composable
private fun SugestoesScreen(state: StoreUiState, viewModel: StoreViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Base: ${state.carrinho.lastOrNull()?.produto ?: "-"}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (state.justificativaPendente) {
            CircularProgressIndicator(Modifier.height(20.dp))
            Text(" aguardando IA...", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (state.fallbackUsado) {
        Text("fallback usado (sem justificativa do LLM)", color = MaterialTheme.colorScheme.error)
    }
    if (state.quotaEsgotada) {
        Text("cota diária do LLM esgotada", color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.sugestoes, key = { it.codProd }) { item ->
            LinhaSugestao(item) { viewModel.adicionarSugestao(item) }
        }
    }
}

@Composable
private fun LinhaProduto(produto: ProductItem, acao: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(produto.produto, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "cod ${produto.codProd} · ${produto.categoria}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (produto.ean.isNotBlank()) {
                    Text(
                        "EAN ${produto.ean}" +
                            if (produto.marca.isNotBlank()) " · ${produto.marca}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(onClick = onClick) { Text(acao) }
        }
    }
}

@Composable
private fun LinhaSugestao(item: SuggestionItem, onAdicionar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.product, style = MaterialTheme.typography.bodyLarge)
            Text(
                "cod ${item.codProd} · score ${item.score} · ${item.source ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
            )
            // Evidencia do ranking: por que este candidato entrou na lista.
            Text(
                "co-ocorrencia ${item.cooccurrence} · faixa ${item.confidenceBand ?: "-"}" +
                    if (item.complementsCod != null) " · complementa o item ${item.complementsCod}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.ean.isNotBlank()) {
                Text(
                    "EAN ${item.ean}" +
                        if (item.marca.isNotBlank()) " · ${item.marca}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (item.reason.isNotBlank()) {
                Text(item.reason, style = MaterialTheme.typography.bodySmall)
            }
            // Acao do VENDEDOR: o agente nunca mexe no carrinho -- quem
            // pergunta ao cliente e adiciona e o vendedor (decisao D1 do plano
            // de integracao padrao com os clientes).
            Button(
                onClick = onAdicionar,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Adicionar ao carrinho") }
        }
    }
}
