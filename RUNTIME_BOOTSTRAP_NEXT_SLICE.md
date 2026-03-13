# Runtime Bootstrap Next Slice

## Objective

Concluir o bootstrap em dois estágios no caminho de produção, com `Seed4JCliApp` como composition root enxuto e `Seed4JCliLauncher` como orquestrador.

## Decisions Locked (2026-03-13)

- Estratégia de testes: **alto nível primeiro**.
- Fonte principal de confiança para regra de bootstrap: `src/test/java/com/seed4j/cli/bootstrap/Seed4JCliLauncherTest.java`.
- Evitar testes por classe extraída quando o comportamento já está visível no teste de launcher.
- Handoff do processo filho (`java -D... -cp ... PropertiesLauncher`) deve ser validado no teste de launcher, não com matriz detalhada por implementação.
- Comando padrão de validação do ciclo: `./mvnw clean verify`.

## Current Status

### Done

- `Seed4JCliApp` já não usa camadas de fábrica/dependência intermediárias.
- `LocalSpringCliRunner` foi extraído para o caminho local in-process.
- `JavaProcessChildLauncher` foi extraído para montagem do comando de child process.
- `RuntimeModeConfigReader` foi extraído e `Seed4JCliLauncher` deixou de fazer parsing YAML diretamente.
- Cenários centrais de política de runtime seguem cobertos por `Seed4JCliLauncherTest`.
- Estado atual validado com `./mvnw clean verify` verde.

### Missing

- `Seed4JCliApp.main(String[] args)` ainda inicia Spring direto e não usa `Seed4JCliLauncher` no caminho de produção.
- `JavaProcessChildLauncher` ainda não executa processo real com `ProcessBuilder`, `inheritIO` e `${java.home}/bin/java` por padrão.
- Versão atual da CLI ainda entra no launcher como string; falta resolvedor dedicado.

## Non-Negotiable Runtime Rules

- Execução empacotada em JAR é o caminho oficial do bootstrap em dois estágios.
- Fora de JAR regular:
  - `standard` executa local.
  - `extension` falha antes do Spring com erro claro.
- Nunca rebaixar `extension` para `standard` silenciosamente.
- Contrato de handoff continua baseado em system properties.

## Next Steps (Ordered)

### 1) Wire do entrypoint de produção

- Fazer `Seed4JCliApp.main(String[] args)` compor dependências de bootstrap e delegar ao launcher.
- Centralizar saída de processo em um único `ExitHandler`.
- Manter `Seed4JCliAppTest` enxuto: apenas forwarding de args e exit code.

### 2) Child process real

- Finalizar `JavaProcessChildLauncher` com execução via `ProcessBuilder`.
- Padrão de executável: `${java.home}/bin/java`.
- Preservar contrato de comando:
  - `-Dkey=value` da request
  - `-cp <current-boot-jar>`
  - `org.springframework.boot.loader.launch.PropertiesLauncher`
  - args originais
- Herdar stdio e retornar exit code do filho.

### 3) Resolvedor de versão da CLI

- Extrair tipo dedicado para resolver versão da CLI antes do Spring.
- Mover para esse tipo a responsabilidade por leitura/parsing e falhas.
- Falhar rápido com mensagem clara quando não for possível resolver a versão atual.

### 4) Limites de orquestração

- `Seed4JCliLauncher` decide apenas:
  - child vs parent
  - local vs relaunch
  - runtime selecionado
  - próximo colaborador a invocar
- Não recolocar boot de Spring dentro do launcher.

## Acceptance Criteria

- `Seed4JCliApp.main(String[] args)` usa fluxo do launcher em produção.
- `standard` fora de JAR executa local com aviso.
- `extension` fora de JAR falha antes de Spring.
- Execução empacotada dispara handoff para JVM filha com propriedades esperadas.
- `./mvnw clean verify` permanece verde.
