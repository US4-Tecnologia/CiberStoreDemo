# CiberStore Demo

App demo **Kotlin Multiplatform** (Android + Desktop + Web) em **Compose Multiplatform**,
que consome o contrato da API SmartOrder AI (`docs/openapi.yaml` do repo
`D:\GIT\SmartAgenteVendasIA`).

**Fase atual: M1 (scaffold)** — os 3 targets compilam com a UI de smoke (tela de busca vazia).

## Toolchain (validada em 19/11/2026)

| Ferramenta | Versão |
|---|---|
| JDK | 17 (`JAVA_HOME=C:\Program Files\Java\jdk-17`) |
| Gradle | 8.10.2 (wrapper em `./gradlew.bat`) |
| Kotlin | 2.0.21 |
| Compose Multiplatform | 1.7.3 |
| Android Gradle Plugin | 8.7.3 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (via `local.properties`, gitignored) |

## Rodar

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

.\gradlew.bat :composeApp:run                       # Desktop (janela) — task canônica do CMP
.\gradlew.bat :composeApp:desktopRun                # Desktop via task do KGP (carrier do IDE; mainClass configurado no build script)
.\gradlew.bat :composeApp:assembleDebug             # Android (APK em composeApp\build\outputs\apk\debug\)
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun   # Web (Wasm) no navegador
```

## Marcos

| Marco | Conteúdo | Critério de pronto | Situação |
|---|---|---|---|
| **M1** | Projeto KMP: `androidTarget()` + `jvm("desktop")` + `wasmJs()` | build verde nos 3 targets | ✅ 19/11/2026 |
| **M2** | Camada compartilhada: client Ktor, DTOs do OpenAPI, repositório com cache + degradação + `ErrorMapper` + SSE 2 fases | testes unitários offline | ⏳ |
| **M3** | UI de carrinho: busca → carrinho → sugestões (pool antes da justificativa) | demo em desktop e emulador | ⏳ |
| **M4** | E2E contra a stack Docker (`SMARTORDER_HTTP_PORT=8081`) | lista de ajustes do contrato gerada | ⏳ |

## Notas

- **SSE é `POST`** → no web usar `fetch` + `ReadableStream` (não `EventSource`) — risco mapeado do M2/M3.
- **Web + CORS**: setar `CORS_ALLOW_ORIGINS` no `.env` da API (vazio por padrão = sem CORS).
- **Chave de API não vai para o browser**: chave dedicada ou auth off no ambiente local.
- O `local.properties` é local (gitignored) e aponta para o Android SDK da máquina.
