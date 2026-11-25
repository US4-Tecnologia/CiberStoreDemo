# Bloco 2 — Demo app como vitrine do cliente · 25/11/2026

> Plano: [`docs/plano-integracao-padrao-cliente-ean.md`](plano-integracao-padrao-cliente-ean.md)
> · Spec: [`specs/001-vitrine-e-integracao-cliente/spec.md`](../specs/001-vitrine-e-integracao-cliente/spec.md)
>
> O `CiberStoreDemo` é a **vitrine** do agente para o cliente e o esqueleto da
> integração que vai entrar nos apps dele (`GEMCO`, `US4Winthor` — Java).
>
> **Verde:** `desktopTest` **29/29** (25 offline + 4 de integração) ·
> `wasmJsBrowserTest` **25/25** · bundle Wasm de produção rodando no Chrome
> headless com a justificativa real do LLM.

---

## 1. O que mudou no app

### 1.1 O card da sugestão agora mostra a **evidência** do ranking

| Antes | Agora |
|---|---|
| produto, score, source, motivo | produto, **EAN**, marca, score, source, **coocorrência**, **faixa de confiança**, **"complementa o item X"**, motivo |

O `ean`/`marca`/`ref` chegaram pela **identidade de produto** do Bloco 1
(contrato v1.1) — o app mostra a chave que o cliente usa.

### 1.2 Ação do **vendedor** na sugestão

Botão **"Adicionar ao carrinho"** em cada sugestão. É o **vendedor** tocando:
o agente **nunca** mexe no carrinho (decisão D1 do plano). Fecha o fluxo real:

> vendedor vê a sugestão → pergunta ao cliente → cliente diz que sim → vendedor toca em adicionar

`StoreViewModel.adicionarSugestao()` converte o `SuggestionItem` em
`ProductItem` (preservando `ean`/`marca`/`ref`) e respeita o limite de 10 itens
e a regra de duplicidade.

### 1.3 Modo demo

`criarViewModel(baseUrl, apiKey, modoDemo = true)` abre o app **já com um
carrinho de exemplo e as sugestões pedidas** — o cliente vê o fluxo completo
em ~5 s, sem digitar nada. O produto de exemplo é fixo (`TINTA ACR 18L DUCRYL`,
cod 124, EAN `7895860040265`) para o modo demo **não depender de rede** para
montar o carrinho: só a justificativa (SSE) vai ao servidor.

---

## 2. 🐛 Achado no caminho — app web sem exceção e sem trabalho no construtor

### 2.1 O sintoma

Na primeira rodada de build do Wasm (com o modo demo disparando no `init` do
`ViewModel`), a captura do browser acusou:

```
RuntimeError: illegal cast
    at http://127.0.0.1:8081/CiberStoreDemo-composeApp-wasm-js.wasm:wasm-function[15052]:0x3a5239
```

**O app renderizava normalmente** nessa rodada (a tela de sugestões apareceu
completa) — ou seja, a exceção não impedia a UI.

### 2.2 O que foi feito (e o que é fato)

1. **Fato**: a exceção vinha de uma **aba antiga do Chrome**, que ainda tinha o
   bundle anterior em cache (o driver CDP conecta na primeira aba `page` da
   lista). Navegando de novo numa aba limpa, a exceção **desapareceu**.
2. **Fato**: o desktop (JVM) **nunca** acusou exceção; os **25 testes do browser**
   (`wasmJsBrowserTest`) passam, incluindo o novo teste que decodifica o payload
   real da API v1.1 (com `ean`/`marca`/`ref`).
3. **Mudança feita** (boa prática, independentemente da causa): o modo demo
   deixou de disparar no `init` do `ViewModel` e passou a ser disparado pela
   **UI** (`LaunchedEffect(Unit) { viewModel.carregarDemo() }` em `App.kt`).
   Efeito de tela pertence à tela, e um `ViewModel` não deve iniciar
   corrotinas no construtor.

> **Registro honesto:** a causa-raiz do `illegal cast` **não foi comprovada**.
> O sintoma sumiu quando (a) a aba obsoleta deixou de ser a alvo do driver e
> (b) o disparo saiu do `init`. A hipótese mais provável é a aba com o bundle
> antigo — mas o `init` com `viewModelScope` continua sendo uma prática
> suspeita que valeu a troca. **Fica como pendência:** reproduzir o
> `illegal cast` de forma determinística (build limpo + aba limpa) e fechar a
> causa.

### 2.3 Estado atual do browser

| Verificação | Resultado |
|---|---|
| Exceções JS | **0** |
| Erros de console | **0** (só 4 avisos de WebGL do Skiko no Chrome headless) |
| Falhas de rede | **0** |
| Requisição para a API | `POST /api/v1/sugestoes/stream` (o modo demo dispara o SSE sozinho) |
## 3. Testes

| Suite | Resultado |
|---|---|
| `desktopTest` (offline) | **29/29** · 0 falhas |
| `wasmJsBrowserTest` (Chrome headless) | **25/25** · 0 falhas |
| `desktopTest "-Dsmartorder.integration=true"` | **4/4** contra a API real |

Testes novos (`composeApp/src/commonTest/kotlin/com/ciberstore/demo/ui/StoreViewModelTest.kt`):

| Caso | Trava |
|---|---|
| `sugestao adicionada ao carrinho pelo vendedor` | a ação do vendedor adiciona o item **com a identidade** (EAN/marca) |
| `sugestao repetida nao entra duas vezes no carrinho` | a regra de duplicidade do carrinho |
| `modo demo abre com carrinho de exemplo e nas sugestoes` | o modo demo monta o carrinho e vai para a tela de sugestões |
| `sem modo demo o carrinho comeca vazio` | o modo demo é opt-in |

> Nota de teste: o `Dispatchers.setMain(StandardTestDispatcher(testScheduler))`
> precisa compartilhar o scheduler do `runTest` — com um dispatcher separado, o
> `advanceUntilIdle()` não enxerga a corrotina.

---

## 4. Prova visual (browser)

Bundle Wasm de **produção** servido estaticamente e dirigido por CDP (Chrome
headless), como em 24/11:

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
node serve.mjs composeApp\build\dist\wasmJs\productionExecutable 8081
chrome.exe --headless=new --remote-debugging-port=9222 http://127.0.0.1:8081/index.html
```

| Arquivo | O que mostra |
|---|---|
| [`evidencias/2026-11-25/vitrine_01_sugestoes.png`](evidencias/2026-11-25/vitrine_01_sugestoes.png) | tela de sugestões com EAN, evidência do ranking e o botão do vendedor |
| [`evidencias/2026-11-25/vitrine_02_carrinho.png`](evidencias/2026-11-25/vitrine_02_carrinho.png) | o item adicionado ao carrinho pela ação do vendedor |

---

## 5. Estado

- `main` @ `8a690e2` (publicado) + o commit do Bloco 2.
- Pendências do app: **Bloco 3** (`cliente-java` + guia de integração, no repo da
  API) e a variante Kotlin/OkHttp (3.2, só documentada).
- Emulador Android: o APK continua **não instalado/executado** (o critério de M3
  foi atendido por build + desktop + web).
