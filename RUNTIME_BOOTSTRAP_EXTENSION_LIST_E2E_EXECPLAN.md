# Runtime Bootstrap Extension List: Dual Mode E2E

This ExecPlan is a living document. Keep `Progress`, `Decisions`, `Risks`, and `Lessons Learned` up to date as work advances.

## Purpose / Big Picture

Entregar validacao E2E observavel para o comando `list` em dois modos de runtime do CLI: `standard` e `extension`. O comportamento esperado para o usuario final e simples de observar: um slug de modulo extra (`runtime-extension-list-only`) nao aparece em `standard` e aparece em `extension`. O objetivo e provar o contrato real de bootstrap com `java -jar` e `loader.path`, sem depender de outro repositorio.

## Scope

Em escopo:

- Novo ExecPlan para o slice `list` no bootstrap runtime extension.
- Novo teste E2E empacotado (Failsafe) que executa `list` em `standard` e em `extension`.
- Fixture de runtime extension capaz de gerar `extension.jar` com classes de modulo de teste.
- Definicao minima de modulo extension em codigo de teste seguindo o padrao `ModuleConfiguration -> ApplicationService -> ModuleFactory`.

Fora de escopo:

- Integracao com build dinamico de `seed4j-sample-extension`.
- Mudancas de comportamento em codigo de producao para `list`.
- Validacao de conteudo funcional do modulo extra (documentacao, arquivos gerados, apply flow).
- Comparacao snapshot completa da saida de `list`.

## Definitions

- Standard mode: runtime padrao do CLI quando `seed4j.runtime.mode` nao esta configurado como `extension` no `~/.config/seed4j-cli.yml`.
- Extension mode: runtime do CLI que injeta `loader.path` para carregar um `extension.jar` durante o bootstrap.
- Parent JVM: processo inicial que resolve runtime e dispara child process.
- Child JVM: processo efetivo que executa `PropertiesLauncher` com propriedades de runtime.
- Extension fixture jar: JAR temporario criado nos testes para simular extensao carregada por `loader.path`.
- Extension-only slug: slug de modulo usado apenas neste teste (`runtime-extension-list-only`) para validacao de presenca/ausencia entre modos.

## Existing Context

- `Seed4JCliLauncher` resolve runtime mode e cria `JavaChildProcessRequest` com `loader.path` apenas em extension mode.
  - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncher.java`
- `JavaProcessChildLauncher` sobe o child JVM com `-cp <cli-jar>` e propriedades de sistema ordenadas, incluindo `loader.path` quando presente.
  - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/JavaProcessChildLauncher.java`
- O comando `list` imprime recursos vindos de `Seed4JModulesApplicationService.resources()`.
  - Arquivo: `src/main/java/com/seed4j/cli/command/infrastructure/primary/ListModulesCommand.java`
- Ja existe E2E extension para `--version`, mas nao existe E2E dual-mode para `list`.
  - Arquivo: `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeBootstrapPackagedJarIT.java`
- O fixture atual de extension cria um JAR minimo vazio (manifest + estrutura valida), suficiente para validacao de metadata, mas sem beans/modulos extras.
  - Arquivo: `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeFixture.java`
- O padrao de definicao de modulo no seed4j usado como referencia e:
  - `infrastructure/primary/*ModuleConfiguration` define `@Bean Seed4JModuleResource`.
  - `application/*ApplicationService` delega para factory.
  - `domain/*ModuleFactory` monta `Seed4JModule`.
  - Referencia consultada: `com/seed4j/generator/server/hexagonaldocumentation/**` no repositorio `seed4j`.

## Desired End State

- Existe um IT empacotado para `list` que executa:
  - `standard`: sem config extension, slug `runtime-extension-list-only` ausente.
  - `extension`: com config + metadata + `extension.jar` com modulo de teste, slug presente.
- A fixture extension continua suportando testes existentes sem regressao.
- O comportamento e observavel pela propria saida do comando `list` e por asserts de teste automatizado.

## Milestones

### Milestone 1 - Definir modulo extension minimo para teste

#### Goal

Criar um modulo de teste carregavel por Spring em extension mode, seguindo o mesmo desenho arquitetural do seed4j.

#### Changes

- [x] Adicionar tipo de slug dedicado para `runtime-extension-list-only` (implementando `Seed4JModuleSlugFactory`) em pacote de teste.
- [x] Adicionar `ModuleFactory` de teste com `buildModule(Seed4JModuleProperties)` retornando modulo minimo (`moduleBuilder(properties).build()`).
- [x] Adicionar `ApplicationService` de teste delegando para factory.
- [x] Adicionar `@Configuration` de teste com `@Bean Seed4JModuleResource` usando `withoutProperties()`, `standalone()`, `tags(...)` e `factory(applicationService::buildModule)`.

#### Validation

- [x] Command: `./mvnw -Dtest='*ExtensionRuntimeFixtureTest' test`
- [x] Expected result: compilacao e execucao verdes com classes de modulo de teste disponiveis no classpath de teste.

#### Acceptance Criteria

- [x] O modulo extra de teste e definido por tipos separados (slug, factory, service, configuration).
- [x] A definicao segue o mesmo padrao estrutural do exemplo `hexagonaldocumentation`.

### Milestone 2 - Evoluir fixture para gerar extension.jar com classes de modulo

#### Goal

Permitir que o fixture de extension gere um jar carregavel que realmente injete modulo extra via `loader.path`.

#### Changes

- [x] Manter `ExtensionRuntimeFixture.install(Path userHome)` intacto para preservar comportamento atual.
- [x] Adicionar variante dedicada (ex.: `installWithListExtensionModule(Path userHome)`) retornando os mesmos paths de fixture.
- [x] Implementar criacao de jar que copia bytes de classes de teste do modulo extension para entradas `.class` no `extension.jar` (incluindo classes internas quando existirem).
- [x] Garantir que metadata e config extension atuais continuam sendo reutilizados (`distribution.kind: extension`, `artifact.filename: extension.jar`).

#### Validation

- [x] Command: `./mvnw -Dtest='*ExtensionRuntimeFixtureTest' test`
- [x] Expected result: fixture extension segue valido e novo jar contem entradas de classe do modulo extra.

#### Acceptance Criteria

- [x] O `extension.jar` da variante nova e estruturalmente valido e contem classes do modulo extension de teste.
- [x] Nenhum teste existente dependente de `install(...)` antigo e quebrado.

### Milestone 3 - Teste E2E empacotado dual-mode para list

#### Goal

Validar no fluxo real `java -jar` que `list` muda apenas pela ativacao do extension runtime.

#### Changes

- [x] Adicionar novo IT Failsafe (ex.: `ExtensionRuntimeBootstrapListPackagedJarIT`) no pacote de bootstrap domain.
- [x] No teste, localizar deterministicamente o jar empacotado `target/seed4j-cli-*.jar` (ignorando `.jar.original`).
- [x] Preparar `user.home` para cenario `standard` (sem runtime extension configurado).
- [x] Preparar `user.home` para cenario `extension` usando fixture com modulo extra no `extension.jar`.
- [x] Executar `java -Duser.home=<tmpHome> -jar <cliJar> list` para ambos os cenarios.
- [x] Assert escolhido: slug `runtime-extension-list-only` ausente no output `standard` e presente no output `extension`.

#### Validation

- [x] Command: `./mvnw -Dtest='NoSuchTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*ExtensionRuntimeBootstrapListPackagedJarIT*' -DfailIfNoTests=false verify`
- [x] Expected result: IT verde, com diferenca observavel entre saidas de `list` por modo de runtime.

#### Acceptance Criteria

- [x] O comportamento dual-mode e comprovado no contrato empacotado real (`java -jar`).
- [x] O modulo extra aparece somente no extension mode.

## Progress

- [x] Milestone 1 started
- [x] Milestone 1 completed
- [x] Milestone 2 started
- [x] Milestone 2 completed
- [x] Milestone 3 started
- [x] Milestone 3 completed

## Decisions

- Decision: validar esse slice com Packaged IT (modo real), sem depender de teste in-process para provar `loader.path`.
  Rationale: somente o fluxo empacotado reproduz o contrato real de bootstrap e injecao de classpath extension.
  Date/Author: 2026-03-16 / user+codex

- Decision: usar assert de slug exclusivo (presenca/ausencia), sem snapshot completo de saida.
  Rationale: reduz fragilidade por ordenacao ou variacao legitima de modulos base.
  Date/Author: 2026-03-16 / user+codex

- Decision: usar simulacao minima de modulo extension no proprio teste, sem acoplamento com `seed4j-sample-extension`.
  Rationale: mantem teste deterministico, rapido e autocontido, validando o mesmo mecanismo tecnico.
  Date/Author: 2026-03-16 / user+codex

- Decision: para o IT empacotado focado, filtrar Failsafe por `-Dit.test` e desabilitar falha de selecao no Surefire.
  Rationale: o teste depende de jar empacotado e precisa rodar no ciclo `verify`, nao no ciclo `test`.
  Date/Author: 2026-03-16 / user+codex

## Risks and Mitigations

- Risk: jar de fixture nao incluir todas as classes necessarias (ex.: tipos internos), causando falha de bootstrap.
  Mitigation: copiar explicitamente cada classe necessaria para o jar e validar entradas no teste de fixture.

- Risk: flakiness por selecao ambigua de jar em `target/`.
  Mitigation: manter regra deterministica de selecao e erro claro quando houver 0 ou >1 candidatos.

- Risk: regressao em testes existentes de extension runtime.
  Mitigation: preservar metodo `install(...)` atual e introduzir nova variante dedicada.

- Risk: falso positivo caso slug extra exista no baseline no futuro.
  Mitigation: usar slug altamente especifico (`runtime-extension-list-only`) e assert de ausencia em `standard`.

## Validation Strategy

1. Rodar validacao focada de fixture:
   - `./mvnw -Dtest='*ExtensionRuntimeFixtureTest' test`
2. Rodar novo IT de dual-mode `list`:
   - `./mvnw -Dtest='NoSuchTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*ExtensionRuntimeBootstrapListPackagedJarIT*' -DfailIfNoTests=false verify`
3. Rodar IT extension ja existente para regressao rapida:
   - `./mvnw -Dtest='NoSuchTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='*ExtensionRuntimeBootstrapPackagedJarIT*' -DfailIfNoTests=false verify`
4. Rodar validacao completa do repositorio:
   - `./mvnw clean verify`
5. Evidencia observavel esperada:
   - output `standard` de `list` sem `runtime-extension-list-only`.
   - output `extension` de `list` com `runtime-extension-list-only`.

## Rollout and Recovery

- Rollout: merge normal no fluxo atual, sem mudanca de contrato publico do CLI.
- Recovery: se houver instabilidade, isolar a causa no novo IT e aplicar skip temporario justificado somente nele, preservando os demais testes de bootstrap extension.

## Lessons Learned

- O teste in-process atual de bootstrap extension nao prova carregamento real de `loader.path`; o contrato real exige validacao empacotada.
- Um modulo de extensao pode ser validado com implementacao minima se respeitar o padrao de definicao `Configuration -> ApplicationService -> ModuleFactory`.
- Para exercitar IT empacotado isolado, o filtro deve priorizar Failsafe (`-Dit.test`) para evitar execucao antecipada no Surefire.
