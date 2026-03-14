# Runtime Bootstrap Next Slice

## Foco deste slice

Fechar a lacuna principal do caminho de produção: `Seed4JCliApp.main(String[] args)` ainda inicializa Spring diretamente, ignorando `Seed4JCliLauncher`.

## Problema atual (observável)

- Entrypoint de produção:
  - `Seed4JCliApp.main(String[] args)` chama `loadExternalConfigFile(createApplicationBuilder()).run(args)`.
  - Em seguida chama `System.exit(SpringApplication.exit(context))`.
- Consequência:
  - O fluxo principal não passa pela política de runtime já encapsulada em `Seed4JCliLauncher`.
  - O overload `main(String[], BootstrapEntryPoint, ExitHandler)` existe e é testável, mas não é usado no caminho real.

## Objetivo de comportamento

Quando o usuário executa a CLI pelo entrypoint público `Seed4JCliApp.main(String[] args)`, o processo deve sair com o código retornado por `Seed4JCliLauncher.launch(args)`.

## Fora de escopo neste slice

- Trocar implementação interna de `JavaProcessChildLauncher` para `ProcessBuilder`.
- Introduzir resolvedor dedicado de versão da CLI.
- Reabrir regras de seleção `standard`/`extension` já cobertas por `Seed4JCliLauncherTest`.

## Plano TDD (um comportamento por vez)

### Ciclo 1 - Entrypoint público delega ao bootstrap de produção

[TEST] `Seed4JCliApp.main(String[] args)` delega os mesmos argumentos para um `BootstrapEntryPoint` de produção.

- 🔴 Primeiro red esperado:
  - teste referencia um ponto de composição de produção ainda inexistente (falha de compilação).
- 🌱 Green mínimo:
  - introduzir ponto mínimo de composição para que o `main(String[] args)` use o mesmo caminho do overload testável.
- 🔴 Segundo red esperado:
  - compila, mas o teste falha porque os argumentos ainda não chegam ao bootstrap.
- 🌱 Green mínimo:
  - encaminhar `args` sem transformação.
- 🌀 Refactor:
  - remover duplicação entre os dois métodos `main(...)` sem criar camada nova.

### Ciclo 2 - Código de saída do processo é controlado pelo launcher

[TEST] `Seed4JCliApp.main(String[] args)` propaga para `System.exit(...)` o exit code retornado pelo bootstrap de produção.

- 🔴 Primeiro red esperado:
  - teste depende de seam explícito para `ExitHandler` no caminho de produção.
- 🌱 Green mínimo:
  - conectar caminho de produção ao mesmo `ExitHandler` já usado no overload de teste.
- 🔴 Segundo red esperado:
  - compila, mas o código de saída observado não corresponde ao retorno do bootstrap.
- 🌱 Green mínimo:
  - garantir `exitHandler.exit(bootstrapEntryPoint.launch(args))`.
- 🌀 Refactor:
  - consolidar wiring para manter `Seed4JCliApp` como composition root enxuto.

### Checkpoint vertical obrigatório

Após os ciclos 1 e 2, executar validação de ponta a ponta pelo caminho público:

- `./mvnw clean verify`

## Definição de pronto deste slice

- `Seed4JCliApp.main(String[] args)` não inicia Spring diretamente.
- Caminho de produção usa `Seed4JCliLauncher` como bootstrap entrypoint.
- `Seed4JCliAppTest` continua pequeno, cobrindo forwarding de `args` e propagação de exit code.
- Suite completa permanece verde com `./mvnw clean verify`.
