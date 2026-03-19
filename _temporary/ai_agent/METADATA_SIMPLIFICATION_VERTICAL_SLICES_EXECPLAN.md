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

- [x] Atualizar `RuntimeMetadata` para carregar apenas:
  - [x] `distribution.id` (obrigatório)
  - [x] `distribution.version` (obrigatório)
  - [x] `compatibility.min-cli-version` (opcional)
- [x] Remover do modelo interno campos sem papel ativo (`vendor`, `kind`, `artifactFilename`, `compatibilityCli`).
- [x] Garantir que mapas extras no YAML sejam ignorados silenciosamente.

#### Validation

- [x] Command: `./mvnw -Dtest=RuntimeSelectionTest test`
- [x] Expected result: testes de parser/contrato passam com novo esquema.

#### Acceptance Criteria

- [x] Metadata mínima (id + version) parseia com sucesso.
- [x] Ausência de `compatibility` ou de `min-cli-version` não gera erro.
- [x] Campos legados extras não alteram resultado.
- [x] `distribution.vendor` não é mais obrigatório e não participa do modelo.

### Slice 2 - Runtime selection sem dependências estruturais

#### Goal

Eliminar validações estruturais redundantes e manter seleção extension funcional.

#### Changes

- [x] Remover de `RuntimeSelection.resolve(...)` a validação de `distribution.kind`.
- [x] Remover de `RuntimeSelection.resolve(...)` a validação de `artifact.filename`.
- [x] Preservar validações de existência de `metadata.yml` e `extension.jar`.
- [x] Preservar exposição de `distributionId` e `distributionVersion` para observabilidade.

#### Validation

- [x] Command: `./mvnw -Dtest='RuntimeSelectionTest,Seed4JCliLauncherTest' test`
- [x] Expected result: seleção extension continua funcionando sem depender dos campos removidos.

#### Acceptance Criteria

- [x] Runtime extension sobe sem `distribution.kind` e sem `artifact.filename`.
- [x] Erros não mencionam mais esses campos.

### Slice 3 - Compatibility gate opcional com `min-cli-version`

#### Goal

Aplicar compatibilidade somente quando declarada, com nome novo de campo.

#### Changes

- [x] Ajustar leitura de compatibilidade para `compatibility.min-cli-version`.
- [x] Ajustar `RuntimeSelection` para validar versão somente quando esse campo existir.
- [x] Atualizar mensagens em `CliVersion` para o novo nome de campo.
- [x] Manter regra de comparação atual (versão mínima, não igualdade exata).

#### Validation

- [x] Command: `./mvnw -Dtest=RuntimeSelectionTest test`
- [x] Expected result: cenários com e sem compatibilidade passam conforme regra condicional.

#### Acceptance Criteria

- [x] Com `min-cli-version`, incompatibilidade falha.
- [x] Sem `min-cli-version`, bootstrap não bloqueia por compatibilidade.
- [x] Formato inválido de `min-cli-version` falha com mensagem clara.

### Slice 4 - Migração de fixtures e regressão E2E

#### Goal

Alinhar fixtures/tests de bootstrap ao novo contrato oficial sem regressões.

#### Changes

- [x] Atualizar fixture compartilhada `src/test/resources/runtime/extension/metadata.yml` para formato novo.
- [x] Atualizar metadata inline em `Seed4JCliLauncherTest` e demais testes para novo formato.
- [x] Adicionar cenário explícito de "metadata com campos legados extras" garantindo efeito nulo.
- [x] Revisar testes in-process/packaged para manter observabilidade de distribuição.

#### Validation

- [x] Command: `./mvnw -Dtest='RuntimeSelectionTest,Seed4JCliLauncherTest,ExtensionRuntimeFixtureTest,ExtensionRuntimeBootstrapInProcessTest' test`
- [x] Expected result: cobertura de unidade/in-process verde.
- [x] Command: `./mvnw -Dit.test='ExtensionRuntimeBootstrapPackagedJarIT,ExtensionRuntimeBootstrapListPackagedJarIT' failsafe:integration-test failsafe:verify`
- [x] Expected result: bootstrap empacotado extension continua funcional.

#### Acceptance Criteria

- [x] `--version` continua exibindo `Distribution ID` e `Distribution version` corretos.
- [x] Startup extension não depende de campos removidos.

### Slice 5 - Fechamento de contrato e limpeza de dívida imediata

#### Goal

Fechar a transição com rastreabilidade de regra oficial e eliminar resíduos de naming legado.

#### Changes

- [x] Revisar strings de erro e asserts para remover referência a `compatibility.cli`.
- [x] Atualizar documentos temporários que ainda registram contrato legado (quando usados como referência ativa).
- [x] Executar validação completa.

#### Validation

- [x] Command: `./mvnw clean verify`
- [x] Expected result: build verde com surefire/failsafe/checkstyle/jacoco.

#### Acceptance Criteria

- [x] Nenhum teste novo depende de `distribution.kind`/`artifact.filename`.
- [x] Semântica opcional de compatibilidade está comprovada por teste.

## Required Test Matrix (from spec)

Valid scenarios:

- [x] metadata só com `distribution.id` + `distribution.version`
- [x] metadata com `distribution.*` + `compatibility.min-cli-version`
- [x] metadata com campos legados extras sem efeito
- [x] metadata sem `distribution.vendor` continua válida
- [x] CLI compatível quando campo de compatibilidade presente
- [x] sem campo de compatibilidade => sem validação

Invalid scenarios:

- [x] `metadata.yml` ausente
- [x] `distribution.id` ausente
- [x] `distribution.version` ausente
- [x] `compatibility.min-cli-version` malformado (quando presente)
- [x] versão CLI menor que mínimo

Regression scenarios:

- [x] runtime extension funciona sem `distribution.kind`
- [x] runtime extension funciona sem `artifact.filename`
- [x] `distribution.id` e `distribution.version` seguem disponíveis para diagnóstico
- [x] ausência de compatibilidade não bloqueia startup

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
- [x] Slice 1 started
- [x] Slice 1 completed
- [x] Slice 2 started
- [x] Slice 2 completed
- [x] Slice 3 started
- [x] Slice 3 completed
- [x] Slice 4 started
- [x] Slice 4 completed
- [x] Slice 5 started
- [x] Slice 5 completed

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

- Decision: a validação de compatibilidade passou a ser condicional no `RuntimeSelection` para viabilizar metadata mínima já no Slice 1.
  Rationale: o critério de aceite do Slice 1 exige ausência de erro sem seção `compatibility`.
  Date/Author: 2026-03-18 / Codex

- Decision: mensagens de erro de compatibilidade em `CliVersion` foram migradas para `compatibility.min-cli-version`.
  Rationale: alinhar diagnóstico de erro ao novo contrato oficial e evitar referências legadas em asserts.
  Date/Author: 2026-03-18 / Codex

- Decision: testes packaged devem rodar com JAR repackaged atualizado no `target/`.
  Rationale: os ITs packaged executam o artefato físico; sem `package` recente, validam comportamento obsoleto.
  Date/Author: 2026-03-19 / Codex

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
- ITs packaged dependem do artefato em `target/`; sem rebuild (`package`), podem reportar falso negativo por executar JAR antigo.
- A opcionalidade de `compatibility.min-cli-version` precisa de testes explícitos para valores blank/não-string, senão o gate de cobertura falha em `RuntimeMetadata`.
