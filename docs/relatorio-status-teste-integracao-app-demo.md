# Relatório de status — teste de integração com o app demo

> **Data:** 25/11/2026 · **Escopo:** o quanto do **fluxo real** (app ↔ API) já
> está coberto por teste automatizado, provado visualmente e o que **ainda
> depende de teste manual**.
>
> App: `D:\GIT\CiberStoreDemo` (KMP: Android/Desktop/Web) ·
> API: `D:\GIT\SmartAgenteVendasIA` (contrato v1.1) — a versão completa, com o
> lado da API, está em
> `SmartAgenteVendasIA/docs/relatorio-status-teste-integracao-app-demo.md`.

---

## 1. Status

| Frente | Estado |
|---|---|
| Teste automatizado do app | 🟢 **verde** — `desktopTest` **29/29** · `wasmJsBrowserTest` **27/27** |
| Teste de integração contra a **API real** | 🟢 **4/4** (desktop, opt-in) |
| Prova visual no **browser** (bundle de produção) | 🟢 renderizou, **0 exceções**, **0 erros de console** |
| **Teste manual** (janela desktop, web, Android, degradação) | 🟡 **pendente** — checklist no §4 |
| App do **cliente** (GEMCO / US4Winthor) | ⚪ não plugado ainda — é a fase de validação |

## 2. O que já é coberto por teste automatizado

### 2.1 O app (`commonTest`, roda no desktop **e** no browser)

| Grupo | O que trava |
|---|---|
| `SseParser` | as 3 fases do stream (`pool` → `justificativa` → `done`) |
| `ErrorMapper` | 401/403/422/429/503 → **ação** para a UI (nunca exceção crua) |
| `Cache` / `TraceId` / `Repository` | cache, correlação, cursor, degradação para o cache |
| `Enriquecer` | a justificativa **enriquece** o pool — não substitui (os candidatos que o LLM não mencionou continuam) |
| `StoreViewModel` | a **ação do vendedor** (adicionar ao carrinho, com a identidade do produto) e o **modo demo** |
| `PayloadIdentidade` | o payload real da v1.1 (`ean`/`marca`/`ref`) é decodificado |

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat :composeApp:desktopTest        # 29 testes
.\gradlew.bat :composeApp:wasmJsBrowserTest  # os mesmos no navegador
```

### 2.2 Integração com a API real (`desktopTest`, opt-in)

| Teste | O que prova |
|---|---|
| `busca real de produtos` | a busca do app conversa com o contrato v1 |
| `status real` | o `/status` responde |
| `SSE real entrega pool antes do done` | o stream chega em 2 fases e na ordem |
| `SSE real entrega justificativa do LLM depois do pool` | a **fase 2 real** (LLM) chega depois do pool, com o motivo |

```powershell
# a API precisa estar no ar
cd D:\GIT\SmartAgenteVendasIA
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8099

cd D:\GIT\CiberStoreDemo
.\gradlew.bat :composeApp:desktopTest "-Dsmartorder.integration=true"
```

> O teste de integração usa o produto **14151** ("TUBO 6,00M 25MM PVC SOLD") de
> propósito: a chave de cache da justificativa na API é `(cod_prod, carrinho)`
> com TTL de 300 s — repetir o 124 faria o app pegar `cache_hit` e não provaria
> o caminho do LLM.

### 2.3 Prova visual (browser)

O **bundle de produção** (`wasmJsBrowserDistribution`) foi servido e dirigido por
Chrome headless (CDP): busca → carrinho → Sugestões → justificativa real do LLM
renderizada → clique em "Adicionar ao carrinho" → o item entrou no carrinho
(com EAN).

| Captura | O que mostra |
|---|---|
| [`evidencias/2026-11-25/vitrine_01_sugestoes.png`](evidencias/2026-11-25/vitrine_01_sugestoes.png) | as 5 sugestões com EAN, evidência do ranking e o motivo do LLM |
| [`evidencias/2026-11-25/vitrine_02_vendedor_adicionou.png`](evidencias/2026-11-25/vitrine_02_vendedor_adicionou.png) | o carrinho com 2 itens após a ação do vendedor |
| [`evidencias/2026-11-25/vitrine_03_carrinho.png`](evidencias/2026-11-25/vitrine_03_carrinho.png) | o carrinho com os dois itens e seus EANs |

> Console do browser: **0 exceções**, **0 erros de console** (só 4 avisos de WebGL
> do Skiko em headless).

## 3. O que o teste automatizado **não** cobre

| Fora da cobertura automatizada | Por quê |
|---|---|
| **Janela desktop** (a UI real, com mouse/teclado) | o teste roda a camada de dados; a janela foi aberta uma vez (sem erro), mas sem interação automatizada |
| **Android** (emulador/dispositivo) | o APK compila (`assembleDebug`), mas **nunca foi instalado nem executado** — o AVD `Medium_Tablet` (android-28, x86) existe na máquina |
| **App do cliente** (GEMCO / US4Winthor) | ainda não plugado — é o objetivo da sessão de validação |
| **Comportamento com a API fora do ar**, na UI | a degradação está implementada e testada na camada de dados, não na tela |
| **Busca por EAN** digitando o código de barras | o contrato está testado (`?ean=` e `?q=`); falta digitar no app |

## 4. 🟡 Checklist do teste manual (o que falta)

> Para o cliente ver, é preciso rodar o app **com a API no ar**.

```powershell
# 0) API no ar (repo SmartAgenteVendasIA)
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8099 --log-level warning
curl "http://127.0.0.1:8099/api/v1/health"   # confere: status, slug_alive, quota
```

| # | O que testar | Como | O que esperamos |
|---|---|---|---|
| 1 | **Desktop — o fluxo completo** | `cd D:\GIT\CiberStoreDemo` · `.\gradlew.bat :composeApp:run` | Abre com o **modo demo** (carrinho de exemplo + sugestões já pedidas). Ver as 5 recomendações com EAN, score, origem, coocorrência, faixa e o **motivo do LLM**. Clicar em **"Adicionar ao carrinho"** → o item entra no carrinho com o EAN. Clicar em "Carrinho" → 2 itens |
| 2 | **Web — o mesmo fluxo** | `.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun` | Abre no navegador (porta 8081: confira a real). Mesma tela, mesmas sugestões. A URL da API precisa estar liberada no CORS (`http://127.0.0.1:8081`) |
| 3 | **Busca por EAN** | aba Busca → digitar um código de barras (ex.: `7895860040265`) | Deve achar o produto pelo código de barras, não só pelo nome |
| 4 | **Android** | `.\gradlew.bat :composeApp:assembleDebug` → instalar o APK no AVD `Medium_Tablet` | O app abre e o fluxo funciona no celular (a URL da API no emulador é `10.0.2.2:8099`) |
| 5 | **Degradação** | parar a API (Ctrl+C) e usar o app | **Não pode quebrar**: o app deve avisar e mostrar o cache/última resposta; ao religar, volta sozinho |
| 6 | **Justificativa do LLM** (opcional) | observar o rodapé na tela de sugestões | O selo de cota/fallback aparece quando o LLM não responde (a cota é de ~50 req/dia) |

**O que anotar**: o que o vendedor **não entende** ou **não confia** na tela; campo
que falta; valor que o app precisa e a API não devolve. Essa lista é a
**lista de ajustes do contrato** (M4).

## 5. Referências

| Onde | O quê |
|---|---|
| [`docs/2026-11-25-vitrine-cliente.md`](2026-11-25-vitrine-cliente.md) | o Bloco 2 (vitrine) em detalhe |
| [`docs/2026-11-25-revalidacao.md`](2026-11-25-revalidacao.md) | a revalidação com LLM real no app |
| [`docs/plano-integracao-padrao-cliente-ean.md`](plano-integracao-padrao-cliente-ean.md) | o plano (parte do app: Bloco 2) |
| [`specs/001-vitrine-e-integracao-cliente/spec.md`](../specs/001-vitrine-e-integracao-cliente/spec.md) | a spec da vitrine |
| `SmartAgenteVendasIA/docs/integracao-apps-cliente.md` | passo a passo para plugar nos apps do cliente |
| `SmartAgenteVendasIA/docs/roteiro-validacao-cliente.md` | roteiro de 30 min da sessão de validação |
