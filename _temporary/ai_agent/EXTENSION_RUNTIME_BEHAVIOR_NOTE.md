# Constatação: extension mode é aditivo (não substitutivo)

Data: 2026-03-16

## Resumo

No comportamento atual do projeto, `seed4j.runtime.mode=extension` **não substitui** os módulos do core do `seed4j`.
Ele **adiciona** módulos extras via `extension.jar` ao classpath já existente do CLI empacotado.

## Evidências principais

1. O fixture cria um `extension.jar` separado em `~/.config/seed4j-cli/runtime/active/extension.jar`, contendo apenas classes do módulo extra de teste.
2. O launcher propaga `loader.path=<extension.jar>` para o processo filho.
3. O processo filho executa `PropertiesLauncher` com `-cp <seed4j-cli-*.jar>`.
4. O `PropertiesLauncher` monta classpath com:
   - entradas de `loader.path` (extensão), e também
   - classpath do próprio JAR raiz (`BOOT-INF/classes` + `BOOT-INF/lib`, incluindo `seed4j-2.2.0.jar`).
5. O `list` usa `Seed4JModulesApplicationService.resources()` sem filtro por runtime mode.
   Resultado: módulos base + módulo extra (`runtime-extension-list-only`) aparecem juntos.

## Why slugs do not duplicate

1. Even with core classes present in two JARs, classes with the same FQCN do not become two distinct module entities at runtime.
2. In seed4j itself, `Seed4JModulesResources` enforces slug uniqueness (`assertUniqueSlugs`) and throws `DuplicatedSlugException` if a duplicate exists.
3. So, if duplication were really happening, bootstrap/list would fail with an error, which is not what we observed.
4. What we saw earlier was a different effect: `seed4j.hidden-resources.slugs` from `config/application.yml` inside the copied JAR hid three modules. After skipping that entry in the fixture, the result became standard + `runtime-extension-list-only`, with no duplicates.

## Conclusão prática

`extension mode` hoje funciona como **overlay/augmentação**.
Se o objetivo for modo **extension-only** (ignorar módulos implícitos do core), será necessário alterar a estratégia de bootstrap/classpath e/ou aplicar filtragem explícita dos módulos base.
