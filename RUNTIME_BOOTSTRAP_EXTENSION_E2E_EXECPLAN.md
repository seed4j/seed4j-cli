# Runtime Bootstrap Extension: E2E + Coverage Hibrido

Este ExecPlan e um documento vivo. Mantenha `Progress`, `Decisions`, `Risks` e `Lessons Learned` atualizados durante a execucao.

## Purpose / Big Picture

Garantir que o `seed4j-cli` execute com `extension.jar` no fluxo real de bootstrap e, ao mesmo tempo, que esse esforco ajude no code coverage (evitando testes unitarios redundantes). O resultado final deve ter dois niveis de confianca: um teste de integracao no mesmo JVM para cobertura efetiva e um smoke test externo para contrato real empacotado.

## Scope

Em escopo:

- Criar teste de integracao in-process para modo `extension` com propagacao de propriedades de runtime.
- Criar smoke test externo automatizado no ciclo `verify` para validar `java -jar` com `extension.jar`.
- Versionar fixture de metadata e gerar `extension.jar` temporario no teste.
- Manter o contrato operacional suportado: execucao empacotada por `java -jar`.

Fora de escopo:

- Suporte especial para inicializacao manual com classpath multiplo (`java -cp a.jar:b.jar ...`).
- Campos novos de metadata (`runtime-version`, `bootstrap-class`, `runtime-contract-version`).
- Mudancas de naming (`standard` -> `base`) e handshake de contrato de runtime.

## Definitions

- Parent JVM: processo inicial do CLI que decide runtime e prepara o child.
- Child JVM: processo efetivo que executa o Spring CLI com propriedades de runtime propagadas.
- In-process integration: teste que simula o comportamento do child dentro do mesmo JVM do teste para ganhar cobertura JaCoCo.
- External smoke E2E: teste que sobe o jar empacotado em processo externo para validar contrato real de execucao.

## Existing Context

- `Seed4JCliApp` delega bootstrap para `Seed4JCliLauncher`.
- `Seed4JCliLauncher` resolve runtime (`standard`/`extension`) e monta `JavaChildProcessRequest` com:
  - `seed4j.cli.runtime.child=true`
  - `seed4j.cli.runtime.mode`
  - `seed4j.cli.runtime.distribution.id`
  - `seed4j.cli.runtime.distribution.version`
  - `loader.path` (quando extension)
- `JavaProcessChildLauncher` executa child real via `ProcessBuilder`.
- A suite atual ja cobre contratos de launcher e providers, mas ainda falta um E2E automatizado focado em `extension.jar`.
- Cobertura JaCoCo atual vem principalmente de testes no mesmo JVM; processo externo nao contribui automaticamente.

## Desired End State

- Existe um teste de integracao que cobre comportamento principal de runtime extension no mesmo JVM (com cobertura).
- Existe um smoke IT externo no `verify` que valida o contrato real `java -jar ... --version` em modo extension.
- `./mvnw clean verify` continua verde.
- O teste in-process reduz necessidade de unitarios redundantes para os mesmos comportamentos observaveis.

## Milestones

### Milestone 1 - Fixtures de runtime extension para testes

#### Goal

Criar insumos padronizados para testar modo extension de forma repetivel.

#### Changes

- [ ] Adicionar metadata fixture versionada em `src/test/resources` (arquivo dedicado para runtime extension).
- [ ] Definir helper de teste para criar `extension.jar` temporario minimo (jar valido).
- [ ] Garantir que fixture contenha `artifact.filename: extension.jar`, `distribution.kind: extension` e `compatibility.cli` compativel.

#### Validation

- [ ] Command: `./mvnw -Dtest='*Runtime*' test`
- [ ] Expected result: fixtures de runtime sao consumiveis por testes sem erro de parsing/validacao.

#### Acceptance Criteria

- [ ] Metadata da fixture representa um runtime extension valido.
- [ ] JAR temporario minimo pode ser usado como `loader.path` sem falha estrutural.

### Milestone 2 - Integracao in-process com cobertura

#### Goal

Cobrir fluxo principal de extension no mesmo JVM do teste para ganho real de JaCoCo.

#### Changes

- [ ] Adicionar classe de integracao (ex.: `ExtensionRuntimeBootstrapIntegrationTest`) em `src/test/java`.
- [ ] Instanciar `Seed4JCliLauncher` real com `user.home` temporario e `executableJar` valido.
- [ ] Usar `ChildProcessLauncher` de teste que:
  - [ ] recebe `JavaChildProcessRequest`,
  - [ ] aplica `request.systemProperties()` temporariamente em `System`,
  - [ ] executa caminho local do CLI (runner Spring/picocli) no mesmo JVM,
  - [ ] restaura todas as propriedades apos execucao.
- [ ] Capturar saida de `--version` para validar identidade de runtime extension.

#### Validation

- [ ] Command: `./mvnw -Dtest='*ExtensionRuntimeBootstrapIntegrationTest' test`
- [ ] Expected result: `exitCode=0` e saida contendo runtime/distribution da extension fixture.

#### Acceptance Criteria

- [ ] Fluxo de extension e exercitado com componentes reais de bootstrap (launcher + resolucao + request).
- [ ] Teste contribui para coverage de codigo de producao no caminho de runtime extension.

### Milestone 3 - Smoke E2E externo automatizado

#### Goal

Validar contrato real de execucao empacotada sem passo manual.

#### Changes

- [ ] Adicionar classe `*IT*` (ex.: `ExtensionRuntimeBootstrapIT`) para Failsafe.
- [ ] No teste, localizar jar empacotado em `target/` (`seed4j-cli-*.jar`, ignorando `.jar.original`).
- [ ] Criar ambiente temporario (`user.home`) com config/metadata/jar de extension.
- [ ] Executar `java -Duser.home=<tmpHome> -jar <cliJar> --version` via `ProcessBuilder`.
- [ ] Validar `exitCode=0` e identidade de runtime extension na saida.

#### Validation

- [ ] Command: `./mvnw -Dtest='*ExtensionRuntimeBootstrapIT*' -DfailIfNoTests=false verify`
- [ ] Expected result: smoke E2E externo verde no ciclo Failsafe.

#### Acceptance Criteria

- [ ] Contrato real empacotado (`java -jar`) funciona em extension mode sem intervencao manual.
- [ ] Falha de bootstrap extension aparece como falha automatica no pipeline local/CI.

## Progress

- [ ] Milestone 1 started
- [ ] Milestone 1 completed
- [ ] Milestone 2 started
- [ ] Milestone 2 completed
- [ ] Milestone 3 started
- [ ] Milestone 3 completed

## Decisions

- Decision: usar estrategia hibrida (in-process para coverage + E2E externo para contrato real).
  Rationale: maximiza cobertura util sem abrir mao da validacao de execucao empacotada.
  Date/Author: 2026-03-16 / user+codex

- Decision: fixture de metadata versionada e `extension.jar` temporario.
  Rationale: evita binario versionado e mantem dados declarativos estaveis.
  Date/Author: 2026-03-16 / user+codex

- Decision: classpath multiplo manual (`java -cp ...`) fica fora de escopo.
  Rationale: contrato operacional suportado e `java -jar`.
  Date/Author: 2026-03-16 / user+codex

## Risks and Mitigations

- Risk: teste in-process divergir do child real.
  Mitigation: manter smoke E2E externo cobrindo contrato real empacotado.

- Risk: manipular `System.setProperty` causar interferencia entre testes.
  Mitigation: snapshot/restauracao completa das chaves alteradas e isolamento por teste.

- Risk: instabilidade ao localizar jar em `target/`.
  Mitigation: regra de selecao deterministica com mensagem de erro clara quando ambiguo.

## Validation Strategy

1. Rodar teste focado da integracao in-process.
2. Rodar teste focado do smoke E2E externo.
3. Rodar validacao completa do repositorio:
   - `./mvnw clean verify`
4. Confirmar observabilidade:
   - saida `--version` mostra `Runtime mode: extension`, `Distribution ID`, `Distribution version`.

## Rollout and Recovery

- Rollout: merge normal no fluxo atual; sem alteracao de contrato publico.
- Recovery: se testes ficarem flaky, desabilitar temporariamente apenas o smoke externo com anotacao de skip justificando causa, mantendo integracao in-process ativa ate corrigir.

## Lessons Learned

- Cobertura JaCoCo em processo externo nao vem automaticamente; para ganho pratico de coverage, manter teste principal no mesmo JVM.
- Smoke externo deve ser curto e focado em contrato, nao em fechar cobertura de todas as branches.
