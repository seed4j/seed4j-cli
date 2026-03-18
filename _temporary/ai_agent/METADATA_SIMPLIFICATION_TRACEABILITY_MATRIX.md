# Metadata Simplification Traceability Matrix

Data: 2026-03-18

## Delta oficial vs baseline atual

| Tema                                     | Baseline atual                  | Especificação nova                       |
| ---------------------------------------- | ------------------------------- | ---------------------------------------- |
| Campo obrigatório `distribution.id`      | Sim                             | Sim                                      |
| Campo obrigatório `distribution.version` | Sim                             | Sim                                      |
| Campo obrigatório `distribution.vendor`  | Sim                             | Não (deve sair do contrato)              |
| Campo obrigatório `distribution.kind`    | Sim                             | Não (remover)                            |
| Campo obrigatório `artifact.filename`    | Sim                             | Não (remover)                            |
| Compatibilidade                          | `compatibility.cli` obrigatório | `compatibility.min-cli-version` opcional |
| Execução da validação de compatibilidade | Sempre                          | Somente quando campo opcional presente   |

## Impacto por arquivo (produção)

| Arquivo                                                               | Mudança necessária                                                                                    |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `src/main/java/com/seed4j/cli/bootstrap/domain/RuntimeMetadata.java`  | Remover campos legados do record, ler `compatibility.min-cli-version` opcional, ignorar extras        |
| `src/main/java/com/seed4j/cli/bootstrap/domain/RuntimeSelection.java` | Remover checks de `distribution.kind` e `artifact.filename`; validar compatibilidade condicionalmente |
| `src/main/java/com/seed4j/cli/bootstrap/domain/CliVersion.java`       | Trocar naming de erro de `compatibility.cli` para `compatibility.min-cli-version`                     |

## Impacto por arquivo (testes/fixtures)

| Arquivo                                                                                     | Mudança necessária                                                                                     |
| ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `src/test/java/com/seed4j/cli/bootstrap/domain/RuntimeSelectionTest.java`                   | Reescrever cenários que exigem campos removidos; adicionar cenários de opcionalidade e legado ignorado |
| `src/test/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncherTest.java`                  | Atualizar metadata inline para o novo contrato                                                         |
| `src/test/resources/runtime/extension/metadata.yml`                                         | Migrar para `distribution.id`, `distribution.version`, `compatibility.min-cli-version` opcional        |
| `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeFixtureTest.java`            | Validar que fixture segue resolvendo runtime com metadata simplificada                                 |
| `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeBootstrapInProcessTest.java` | Regressão funcional de observabilidade de distribution id/version                                      |
| `src/test/java/com/seed4j/cli/bootstrap/domain/ExtensionRuntimeBootstrapPackagedJarIT.java` | Regressão funcional no bootstrap extension com JAR empacotado                                          |

## Mapeamento de critérios de aceite

| Critério                                                    | Cobertura recomendada                                   |
| ----------------------------------------------------------- | ------------------------------------------------------- |
| Contrato oficial reduzido                                   | Parser/modelo + `RuntimeSelectionTest`                  |
| Runtime sem `distribution.kind`/`artifact.filename`         | `RuntimeSelectionTest` + launcher tests                 |
| Parser/modelo sem campos removidos                          | inspeção de tipo + testes de parsing                    |
| Compatibilidade somente quando presente                     | `RuntimeSelectionTest` (com e sem campo)                |
| Ausência de compatibilidade não bloqueia                    | `RuntimeSelectionTest`                                  |
| `distribution.id` e `distribution.version` para diagnóstico | `Seed4JCommandsFactoryTest` + extension bootstrap tests |
| Legado com campos extras ainda lido sem efeito              | novo teste explícito de metadata com extras             |

## Pontos de atenção

- `distribution.vendor` será removido do contrato, da validação e do modelo interno.
- Se for decidido suportar `compatibility.cli` temporariamente, isso deve ser marcado como transição, com teste dedicado e data de remoção.
- O parser atual falha quando um mapa obrigatório não existe; com compatibilidade opcional, a leitura de `compatibility` precisa mudar para fluxo não obrigatorio.
