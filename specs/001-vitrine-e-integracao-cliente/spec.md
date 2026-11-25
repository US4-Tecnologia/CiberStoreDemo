# Feature Specification: Vitrine do cliente + integração plug-and-play (EAN)

**Feature Branch**: `001-vitrine-e-integracao-cliente`

**Created**: 2026-11-25

**Status**: Implemented (25/11/2026 - Bloco 2 do plano; o Bloco 3 fica para o repo da API)

**Input**: plano [`docs/plano-integracao-padrao-cliente-ean.md`](../docs/plano-integracao-padrao-cliente-ean.md) — Blocos 2 e 3. Plano canônico no repo da API: `D:\GIT\SmartAgenteVendasIA\docs\plano-integracao-padrao-cliente-ean.md`.

## User Scenarios & Testing

### User Story 1 — O vendedor vê a sugestão e decide (P1)

O vendedor está com um carrinho montado no app. A tela de sugestões mostra os
complementos candidatos **com a evidência** de por que cada um foi indicado
(score, origem da recomendação, nº de vezes que o par apareceu junto no
histórico, faixa de confiança) e o **motivo em linguagem natural** vindo do LLM.
Ele olha, pergunta ao cliente se precisa, e **se o cliente quiser**, adiciona ao
carrinho.

**Why this priority**: é a demonstração do produto inteiro (determinístico + IA)
e o fluxo real de venda.

**Independent Test**: desktop ou web → buscar produto → adicionar ao carrinho →
"Sugestões" → a lista aparece com score/origem/motivo; o botão "Adicionar ao
carrinho" leva o item para o carrinho.

**Acceptance Scenarios**:

1. **Given** um carrinho com itens, **When** abro "Sugestões", **Then** vejo o `pool` **imediatamente** (sem esperar o LLM) e, quando a justificativa chega, ela **enriquece** a lista (mantém os candidatos que o LLM não mencionou).
2. **Given** a fase de IA pendente, **Then** a tela mostra "aguardando IA..." e o indicador de progresso.
3. **Given** a justificativa do LLM, **Then** o card mostra o **EAN** do produto (a chave que o cliente usa), o `score`, a `source`, a faixa de confiança e o motivo.
4. **Given** uma sugestão, **When** o **vendedor** toca em "Adicionar ao carrinho", **Then** o item entra no carrinho — **ação do vendedor, não do agente**.

### User Story 2 — Modo demo: o cliente vê o fluxo em 5 segundos (P1)

Na reunião de demonstração ninguém quer digitar 13 dígitos de EAN e esperar a
busca. O app abre com um exemplo pronto.

**Acceptance Scenarios**:

1. **Given** o app recém-aberto em modo demo, **Then** já existe um carrinho de exemplo e as sugestões do produto-base correspondente.
2. **Given** o mesmo app sem modo demo, **Then** o fluxo normal (busca → carrinho → sugestões) continua igual.

### User Story 3 — O app do cliente integra sem reescrita (P2)

O time do cliente vai plugar o agente nos apps `GEMCO` / `US4Winthor` (Java).
O artefato entregue é um cliente de referência no **mesmo estilo** do
`WebService` deles, com SSE e degradação, e um guia que diz **onde** plugar.

**Acceptance Scenarios**:

1. **Given** o `cliente-java` de referência, **When** aponto a base URL e a chave (`X-API-Key` **ou** `Authorization: Bearer`), **Then** busca, carrinho e sugestões funcionam contra a API real.
2. **Given** a API fora do ar ou a cota do LLM esgotada, **Then** o cliente degrada para o pool determinístico (sem exceção para o vendedor).
3. **Given** o guia de integração, **Then** ele mostra o ponto exato de entrada nos dois apps (config de URL por ambiente e o reuse do token).

## Requirements

- **FR-001**: o DTO de sugestão (`SuggestionItem`) carrega `ean`, `marca` e `ref` — o app mostra o **EAN** (chave do cliente) junto do produto.
- **FR-002**: a tela de sugestões exibe score, `source`, coocorrência e `confidence_band` — a evidência do ranking, não só o nome do produto.
- **FR-003**: a fase `justificativa` **enriquece** o pool (nunca o substitui): os candidatos que o LLM não mencionou continuam na tela, com o motivo determinístico.
- **FR-004**: botão "Adicionar ao carrinho" na sugestão — **ação explícita do vendedor**; o app nunca adiciona sozinho.
- **FR-005**: modo demo com carrinho e produto-base pré-carregados.
- **FR-006**: badges de estado (fallback, cota esgotada, aguardando IA) já existentes são mantidos.
- **FR-007**: `cliente-java/` de referência no padrão dos apps do cliente, com SSE e degradação (no repo da API).
- **FR-008**: `docs/integracao-apps-cliente.md` com o passo-a-passo para GEMCO e US4Winthor (no repo da API; espelhado aqui).

## Success Criteria

- **SC-001**: `desktopTest` **29/29** e `wasmJsBrowserTest` **27/27** verdes (17 originais + 4 do `EnriquecerTest` + 4 do `StoreViewModelTest` + 2 do `PayloadIdentidadeTest` + 2 de integração com a API real)
- **SC-002**: fluxo completo demonstrado no browser (Chrome headless via CDP): busca → carrinho → sugestões com justificativa real do LLM → ação do vendedor
- **SC-003**: capturas de tela versionadas em `docs/evidencias/2026-11-25/`
- **SC-004**: **nenhum cliente existente quebra** — campos novos são opcionais no contrato

## Arquivos

`composeApp/src/commonMain/kotlin/com/ciberstore/demo/data/dto/Dtos.kt` ·
`composeApp/src/commonMain/kotlin/com/ciberstore/demo/ui/StoreViewModel.kt` ·
`composeApp/src/commonMain/kotlin/com/ciberstore/demo/App.kt` ·
`composeApp/src/commonTest/kotlin/com/ciberstore/demo/ui/` ·
`docs/evidencias/2026-11-25/` · `docs/plano-integracao-padrao-cliente-ean.md`

## Documentação / Evidências

- Plano: [`docs/plano-integracao-padrao-cliente-ean.md`](../docs/plano-integracao-padrao-cliente-ean.md)
- Evidência: `docs/2026-11-25-revalidacao.md` (revalidação LLM) + `docs/evidencias/2026-11-25/`
- Contrato da API: `D:\GIT\SmartAgenteVendasIA\docs\contrato-api-v1.md`

## Limitações conhecidas

- O modo demo usa produto fixo — a base real (parquet do ERP) não é embarcada.
- O app de **Android** do demo não foi instalado/executado em dispositivo (critério de M3 atendido por build + desktop + web).
- O `cliente-java` é de referência: entra no app do cliente como **cópia/adaptation**, não como dependência publicada (repo próprio fica para quando houver cliente real).

## Pendências vivas (ver BACKLOG do repo da API)

1. Variante **Kotlin/OkHttp** do cliente de referência (3.2) — só depois do Java
2. Publicar o SDK como dependência (repo/Maven) — só com cliente real
3. Emulador Android do app demo (AVD `Medium_Tablet` existe, não foi executado)
