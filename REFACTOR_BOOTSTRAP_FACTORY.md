# Refactor: encapsular launcher de child process no bootstrap

## Objetivo

Eliminar o warning de visibilidade sem tornar `JavaChildProcessRequest` público.

## Estratégia

Manter `JavaChildProcessRequest` e `JavaProcessChildLauncher` como detalhes internos do pacote `com.seed4j.cli.bootstrap` e expor apenas uma factory pública para montar o `Seed4JCliLauncher`.

## Passo a passo

1. Tornar `JavaProcessChildLauncher` package-private.
   - Arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/JavaProcessChildLauncher.java`
   - Ação: remover o modificador `public` da classe.

2. Criar uma factory pública no pacote `bootstrap` para montar o wiring de produção.
   - Sugestão de arquivo: `src/main/java/com/seed4j/cli/bootstrap/domain/Seed4JCliLauncherFactory.java`
   - Responsabilidade: instanciar internamente `JavaProcessChildLauncher` e `LocalSpringCliRunner`, e retornar `Seed4JCliLauncher` pronto para uso.

3. Definir a API da factory com tipos públicos.
   - A factory não deve expor `JavaChildProcessRequest` nem `JavaProcessChildLauncher` no contrato.
   - Use interfaces públicas próprias da factory (por exemplo, `CommandExecutor`) ou tipos JDK para receber dependências externas.

4. Mover o wiring que hoje está em `Seed4JCliApp` para a factory.
   - Arquivo: `src/main/java/com/seed4j/cli/Seed4JCliApp.java`
   - Ação: substituir o `new JavaProcessChildLauncher(...)` e `new LocalSpringCliRunner(...)` por uma chamada única à factory.

5. Manter testes de comportamento próximos do bootstrap.
   - Ajustar/adicionar testes da factory para garantir que:
     - o `Seed4JCliLauncher` é criado com dependências corretas;
     - o app não depende mais de classes internas de child launcher.

## Critérios de pronto

- `JavaProcessChildLauncher` não é mais público.
- `JavaChildProcessRequest` continua package-private.
- `Seed4JCliApp` não referencia `JavaProcessChildLauncher` diretamente.
- Build e suíte verdes com `./mvnw clean verify`.
