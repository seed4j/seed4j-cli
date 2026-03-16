# Extension Runtime: gerar `extension.jar` como base `seed4j` + módulo extra e validar catálogo sem duplicatas

This ExecPlan is a living document. Keep `Progress`, `Decisions`, `Risks`, and `Lessons Learned` up to date as work advances.

## Purpose / Big Picture

Hoje o fixture de runtime extension cria um `extension.jar` contendo apenas as classes do módulo de teste `runtime-extension-list-only`. A mudança proposta valida um cenário mais próximo de distribuição real: o `extension.jar` passa a ser gerado a partir do `seed4j.jar` de dependência (definida no `pom.xml`) e depois recebe apenas o módulo extra. O comportamento observável esperado é que, ao rodar `seed4j list`, o modo extension mantenha todos os módulos do modo standard, adicione apenas `runtime-extension-list-only`, e não apresente slugs duplicados.

## Scope

In-scope:

- Alterar o fixture de testes para gerar `extension.jar` por cópia do jar de dependência `com.seed4j:seed4j`.
- Adicionar/ajustar testes para validar diferença de catálogo entre modo standard e extension.
- Garantir assertions de ausência de duplicatas e de adição exclusiva do módulo extra.

Out-of-scope:

- Alterar comportamento de produção fora da camada de teste.
- Redesenhar semântica de runtime mode (`standard` vs `extension`) para modo substitutivo.
- Otimizações de performance de build não relacionadas ao cenário.

## Definitions

- `extension.jar`: artefato de runtime extension localizado em `~/.config/seed4j-cli/runtime/active/extension.jar`.
- Modo `standard`: execução sem configuração `seed4j.runtime.mode: extension`.
- Modo `extension`: execução com `seed4j.runtime.mode: extension` e metadados/artefato válidos.
- Slug de módulo: identificador textual exibido pelo comando `seed4j list` (coluna 1 da lista de módulos).
- Módulo extra: módulo de teste `runtime-extension-list-only`.
- Catálogo de módulos: conjunto de slugs retornados por `seed4j list`.

## Existing Context

O cenário atual está distribuído nestes pontos:

- `ExtensionRuntimeFixture.installWithListExtensionModule(...)` usa `createListExtensionModuleJar(...)` para gerar jar de extensão.
- `createListExtensionModuleJar(...)` atualmente grava apenas classes de `LIST_EXTENSION_MODULE_CLASSES` no jar.
- O teste empacotado `ExtensionRuntimeBootstrapListPackagedJarIT` já valida presença/ausência do slug extra entre modos, mas não valida diffs completos de catálogo nem duplicação.
- O launcher em runtime extension injeta `loader.path` com `extension.jar`, mantendo o jar principal do CLI no classpath.

Arquivos relevantes:

- `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeFixture.java`
- `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeBootstrapListPackagedJarIT.java`
- `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeFixtureTest.java`

## Desired End State

Após a implementação:

- O fixture de list extension gera `extension.jar` como cópia do `seed4j.jar` de dependência e adiciona classes do módulo extra.
- O teste empacotado compara os catálogos standard vs extension e garante:
- O catálogo extension contém todos os slugs do standard.
- O catálogo extension adiciona exatamente um slug: `runtime-extension-list-only`.
- Não existem slugs repetidos em nenhuma execução.
- Os testes unitários do fixture comprovam que o jar gerado contém conteúdo base do `seed4j.jar` e conteúdo do módulo extra.

## Milestones

### Milestone 1 - Gerar `extension.jar` como base do `seed4j.jar`

#### Goal

Trocar o mecanismo de geração do jar de extensão no fixture para copiar o jar de dependência e acrescentar o módulo extra, evitando entradas duplicadas de jar.

#### Changes

- [x] Editar `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeFixture.java` para resolver o path do `seed4j.jar` de dependência via `com.seed4j.Seed4JApp` (`CodeSource`).
- [x] Copiar entradas do `seed4j.jar` para o `extension.jar` novo (preservando jar válido) e depois adicionar classes de `LIST_EXTENSION_MODULE_CLASSES`.
- [x] Reusar `Set<String>` para impedir escrita duplicada de entradas no jar final.
- [x] Manter `install(Path userHome)` com comportamento atual de jar mínimo para não alterar cenários não relacionados.

#### Validation

- [x] Command: `./mvnw -Dtest=ExtensionRuntimeFixtureTest test`
- [x] Expected result: testes de fixture passam, incluindo os cenários antigos e os novos de conteúdo do jar.

#### Acceptance Criteria

- [x] `installWithListExtensionModule(...)` gera um `extension.jar` que contém entradas do `seed4j.jar` base.
- [x] O mesmo `extension.jar` contém classes do módulo `runtime-extension-list-only`.
- [x] O jar final não contém entradas de nome duplicado.

### Milestone 2 - Validar catálogo standard vs extension no teste empacotado

#### Goal

Fortalecer o teste de bootstrap empacotado para validar diferença de catálogo por slug, garantindo adição exclusiva do módulo extra sem repetição.

#### Changes

- [x] Editar `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeBootstrapListPackagedJarIT.java` para incluir teste de comparação de conjuntos de slugs.
- [x] Implementar helper local para extrair slugs de `output` de `seed4j list`.
- [x] Adicionar assertions de:
- [x] ausência de duplicatas em standard e extension.
- [x] `extension - standard == {runtime-extension-list-only}`.
- [x] `standard - extension == {}`.

#### Validation

- [x] Command: `./mvnw -Dit.test=ExtensionRuntimeBootstrapListPackagedJarIT failsafe:integration-test failsafe:verify`
- [x] Expected result: teste empacotado passa com diferença de catálogo estritamente controlada.

#### Acceptance Criteria

- [x] Em modo standard, o slug `runtime-extension-list-only` não aparece.
- [x] Em modo extension, o slug `runtime-extension-list-only` aparece.
- [x] Somente esse slug é adicionado ao comparar os dois catálogos.
- [x] Nenhum slug duplicado aparece em qualquer modo.

### Milestone 3 - Consolidação de validação e estabilidade

#### Goal

Executar validação completa do repositório para confirmar que a mudança não regrediu o restante da suíte.

#### Changes

- [x] Rodar validações focadas antes da suíte completa.
- [x] Rodar `clean verify` e registrar resultado no plano.

#### Validation

- [x] Command: `./mvnw clean verify`
- [x] Expected result: build verde com surefire/failsafe/checkstyle/jacoco.

#### Acceptance Criteria

- [x] Nenhuma regressão fora dos testes alterados.
- [x] Evidência observável de que o comportamento final está conforme objetivo.

## Progress

- [x] Milestone 1 started
- [x] Milestone 1 completed
- [x] Milestone 2 started
- [x] Milestone 2 completed
- [x] Milestone 3 started
- [x] Milestone 3 completed

## Decisions

- Decision: tratar `extension` como modo aditivo para catálogo (manter baseline e adicionar apenas módulo extra).
  Rationale: alinha com comportamento atual do launcher e com o objetivo do teste solicitado (comparação standard vs extension por diferença mínima).
  Date/Author: 2026-03-16 / Codex

- Decision: resolver o `seed4j.jar` de dependência via `CodeSource` de `com.seed4j.Seed4JApp`, e não por path fixo de `~/.m2`.
  Rationale: reduz acoplamento ao layout local de repositório Maven e usa classpath real do teste.
  Date/Author: 2026-03-16 / Codex

- Decision: ao copiar entradas do `seed4j.jar`, ignorar `config/application.yml` no `extension.jar` do fixture.
  Rationale: essa configuração do artefato base redefine `seed4j.hidden-resources.slugs` e escondia módulos no modo extension; sem essa entrada, o catálogo extension volta a ser aditivo em relação ao standard.
  Date/Author: 2026-03-16 / Codex

## Risks and Mitigations

- Risk: formato de parsing de `seed4j list` ficar frágil a mudanças de texto.
  Mitigation: parsear apenas linhas de módulos por padrão estrutural (indentação + separação por espaços), sem depender da mensagem de cabeçalho.

- Risk: cópia de jar introduzir colisões de entrada ou problemas de manifest.
  Mitigation: usar `Set<String>` para deduplicação e manter criação de jar com manifest válido.

- Risk: depender de uma classe específica para resolver o jar de base falhar em ambiente incomum.
  Mitigation: validar explicitamente o path resolvido e falhar com mensagem clara de diagnóstico no fixture.

## Validation Strategy

1. Rodar testes focados do fixture para validar montagem de `extension.jar`.
2. Rodar IT focado para validar catálogo standard vs extension.
3. Rodar validação completa `./mvnw clean verify`.
4. Confirmar resultado observável: diff de slugs entre modos é exatamente o slug extra e sem duplicatas.

## Rollout and Recovery

Como a mudança é em testes/fixture, rollout é via merge normal de branch. Se houver instabilidade:

1. Reverter apenas alterações do fixture e do IT introduzidos por este plano.
2. Reexecutar `./mvnw clean verify` para confirmar retorno ao baseline.
3. Reabrir com abordagem incremental (primeiro assertions de catálogo, depois mudança de geração do jar).

## Lessons Learned

- Rodar `failsafe:integration-test` isolado não recompila/empacota artefatos por si só; para mudanças em ITs empacotados, garantir compilação e existência do `target/seed4j-cli-*.jar` antes da execução focada.
- Copiar um jar de dependência para `loader.path` pode impactar precedência de recursos de configuração além de classes; fixtures de runtime extension precisam controlar entradas de configuração para evitar alterações indiretas no catálogo.
