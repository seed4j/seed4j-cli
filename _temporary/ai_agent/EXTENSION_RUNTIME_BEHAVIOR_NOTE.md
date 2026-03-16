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

## Conclusão prática

`extension mode` hoje funciona como **overlay/augmentação**.
Se o objetivo for modo **extension-only** (ignorar módulos implícitos do core), será necessário alterar a estratégia de bootstrap/classpath e/ou aplicar filtragem explícita dos módulos base.
