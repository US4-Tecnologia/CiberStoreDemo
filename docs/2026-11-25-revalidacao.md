# Revalidação com LLM real — 25/11/2026 (M4 parcial)

> Fechamento da pendência "Web + justificativa real" que estava aberta em
> [`2026-11-24-m2-m3.md`](2026-11-24-m2-m3.md) §6, e correção de um bug de UX
> descoberto no caminho.
>
> **Verde**: 4/4 testes de integração contra a API real (desktop) · 25 testes
> offline, 0 falhas · bundle Wasm de produção rodando no Chrome headless com a
> **justificativa real do LLM** renderizada na tela.

---

## 1. O que foi feito

| Item | Resultado |
|---|---|
| Publicação do repo | ✅ `origin` = `US4-Tecnologia/CiberStoreDemo`, `main` @ `cc3f06e` (`git ls-remote` confirma `refs/heads/main = cc3f06e`) |
| Teste opt-in da fase `justificativa` (desktop) | ✅ novo `SSE real entrega justificativa do LLM depois do pool` — **3,47 s**, 0 falhas |
| Browser (web) com justificativa real | ✅ busca → carrinho → Sugestões, justificativa do LLM **renderizada** |
| Bug de UX: a tela substituía o pool pela lista do LLM | ✅ corrigido (`enriquecer()`) + 4 testes |

---

## 2. Teste de integração opt-in (desktop)

`composeApp/src/desktopTest/kotlin/com/ciberstore/demo/data/IntegrationTest.kt`
ganhou um 4º caso, desligado por default como os outros:

```powershell
# API no repo SmartAgenteVendasIA
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8099
# app
.\gradlew.bat :composeApp:desktopTest "-Dsmartorder.integration=true"
```

```
IntegrationTest            tests=4 failures=0
   - status real                                    (0.433s)
   - SSE real entrega justificativa do LLM depois do pool (3.47s)   <-- novo
   - SSE real entrega pool antes do done            (0.027s)
   - busca real de produtos                          (0.764s)
```

> **Por que o produto 14151 ("TUBO 6,00M 25MM PVC SOLD") e não o 124?**
> A chave de cache da justificativa na API é `(cod_prod, carrinho)` com TTL de
> 300 s. Repetir o 124 (usado nos outros testes) faria o app pegar
> `cache_hit` e **não** provaria o caminho do LLM.

---

## 3. Web — bundle de produção no Chrome headless

O `wasmJsBrowserDevelopmentRun` abre o browser padrão da máquina; para uma
execução repetível o bundle de **produção** foi servido estaticamente e dirigido
por CDP (Chrome DevTools Protocol):

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDistribution      # build\dist\wasmJs\productionExecutable
node serve.mjs build\dist\wasmJs\productionExecutable 8081
chrome.exe --headless=new --remote-debugging-port=9222 --user-data-dir=<perfil> http://127.0.0.1:8081/index.html
node cdp.mjs acoes.json
```

O roteiro (JSON de ações do driver CDP):

1. clicar no campo de busca → digitar `TINTA` (eventos de tecla reais, não
   `insertText`: o Compose Web em canvas não tem `<input>` no DOM);
2. clicar em **Buscar** → `GET /api/v1/produtos?q=TINTA&limit=20` + paginação
   por cursor (372 resultados, 19 páginas);
3. clicar em **Adicionar** no 1º resultado (**TINTA ACR 18L DUCRYL**, cod 124);
4. clicar em **Sugestões** → `POST /api/v1/sugestoes/stream`;
5. capturar a tela no meio do stream e depois do fim.

### Evidência no servidor

`execution.log` da API — o `request_id` é o `TraceId` gerado pelo app
(correlação ponta a ponta, como em 24/11):

```json
{"request_id": "297cb343ea19f8dc-cf2289c573c47296", "node": "suggestions_pool", "latency_ms": 2301.6, "count": 5, "seeds": 1}
{"request_id": "297cb343ea19f8dc-cf2289c573c47296", "node": "suggestions_justificativa", "latency_ms": 3852.4, "cache_hit": false, "count": 1}
```

Browser: **0 erros de console**, **0 falhas de rede**, preflight CORS aceito.

---

### Capturas

| Arquivo | O que mostra |
|---|---|
| [`evidencias/2026-11-25/web_01_busca_tinta.png`](evidencias/2026-11-25/web_01_busca_tinta.png) | busca "TINTA" no browser — 372 resultados, 1º = TINTA ACR 18L DUCRYL (cod 124) |
| [`evidencias/2026-11-25/web_02_aguardando_ia.png`](evidencias/2026-11-25/web_02_aguardando_ia.png) | tela de sugestões com o indicador "aguardando IA..." (fase 2 em curso) |
| [`evidencias/2026-11-25/web_03_justificativa_llm.png`](evidencias/2026-11-25/web_03_justificativa_llm.png) | **5 recomendações** (pool preservado) com a justificativa real do LLM no item explicado |

---

## 4. Bug de UX encontrado no browser — a tela trocava o pool pela lista do LLM

O LLM devolveu **1** candidato válido dos 5 do pool. A tela fazia:

```kotlin
onJustificativa = { explicadas -> _state.update { it.copy(sugestoes = explicadas) } }
```

→ o pool determinístico (5 recomendações) era **descartado** e a tela ficava
com 1 item. Isso contraria a regra de ouro do próprio app ("a tela renderiza
no `pool` e **se enriquece** na `justificativa`").

**Correção** (`composeApp/src/commonMain/kotlin/com/ciberstore/demo/ui/StoreViewModel.kt`):

```kotlin
internal fun enriquecer(
    pool: List<SuggestionItem>,
    justificativa: List<SuggestionItem>,
): List<SuggestionItem> {
    if (justificativa.isEmpty()) return pool
    if (pool.isEmpty()) return justificativa
    val porCod = justificativa.associateBy { it.codProd }
    return pool.map { item -> porCod[item.codProd] ?: item }
}
```

O pool continua na tela (5 itens) e o item que o LLM explicou ganha o motivo
real. Testes: `composeApp/src/commonTest/kotlin/com/ciberstore/demo/ui/EnriquecerTest.kt`
(4 casos, roda no desktop **e** no browser).

---

## 5. Gotchas do dia

| Gotcha | Solução |
|---|---|
| O `.env` da API não tinha `http://127.0.0.1:8081` no `CORS_ALLOW_ORIGINS` → o browser foi bloqueado no preflight | acresentar a origem no `.env` (é gitignored) e reiniciar a API |
| `Input.insertText` não digita nada no Compose Web (canvas): o `document.activeElement` continua sendo o `<canvas>` | evento de tecla real (`keyDown` com `text` + `keyUp`) |
| `Input.dispatchMouseEvent` sem `buttons: 1` no `mousePressed` **não foca** o campo de texto | `buttons: 1` no pressed e `buttons: 0` no released |
| `curl.exe -d $body` no PowerShell corrompe o JSON (HTTP 422 `body.1 JSON decode error`) | `--data-binary "@arquivo.json"` |

---

## 6. Estado após a sessão

- `main` @ `cc3f06e` **publicado**; a branch local foi renomeada de `master` para `main`.
- Pendência "Publicação do repo" de [`2026-11-24-m2-m3.md`](2026-11-24-m2-m3.md) §6 → **fechada**.
- Pendência "Web + justificativa real" → **fechada** (o que era do M4 coube aqui).
- **M4** (E2E contra a stack Docker `SMARTORDER_HTTP_PORT=8081` + lista de
  ajustes do contrato) continua previsto para **30/11–02/12**.
- Emulador Android: o APK ainda não foi instalado/executado (o critério de M3 foi
  atendido por build + janela desktop + boot web).
