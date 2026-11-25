# Plano (app demo) — Vitrine para o cliente + integração plug-and-play

> **Data:** 25/11/2026 · **Plano canônico:** `D:\GIT\SmartAgenteVendasIA\docs\plano-integracao-padrao-cliente-ean.md`
> (repo da API). Aqui está a parte que toca **este** repo.
>
> **Spec:** [`specs/001-vitrine-e-integracao-cliente/spec.md`](../specs/001-vitrine-e-integracao-cliente/spec.md)
>
> **Estado:** aprovado pelo solicitante. Execução por blocos.

---

## 1. Por que este app existe

O `CiberStoreDemo` (KMP: Android/Desktop/Web) é a **vitrine** do agente para o
cliente e o **esqueleto da integração** que vai entrar nos apps dele
(`GEMCO`, `US4Winthor` — Java/Android, mesma estrutura de pastas).

O que foi apurado nos apps do cliente (25/11/2026):

| Fato | Onde |
|---|---|
| Auth: `Authorization: Bearer <token>` (o token vem de `POST /consultor/login/`) | `GEMCO/app/src/main/java/br/com/us4/app/WebService.java` |
| Busca de produto por **EAN** | `GEMCO/.../persistence/ProdutoDao.java`, `adapters/ItensPedidoAdapter.java` |
| Já existe um serviço de "justificativa" no ecossistema do cliente | `GEMCO/.../activity/BaseActivity.java` (`urlJustificativaProd`) |
| Config de endpoint no padrão `urlXxxHomol` / `urlXxxProd` + `IPSERVER` | idem |
| `US4Winthor`: mesma estrutura, mas legada (sem token; `FenixWS/Consulta`) | `D:\GIT\US4Winthor` |

**Consequência:** a API do agente precisa falar **EAN** (a chave deles) — é o
**Bloco 1** do plano canônico, que muda o contrato da API e, por consequência,
o que este app mostra e envia.

---

## 2. Bloco 2 — Demo app como vitrine do cliente · ~1 dia

- **Card da sugestão** com a evidência do ranking: **EAN** (a chave do cliente),
  produto, `score`, `source` (coocorrência/prior), nº de coocorrências,
  `confidence_band`, "complementa o item X" e o **motivo do LLM**.
- **Estados visíveis**: `pool` (determinístico, instantâneo) → `justificativa`
  (IA) → badges de fallback/cota.
- **Ação do vendedor**: botão **"Adicionar ao carrinho"** na sugestão. É o
  **vendedor** tocando — o agente **nunca** mexe no carrinho (decisão D1 do
  plano canônico). Fecha o fluxo real: *vendedor vê → pergunta ao cliente →
  cliente diz que sim → vendedor adiciona*.
- **Modo demo**: 1 produto pré-carregado (carrinho + sugestão prontos) para o
  vendedor ver o fluxo completo em 5 s, sem digitar.

**Aceite:** `desktopTest` + `wasmJsBrowserTest` verdes (21 → 25 testes) ·
capturas em `docs/evidencias/2026-11-25/` · fluxo do vendedor demonstrado no
browser (Chrome headless via CDP, como em 25/11).

---

## 3. Bloco 3 — `cliente-java/` no padrão dos apps do cliente · ~1 dia

Entrega no **repo da API** (`D:\GIT\SmartAgenteVendasIA\cliente-java\`), no
padrão do GEMCO/US4Winthor:

- `services/SmartOrderService.java` + `repository/SmartOrderRepository.java`,
  espelhando `WebService`/`ProdutoDao`/`BaseRepository`/`ProdutoRepository`.
- **Opção de conexão e chave** (liga uma ou outra): `X-API-Key` **ou**
  `Authorization: Bearer <token>` — o mesmo `Bearer` que o GEMCO já manda ao ERP.
- Streaming **SSE** no estilo `HttpURLConnection`/Apache deles (sem OkHttp/
  Retrofit, sem dependência nova) + degradação (timeout/cota → pool determinístico).
- `docs/integracao-apps-cliente.md`: passo-a-passo para GEMCO **e** US4Winthor.
- Variante **Kotlin/OkHttp** → apenas **documentada** (3.2, depois).

**Aceite:** o cliente compila e roda contra a API real (mesmo roteiro dos testes
de integração do desktop: busca → carrinho → sugestões).

---

## 4. O que este app **não** faz (e por quê)

| Não faz | Por quê |
|---|---|
| Não cria/altera pedido | decisão de risco (D1); o agente só sugere, quem age é o vendedor |
| Não obriga a UI do cliente | a API entrega dados + estado; o app decide se vira lista ou mensagem (D3) |
| Não usa OAuth2 próprio | o GEMCO já manda `Bearer`; reaproveitar o token do ERP (Fase C) é o caminho |

---

## 5. Ordem

| # | Bloco | Onde | Esforço |
|---|---|---|---|
| 1 | Contrato flexível (EAN) | repo da API | 1 dia |
| 2 | **Demo app vitrine** | **este repo** | 1 dia |
| 3 | `cliente-java` + guia | repo da API (+ doc aqui) | 1 dia |
| 4 | Abstração no servidor | repo da API | 1 dia |

Cada bloco fecha com: testes verdes · evidência · CHANGELOG/README ·
commit + push.
