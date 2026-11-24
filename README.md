# CiberStore Demo

App demo **Kotlin Multiplatform** (Android + Desktop + Web) em **Compose Multiplatform**,
que consome o contrato da API SmartOrder AI (`docs/openapi.yaml` do repo
`D:\GIT\SmartAgenteVendasIA`).

**Fase atual: M2 + M3 concluídos (24/11/2026)** — camada compartilhada com
17 testes offline e UI de carrinho (busca → carrinho → sugestões via SSE)
nos 3 targets.

## Toolchain (validada em 19/11 e 24/11/2026)

| Ferramenta | Versão |
|---|---|
| JDK | 17 (`JAVA_HOME=C:\Program Files\Java\jdk-17`) |
| Gradle | 8.10.2 (wrapper em `./gradlew.bat`) |
| Kotlin | 2.0.21 |
| Compose Multiplatform | 1.7.3 |
| Android Gradle Plugin | 8.7.3 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (via `local.properties`, gitignored) |
| Ktor client | **3.0.3** (última série sobre Kotlin 2.0.21) |
| Lifecycle ViewModel CMP | 2.8.4 |
| kotlinx-serialization / coroutines | 1.7.3 / 1.9.0 |

## Marcos

| Marco | Conteúdo | Critério de pronto | Situação |
|---|---|---|---|
| **M1** | Projeto KMP: `androidTarget()` + `jvm("desktop")` + `wasmJs()` | build verde nos 3 targets | ✅ 19/11/2026 |
| **M2** | `commonMain`: DTOs do OpenAPI, `SseParser`, `ErrorMapper`/`ApiAction`, `TraceId`, `Cache`, `Repository` (cache + degradação + cursor), `ApiClient` (Ktor) e `KtorSseTransport` | testes unitários offline | ✅ **24/11 — 17 testes** rodando em desktop **e** no navegador |
| **M3** | UI: `StoreViewModel` (CMP) + `BuscaScreen` / `CarrinhoScreen` / `SugestoesScreen` | demo em desktop e emulador | ✅ **24/11** — janela desktop aberta, APK 11,7 MB, canvas Wasm inicializado |
| **M4** | E2E contra a stack Docker (`SMARTORDER_HTTP_PORT=8081`) | lista de ajustes do contrato | ⏳ 30/11–02/12 |

## Arquitetura (M2)

```
commonMain/com/ciberstore/demo/
├── data/
│   ├── dto/Dtos.kt              DTOs @Serializable do openapi.yaml
│   ├── sse/SseParser.kt         parser PURO de event:/data: (testavel sem rede)
│   ├── ErrorMapper.kt           HTTP -> ApiAction (nao repetir 401/403/422)
│   ├── TraceId.kt               X-Client-Trace-Id (sem java.util.UUID)
│   ├── Cache.kt                 interface + InMemoryCache
│   ├── Repository.kt            cursor + degradacao para cache
│   ├── ApiClient.kt             REST via Ktor body<String>()
│   ├── SseTransport.kt          interface do SSE
│   └── KtorSseTransport.kt      SSE incremental via bodyAsChannel()
├── ui/StoreViewModel.kt         estado (ViewModel do CMP)
├── App.kt                      3 telas + navegacao por estado
└── DemoModule.kt               monta Api + Sse + Repository + ViewModel
```

**Regra de ouro:** a tela renderiza no evento `pool` (determinístico, ~ms) e
só enriquece no `justificativa` (LLM, até 30 s). Nenhum timeout de leitura é
instalado no `HttpClient` — no cliente de referência `cliente-kotlin/`, o
`readTimeout` do OkHttp matava a fase 2 do SSE (achado de 18/11/2026).

**Por que Ktor e não `fetch` + `ReadableStream` manual no web:** o engine
`ktor-client-core-wasm-js` do Ktor 3.0.3 já implementa
`channelFromStream(ReadableStream)` com `reader.readChunk()` + `flush()` por
chunk (ver `BrowserFetch.kt` no fonte do Ktor). Ou seja, `bodyAsChannel()`
entrega as linhas incrementalmente **também no Wasm**, preservando as 2 fases
sem código JS específico de plataforma.

## Rodar

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# --- API (no repo SmartAgenteVendasIA) -----------------------------------
cd D:\GIT\SmartAgenteVendasIA
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8099 --log-level warning

# --- App ------------------------------------------------------------------
cd D:\GIT\CiberStoreDemo
.\gradlew.bat :composeApp:run                        # Desktop (janela)
.\gradlew.bat :composeApp:assembleDebug              # APK em composeApp\build\outputs\apk\debug\
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun # Web (Chrome)

# --- Testes ---------------------------------------------------------------
.\gradlew.bat :composeApp:desktopTest               # 17 testes offline (JVM)
.\gradlew.bat :composeApp:wasmJsBrowserTest         # os mesmos 17 no navegador
# integracao opt-in contra a API real (3 testes):
.\gradlew.bat :composeApp:desktopTest "-Dsmartorder.integration=true"
```

> ⚠️ **PowerShell:** `-Dsmartorder.integration=true` **sem aspas** quebra no `=`
> (o Gradle interpreta `.integration=true` como nome de task). Use aspas duplas.

## URLs e CORS

| Alvo | URL base da API | Observação |
|---|---|---|
| Desktop / Web | `http://127.0.0.1:8099` | usar `127.0.0.1`, não `localhost` (IPv6 lento nesta máquina) |
| Emulador Android | `http://10.0.2.2:8099` | alias do host; `usesCleartextTraffic=true` só no debug |

No **`.env` da API** (dev, gitignored) é preciso liberar a origem do browser:

```
CORS_ALLOW_ORIGINS=http://localhost:8080,http://127.0.0.1:8080,http://localhost:8081
```

> A porta do `wasmJsBrowserDevelopmentRun` é escolhida dinamicamente porque
> **8080 está ocupada pelo Tomcat9**; em 24/11 o webpack subiu em **8081**.
> Confira a porta real antes de ajustar o CORS (`Get-NetTCPConnection`).

**Segurança:** a chave de produção **não** vai para o browser. O demo local usa
a API aberta do dev (`API_KEYS` vazio).

## Evidência (24/11/2026)

| Check | Resultado |
|---|---|
| `compileKotlinDesktop` / `compileKotlinWasmJs` / `compileDebugKotlinAndroid` | ✅ os 3 |
| `desktopTest` (offline) | ✅ 17/17 |
| `wasmJsBrowserTest` (Karma + Chrome headless) | ✅ 17/17 |
| `desktopTest -Dsmartorder.integration=true` | ✅ 3/3 contra a API real (busca TINTA, `/status`, SSE `pool → done`) |
| Janela desktop | ✅ abriu (`CiberStore Demo`) |
| Web | ✅ dev server HTTP 200, canvas `764×485` inicializado, 0 erros no console |
| Android | ✅ `composeApp-debug.apk` (11,7 MB) |
| CORS preflight | ✅ `OPTIONS` 200 com `Access-Control-Allow-Origin` |
| Correlação | ✅ `execution.log` da API registrou o `request_id` gerado pelo `TraceId` do app (`98f5335f…-8530a0e25d81a187`) |

Registro completo: [`docs/2026-11-24-m2-m3.md`](docs/2026-11-24-m2-m3.md).

## Notas

- `local.properties` é local (gitignored) e aponta para o Android SDK.
- O `build.gradle.kts` mantém o workaround da `desktopRun` do KGP (o
  `mainClass` do Compose Desktop não é herdado pela task carrier do IDE).
- AVM `Medium_Tablet` (android-28, x86) já existe na máquina para o M3/M4.
