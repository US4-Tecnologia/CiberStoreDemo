package com.ciberstore.demo.data

import kotlin.random.Random
import kotlin.random.nextULong

/**
 * Correlacao com o servidor (contrato v1): o header `X-Client-Trace-Id`
 * liga a requisicao do app a `execution.log`, `audit.jsonl` e `/metrics`.
 *
 * Implementacao 100% `commonMain` (sem `java.util.UUID`, sem
 * `expect/actual`): um id pseudo-aleatorio por sessao e mais do que
 * suficiente para correlacionar logs num app de demonstracao.
 */
object TraceId {
    var current: String = novo()
        private set

    fun novo(): String =
        "${Random.nextULong().toString(16)}-${Random.nextULong().toString(16)}"

    /** Rotaciona ao abrir um novo fluxo de tela. */
    fun rotate(): String {
        current = novo()
        return current
    }
}
