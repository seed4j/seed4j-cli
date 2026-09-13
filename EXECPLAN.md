# Ativar `seed4j-cli@experimental` de ponta a ponta

## Purpose and success

Ativar publicamente o canal experimental sem alterar o canal estável. O rollout termina somente quando:

- `experimental` existe, está protegida e contém a identidade Maven experimental correta;
- o build hospedado do HEAD exato fica verde;
- semantic-release publica uma versão `*-experimental.N` com npm Trusted Publishing e provenance;
- `npm install -g seed4j-cli@experimental` funciona em prefixo limpo;
- `seed4j --version` mostra canal, snapshot e SHA upstream corretos;
- `latest` permanece em `0.1.0`, sem GitHub Release ou JAR estável novo;
- documentação, `EXECPLAN.md`, branches temporárias e estado final são reconciliados.

## Context and limits

- Publisher concluído em `main@5c60bfa`; snapshot público atual: `2.2.1-main.20260907.055800.4eebd07bce14-SNAPSHOT`.
- `seed4j-cli main@f139885` contém o ExecPlan mergeado; `experimental` e o dist-tag npm ainda não existem.
- Label `synchronization-pending`, auto-merge e npm OIDC/provenance já funcionam.
- Corrigir a proteção de `main` e criar proteção equivalente para `experimental`: PR obrigatório, `tests` estrito, zero aprovações obrigatórias, sem force-push/deleção e com bypass administrativo preservado.
- Preservar integralmente o checkout local sujo; executar em clones temporários limpos.
- Não criar token npm, não publicar manualmente, não alterar publisher/Central/secrets e não executar `./mvnw clean verify` localmente.
- Permitir somente um ciclo de correção TDD se houver incompatibilidade antes da publicação. Uma segunda falha encerra o rollout sem bypass.

## Decisions

- Selecionar no momento da execução o candidato público mais recente de um run bem-sucedido do publisher, exigindo GAV, SHA completo, manifesto e resolução Maven coerentes.
- Usar `experimental-rollout-plan`, `experimental-bootstrap` e `record-experimental-rollout`; os nomes já foram validados e não colidem com refs existentes.
- Criar a ref permanente pela API Git a partir do commit bootstrap já validado. A API gera um evento `create`, enquanto os workflows atuais não escutam esse evento, permitindo aplicar a proteção antes do primeiro build elegível. [GitHub: criação de refs e evento `create`](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#create).
- Fazer o primeiro build por um dispatch do workflow de sincronização em `main`. Como o bootstrap já contém o HEAD de `main`, o caminho `already-contained` dispara `github-actions.yml` como `github-actions[bot]`, fornecendo a proveniência aceita pelo release.
- Usar `feat(release): activate experimental snapshot channel` no commit bootstrap para garantir a primeira prerelease semantic-release.
- Antes da publicação irreversível, preparar e instalar localmente o tarball equivalente à versão prevista. Se a primeira versão pública ainda assim for inválida, parar, abrir evidência pública e solicitar decisão manual; não existe uma versão experimental anterior para rollback automático.

## Milestones

1. **Registrar o rollout**
   - Revalidar heads, runs ativos, Central, npm, Trusted Publisher, label e permissões.
   - Em clone limpo, atualizar `EXECPLAN.md` com este plano na branch `experimental-rollout-plan`.
   - Abrir PR para `main`, aguardar `tests`, revisar o diff e fazer merge normal.
   - Atualizar a proteção de `main` e confirmar a configuração pela API.

2. **Preparar o bootstrap experimental**
   - Criar `experimental-bootstrap` a partir do novo HEAD exato de `main`.
   - Alterar a autoridade do POM para o GAV público selecionado, SHA completo, canal `experimental`, repositório snapshot-only e indisponibilidade de `seed4j-extension`.
   - Atualizar no bootstrap a redação pública para anunciar que o canal está disponível.
   - Executar testes locais, preparar uma versão experimental prevista, gerar o tarball, instalá-lo em prefixo temporário e validar identidade e comportamento.
   - Publicar apenas a branch temporária no repositório oficial e disparar `github-actions.yml` nela. Exigir build verde e confirmar que o release foi corretamente ignorado por não ser a branch `experimental`.

3. **Criar e proteger `experimental`**
   - Criar `refs/heads/experimental` pela API apontando exatamente para o commit bootstrap verde.
   - Aplicar imediatamente a proteção aprovada e reler todos os campos.
   - Confirmar que nenhum build ou release inesperado foi iniciado pelo evento de criação.
   - Se qualquer execução privilegiada inesperada aparecer antes da proteção, cancelá-la e interromper o rollout para auditoria.

4. **Executar a primeira publicação**
   - Disparar `synchronize-experimental.yml` em `main`.
   - Exigir o resultado `already-contained` e o dispatch bot-authenticated do build do HEAD experimental.
   - Acompanhar build e release até o fim; exigir qualification e publish verdes.
   - Se o build falhar por incompatibilidade, usar o único ciclo autorizado: correção TDD em branch separada, PR para `experimental`, `tests` estrito e merge normal. Não alterar gates nem ignorar testes.
   - Capturar versão, tag imutável, SHA, run, provenance e dist-tags finais.

5. **Validar, documentar e limpar**
   - Instalar `seed4j-cli@experimental` em prefixo npm vazio e validar `--version`, help experimental e bloqueio seguro de `seed4j-extension`.
   - Instalar `@latest` separadamente e confirmar que continua estável.
   - Confirmar ausência de GitHub Release experimental e de mudanças no dist-tag `latest`.
   - Na branch `record-experimental-rollout`, aplicar à `main` a mesma redação pública de disponibilidade e registrar evidências finais no `EXECPLAN.md`; mergear por PR verde.
   - Observar a sincronização final para `experimental`; ela deve manter o POM experimental e não produzir nova versão sem commit release-worthy.
   - Remover branches temporárias e clones, deixando apenas `main` e `experimental`, sem tocar no checkout local original.

## Progress

- [x] Autorização explícita do mantenedor recebida.
- [x] Publisher, snapshot público, npm Trusted Publishing, label e automações inspecionados.
- [x] Política de uma única correção pré-publicação escolhida.
- [x] ExecPlan do rollout mergeado pelo PR #360 com `tests` verde; proteção estrita de `main` confirmada.
- [ ] Bootstrap experimental validado.
- [ ] Branch e proteções configuradas.
- [ ] Primeira prerelease npm publicada.
- [ ] Instalação pública e isolamento de `latest` verificados.
- [ ] Documentação, sincronização e limpeza concluídas.

## Validation

- Local/bootstrap: `npm ci`, `npm run test:release`, `npm run test:workflows`, `npm run test:npm-package`, `./mvnw test`, `npm run prettier:check`, `habit-hooks` e `git diff --check`.
- Hospedado: `github-actions.yml` verde no commit bootstrap e no HEAD permanente de `experimental`; `release.yml` verde no mesmo SHA.
- Registro: `npm view seed4j-cli dist-tags`, versão experimental, `_npmUser` GitHub Actions e attestation apontando para `seed4j/seed4j-cli`, `release.yml`, branch e SHA corretos.
- Smoke test: instalação global com `--prefix` temporário, `seed4j --version`, help, indisponibilidade de `seed4j-extension` e instalação estável separada.
- Estado final: proteções exatas, nenhum workflow ativo, nenhuma issue operacional nova, nenhuma branch temporária e checkout original preservado.
- Marco 1 observado: PR #360 mergeado normalmente em `main@f139885`; `tests` concluiu com sucesso e a API confirmou PR obrigatório, zero aprovações, status estrito e bloqueio de force-push/deleção.

## Rollout and recovery

- Falha antes da ref permanente: corrigir uma vez ou remover somente a branch temporária; nenhuma superfície pública muda.
- Falha após criar `experimental`, mas antes de publicar: manter a branch protegida, realizar no máximo o ciclo autorizado e deixar o dist-tag ausente se continuar vermelho.
- Falha ou publicação parcial em npm: não repetir cegamente, não mover tags e não improvisar rollback; registrar issue e evidências para decisão manual.
- Conflitos futuros de sincronização, Renovate vermelho, issue do publisher, rotação do token ou rollback de pacote continuam sendo os únicos pontos normais de intervenção do mantenedor.
