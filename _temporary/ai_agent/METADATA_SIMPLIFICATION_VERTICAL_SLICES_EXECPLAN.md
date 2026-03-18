# Metadata Contract Simplification: Vertical Slice ExecPlan

This ExecPlan is a living document. Keep `Progress`, `Decisions`, `Risks`, and `Lessons Learned` up to date as work advances.

## Purpose / Big Picture

Implementar a simplificação oficial de `metadata.yml` para runtime extension do `seed4j-cli`, removendo campos legados (`distribution.vendor`, `distribution.kind`, `artifact.filename`) e mantendo apenas contrato funcional (`distribution.id`, `distribution.version`, `compatibility.min-cli-version` opcional).

O resultado observável para quem opera a CLI será: metadata mínima válida com apenas `distribution.id` e `distribution.version`; validação de compatibilidade executada somente quando `compatibility.min-cli-version` existir; e runtime extension funcionando mesmo com campos legados extras presentes.

## Scope

Em escopo:

- Atualizar parser/modelo de metadata para o novo contrato oficial.
- Tornar `compatibility.min-cli-version` opcional e condicional na validação.
- Remover dependência de runtime em `distribution.vendor`, `distribution.kind` e `artifact.filename`.
- Ajustar mensagens de erro para o novo contrato.
- Atualizar testes unitários/integrados/fixtures para refletir o novo comportamento.

Fora de escopo:

- Remover `metadata.yml` do fluxo.
- Derivar `distribution.id` ou `distribution.version` de Manifest/pom.
- Alterar convenção física de `extension.jar`.
- Suporte a múltiplos artefatos por runtime.

## Definitions

- Contract field: campo que participa da decisão de runtime e validação.
- Legacy extra field: campo ainda presente em arquivos antigos, mas sem efeito no comportamento.
- Compatibility gate: validação de versão mínima da CLI.
- Vertical slice: incremento ponta-a-ponta com comportamento observável e testes de regressão.

## Existing Context

### Produção

- `RuntimeMetadata` exige hoje: `distribution.id`, `distribution.version`, `distribution.vendor`, `distribution.kind`, `artifact.filename`, `compatibility.cli`.
  - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/RuntimeMetadata.java`
- `RuntimeSelection.resolve(...)` valida explicitamente:
  - `distribution.kind == extension`
  - `artifact.filename == <jar selecionado>`
  - `compatibility.cli` obrigatório e comparado contra versão atual.
  - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/RuntimeSelection.java`
- `CliVersion` expõe semântica e mensagens acopladas ao nome legado `compatibility.cli`.
  - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/CliVersion.java`

### Testes/fixtures

- Forte cobertura atual em `RuntimeSelectionTest`, mas centrada no contrato antigo.
  - Arquivo: `src/test/java/com/seed4j/cli/bootstrap/domain/RuntimeSelectionTest.java`
- Fixtures de runtime extension ainda carregam campos legados.
  - Arquivo: `src/test/resources/runtime/extension/metadata.yml`
  - Arquivo: `src/test/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncherTest.java`

## Desired End State

- `metadata.yml` oficial aceito com:
  - obrigatório: `distribution.id`, `distribution.version`
  - opcional: `compatibility.min-cli-version`
- `distribution.vendor` removido do contrato oficial e do modelo interno.
- Campos legados extras podem existir sem impacto:
  - `distribution.kind`
  - `artifact.filename`
  - `distribution.vendor` (ignorado, sem validação)
  - `compatibility.cli`
- Compatibilidade só é validada quando `compatibility.min-cli-version` estiver presente.
- Mensagens de erro não mencionam campos removidos do contrato oficial.

## Vertical Slices

### Slice 1 - Parser e modelo no novo contrato funcional

#### Goal

Reduzir o parser/modelo ao contrato oficial, mantendo tolerância a campos extras.

#### Changes

- [ ] Atualizar `RuntimeMetadata` para carregar apenas:
  - [ ] `distribution.id` (obrigatório)
  - [ ] `distribution.version` (obrigatório)
  - [ ] `compatibility.min-cli-version` (opcional)
- [ ] Remover do modelo interno campos sem papel ativo (`vendor`, `kind`, `artifactFilename`, `compatibilityCli`).
- [ ] Garantir que mapas extras no YAML sejam ignorados silenciosamente.

#### Validation

- [ ] Command: `./mvnw -Dtest=RuntimeSelectionTest test`
- [ ] Expected result: testes de parser/contrato passam com novo esquema.

#### Acceptance Criteria

- [ ] Metadata mínima (id + version) parseia com sucesso.
- [ ] Ausência de `compatibility` ou de `min-cli-version` não gera erro.
- [ ] Campos legados extras não alteram resultado.
- [ ] `distribution.vendor` não é mais obrigatório e não participa do modelo.

### Slice 2 - Runtime selection sem dependências estruturais

#### Goal

Eliminar validações estruturais redundantes e manter seleção extension funcional.

#### Changes

- [ ] Remover de `RuntimeSelection.resolve(...)` a validação de `distribution.kind`.
- [ ] Remover de `RuntimeSelection.resolve(...)` a validação de `artifact.filename`.
- [ ] Preservar validações de existência de `metadata.yml` e `extension.jar`.
- [ ] Preservar exposição de `distributionId` e `distributionVersion` para observabilidade.

#### Validation

- [ ] Command: `./mvnw -Dtest='RuntimeSelectionTest,Seed4JCliLauncherTest' test`
- [ ] Expected result: seleção extension continua funcionando sem depender dos campos removidos.

#### Acceptance Criteria

- [ ] Runtime extension sobe sem `distribution.kind` e sem `artifact.filename`.
- [ ] Erros não mencionam mais esses campos.

### Slice 3 - Compatibility gate opcional com `min-cli-version`

#### Goal

Aplicar compatibilidade somente quando declarada, com nome novo de campo.

#### Changes

- [ ] Ajustar leitura de compatibilidade para `compatibility.min-cli-version`.
- [ ] Ajustar `RuntimeSelection` para validar versão somente quando esse campo existir.
- [ ] Atualizar mensagens em `CliVersion` para o novo nome de campo.
- [ ] Manter regra de comparação atual (versão mínima, não igualdade exata).

#### Validation

- [ ] Command: `./mvnw -Dtest=RuntimeSelectionTest test`
- [ ] Expected result: cenários com e sem compatibilidade passam conforme regra condicional.

#### Acceptance Criteria

- [ ] Com `min-cli-version`, incompatibilidade falha.
- [ ] Sem `min-cli-version`, bootstrap não bloqueia por compatibilidade.
- [ ] Formato inválido de `min-cli-version` falha com mensagem clara.

### Slice 4 - Migração de fixtures e regressão E2E

#### Goal

Alinhar fixtures/tests de bootstrap ao novo contrato oficial sem regressões.

#### Changes

- [ ] Atualizar fixture compartilhada `src/test/resources/runtime/extension/metadata.yml` para formato novo.
- [ ] Atualizar metadata inline em `Seed4JCliLauncherTest` e demais testes para novo formato.
- [ ] Adicionar cenário explícito de "metadata com campos legados extras" garantindo efeito nulo.
- [ ] Revisar testes in-process/packaged para manter observabilidade de distribuição.

#### Validation

- [ ] Command: `./mvnw -Dtest='RuntimeSelectionTest,Seed4JCliLauncherTest,ExtensionRuntimeFixtureTest,ExtensionRuntimeBootstrapInProcessTest' test`
- [ ] Expected result: cobertura de unidade/in-process verde.
- [ ] Command: `./mvnw -Dit.test='ExtensionRuntimeBootstrapPackagedJarIT,ExtensionRuntimeBootstrapListPackagedJarIT' failsafe:integration-test failsafe:verify`
- [ ] Expected result: bootstrap empacotado extension continua funcional.

#### Acceptance Criteria

- [ ] `--version` continua exibindo `Distribution ID` e `Distribution version` corretos.
- [ ] Startup extension não depende de campos removidos.

### Slice 5 - Fechamento de contrato e limpeza de dívida imediata

#### Goal

Fechar a transição com rastreabilidade de regra oficial e eliminar resíduos de naming legado.

#### Changes

- [ ] Revisar strings de erro e asserts para remover referência a `compatibility.cli`.
- [ ] Atualizar documentos temporários que ainda registram contrato legado (quando usados como referência ativa).
- [ ] Executar validação completa.

#### Validation

- [ ] Command: `./mvnw clean verify`
- [ ] Expected result: build verde com surefire/failsafe/checkstyle/jacoco.

#### Acceptance Criteria

- [ ] Nenhum teste novo depende de `distribution.kind`/`artifact.filename`.
- [ ] Semântica opcional de compatibilidade está comprovada por teste.

## Required Test Matrix (from spec)

Valid scenarios:

- [ ] metadata só com `distribution.id` + `distribution.version`
- [ ] metadata com `distribution.*` + `compatibility.min-cli-version`
- [ ] metadata com campos legados extras sem efeito
- [ ] metadata sem `distribution.vendor` continua válida
- [ ] CLI compatível quando campo de compatibilidade presente
- [ ] sem campo de compatibilidade => sem validação

Invalid scenarios:

- [ ] `metadata.yml` ausente
- [ ] `distribution.id` ausente
- [ ] `distribution.version` ausente
- [ ] `compatibility.min-cli-version` malformado (quando presente)
- [ ] versão CLI menor que mínimo

Regression scenarios:

- [ ] runtime extension funciona sem `distribution.kind`
- [ ] runtime extension funciona sem `artifact.filename`
- [ ] `distribution.id` e `distribution.version` seguem disponíveis para diagnóstico
- [ ] ausência de compatibilidade não bloqueia startup

## Open Points To Confirm Before Coding

1. Campo legado de compatibilidade (`compatibility.cli`):
   - A especificação permite suporte transitório opcional.
   - Decisão recomendada: não suportar alias por padrão para reduzir ambiguidade; se suportar, marcar como transicional e com prazo de remoção.

2. Regra de formato de versão:
   - Hoje a comparação aceita segmentos numéricos com sufixo opcional (`1.2.0-SNAPSHOT` -> `1.2.0`).
   - Decisão recomendada: manter essa regra para evitar regressão de comportamento.

3. Seção `compatibility` vazia:
   - Exemplo: `compatibility: {}`.
   - Decisão recomendada: tratar como ausência de `min-cli-version` (skip de validação), não como erro.

## Progress

- [x] Levantamento de impacto no código e testes concluído
- [ ] Slice 1 started
- [ ] Slice 1 completed
- [ ] Slice 2 started
- [ ] Slice 2 completed
- [ ] Slice 3 started
- [ ] Slice 3 completed
- [ ] Slice 4 started
- [ ] Slice 4 completed
- [ ] Slice 5 started
- [ ] Slice 5 completed

## Decisions

- Decision: organizar a entrega por slices verticais com comportamento observável.
  Rationale: reduz risco de regressão e mantém validação contínua por cenário.
  Date/Author: 2026-03-18 / Codex

- Decision: tratar campos legados como extras ignorados no parser, não como parte do modelo.
  Rationale: mantém migração barata sem perpetuar dívida no domínio.
  Date/Author: 2026-03-18 / Codex

- Decision: `distribution.vendor` será removido do contrato, do parser obrigatório e do modelo interno.
  Rationale: o contrato oficial contém apenas identidade (`distribution.id`, `distribution.version`) e compatibilidade opcional.
  Date/Author: 2026-03-18 / Codex

## Risks and Mitigations

- Risk: remover campos do record `RuntimeMetadata` quebra vários testes de uma vez.
  Mitigation: aplicar primeiro alteração de parser/modelo com testes focados (`RuntimeSelectionTest`) antes de mexer nos ITs.

- Risk: ambiguidade de suporte transitório para `compatibility.cli` gerar comportamento inesperado.
  Mitigation: documentar decisão explícita antes do merge e cobrir por testes de regressão.

- Risk: mensagens de erro antigas continuarem referenciadas em asserts.
  Mitigation: atualizar asserts por intenção (campo/causa), não por texto legado literal.

## Validation Strategy

1. Rodar `RuntimeSelectionTest` a cada alteração de parser/compatibilidade.
2. Rodar `Seed4JCliLauncherTest` após ajustes de fixtures inline.
3. Rodar `ExtensionRuntimeFixtureTest` + `ExtensionRuntimeBootstrapInProcessTest` para cobrir fluxo integrado.
4. Rodar ITs empacotados de extension.
5. Rodar `./mvnw clean verify` como gate final.

## Rollout and Recovery

- Rollout: merge normal com contrato oficial simplificado e testes verdes.
- Recovery: reverter somente alterações de parser/selection caso haja regressão crítica, mantendo evidências de teste para reintrodução incremental.

## Lessons Learned

- O código atual ainda acopla regras estruturais no parser/selection; a simplificação exige mudança simultânea de modelo, validação e fixture para manter coerência.
