package com.ciberstore.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Sobrescreva com `-Dsmartorder.baseUrl=...` se precisar de outra URL. */
private val BASE_URL: String = System.getProperty("smartorder.baseUrl") ?: "http://127.0.0.1:8099"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "CiberStore Demo") {
        // Modo demo ligado: abre com um carrinho de exemplo e as sugestoes
        // ja carregadas (para o cliente ver o fluxo completo em 5 s).
        App(criarViewModel(BASE_URL, modoDemo = true))
    }
}
