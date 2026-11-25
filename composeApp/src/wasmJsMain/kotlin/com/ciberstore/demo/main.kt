package com.ciberstore.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow

/**
 * Web (Wasm): a API local precisa de `CORS_ALLOW_ORIGINS` no `.env` do
 * servidor. A chave de producao **nunca** vem para o browser -- o demo
 * local usa a API aberta do dev.
 */
private const val BASE_URL = "http://127.0.0.1:8099"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        // Modo demo ligado: abre com um carrinho de exemplo e as sugestoes
        // ja carregadas (para o cliente ver o fluxo completo em 5 s).
        App(criarViewModel(BASE_URL, modoDemo = true))
    }
}
