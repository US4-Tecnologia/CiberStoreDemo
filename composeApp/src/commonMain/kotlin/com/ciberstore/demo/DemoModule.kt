package com.ciberstore.demo

import com.ciberstore.demo.data.ApiClient
import com.ciberstore.demo.data.KtorSseTransport
import com.ciberstore.demo.data.Repository
import com.ciberstore.demo.ui.StoreViewModel
import io.ktor.client.HttpClient

/**
 * Montagem do grafo do app (M3). Fica em `commonMain` para que os 3 targets
 * so precisem informar a URL base da API:
 *
 * - Desktop / Web: `http://127.0.0.1:8099`
 * - Emulador Android: `http://10.0.2.2:8099` (alias do host)
 *
 * Um unico [HttpClient] e compartilhado entre REST e SSE. Nenhum timeout de
 * leitura e instalado: a fase `justificativa` do SSE pode levar ate 30 s.
 */
fun criarViewModel(baseUrl: String, apiKey: String? = null): StoreViewModel {
    val client = HttpClient()
    val api = ApiClient(baseUrl, apiKey, client)
    val sse = KtorSseTransport(baseUrl, apiKey, client)
    return StoreViewModel(Repository(api, sse))
}
