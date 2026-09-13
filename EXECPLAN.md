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

O canal foi ativado, mas o isolamento estável acima não foi integralmente cumprido: durante o sexto ciclo, o commit
`fix(release)` publicou irreversivelmente `0.1.1` e moveu `latest` para essa versão. O restante do rollout foi concluído
sem ocultar esse desvio, sem apagar release/tag, sem unpublish e sem criar novas versões estáveis.

## Context and limits

- Publisher concluído em `main@5c60bfa`; snapshot público atual: `2.2.1-main.20260907.055800.4eebd07bce14-SNAPSHOT`.
- `seed4j-cli main@8710f9a` contém a correção final de provenance; `experimental@4592cd8` está protegida e o npm publica
  `experimental: 0.2.0-experimental.3`.
- Label `synchronization-pending`, auto-merge e npm OIDC/provenance já funcionam.
- Corrigir a proteção de `main` e criar proteção equivalente para `experimental`: PR obrigatório, `tests` estrito, zero aprovações obrigatórias, sem force-push/deleção e com bypass administrativo preservado.
- Preservar integralmente o checkout local sujo; executar em clones temporários limpos.
- Não criar token npm, não publicar manualmente, não alterar publisher/Central/secrets e não executar `./mvnw clean verify` localmente.
- Permitir somente um ciclo de correção TDD se houver incompatibilidade antes da publicação. Uma segunda falha encerra o rollout sem bypass.

## Decisions

- Selecionar no momento da execução o candidato público mais recente de um run bem-sucedido do publisher, exigindo GAV, SHA completo, manifesto e resolução Maven coerentes.
- Usar `experimental-rollout-plan`, `experimental-bootstrap` e `record-experimental-rollout`; os nomes já foram validados e não colidem com refs existentes.
- Criar a ref permanente pela API Git a partir do commit bootstrap já validado e aplicar a proteção na mesma operação. A execução observada também emitiu um `push` com base zero, que falhou de forma segura no commitlint antes do build e deixou o release derivado como `skipped`; o primeiro build elegível continua sendo o dispatch autenticado pela sincronização.
- Fazer o primeiro build por um dispatch do workflow de sincronização em `main`. O caminho `already-contained` produziu o build verde esperado como `github-actions[bot]`, mas o `GITHUB_TOKEN` não produziu o evento `workflow_run` derivado para `release.yml`; não disparar nem publicar manualmente, e exigir nova autorização antes de corrigir o encadeamento.
- O mantenedor autorizou explicitamente um segundo ciclo TDD. Corrigir o encadeamento no código confiável e revisado: o próprio build experimental verde solicitará `release.yml` por `workflow_dispatch`, e a release aceitará somente a identidade imutável desse build após validá-la pela API do GitHub.
- A validação hospedada mostrou que a supressão também alcança o callback `workflow_run` usado para finalizar o PR automático de sincronização. Tratar os dois callbacks como o mesmo defeito autorizado: o build do proposal solicitará explicitamente a finalização, que revalidará pela API o run ID, SHA, branch, ator e conclusão antes de habilitar auto-merge.
- O mantenedor recusou a aprovação manual do run `pull_request` e autorizou explicitamente continuar após o limite anterior. O terceiro ciclo deve preservar a proteção e fazer o código confiável de finalização reportar o check obrigatório `tests` no SHA do PR somente depois que o run explícito exato for revalidado; não aprovar o run bloqueado nem reduzir os gates.
- O terceiro ciclo comprovou que um check criado pela GitHub Actions App não substitui a aprovação obrigatória do workflow: o check #103726590315 ficou `success`, associado ao PR #362, com nome, app e SHA corretos, mas o `statusCheckRollup` permaneceu vazio enquanto o run `pull_request` criado pelo `GITHUB_TOKEN` ficou em `action_required`. Interromper novamente sem remover o gate; as alternativas oficiais exigem a aprovação explícita ou criar/atualizar o PR com credencial de outra GitHub App/PAT, ambas fora das decisões atuais.
- O mantenedor autorizou uma GitHub App dedicada após recusar o gate manual. Usar um token de instalação efêmero somente para o push da branch descartável e para criar/atualizar o PR, pois são essas mutações que devem emitir eventos fora da identidade `GITHUB_TOKEN`; manter os dispatches explícitos autenticados pelo `GITHUB_TOKEN`, sem PAT, token npm, mudança de proteção ou checkout de código não confiável com credencial privilegiada. A App requer `Contents: write` e `Pull requests: write`, instalação limitada ao repositório e os valores `SYNC_APP_CLIENT_ID`/`SYNC_APP_PRIVATE_KEY` configurados como variable/secret.
- O PR #362 finalmente executou o check `pull_request` sem aprovação e foi mesclado, mas a primeira release experimental #34761383830 revelou que `semantic-release` usa `GITHUB_REF` do dispatch confiável em `main`, mesmo após checkout do SHA de `experimental`. Preservar o dispatch em `main`; transportar a branch já qualificada para o passo de publicação e sobrescrever `GITHUB_REF`/`GITHUB_SHA` somente no processo `semantic-release`, vinculando sua inferência ao SHA e branch protegidos previamente verificados.
- A release #34762650737 inferiu corretamente `experimental` e `0.2.0-experimental.1`, mas o npm recusou a provenance porque o predicado herdou o ref/SHA sintético enquanto as claims OIDC descrevem o workflow real em `main`. Preservar a tag falha imutável no SHA correto, não publicar nem retaggear `.1`; depois que o semantic-release selecionar o alvo qualificado, restaurar no contexto compartilhado do plugin npm o ref/SHA real do workflow e deixar um commit `fix(release)` produzir `.experimental.2`.
- O semantic-release clona o contexto para cada plugin; por isso a restauração anterior não alcançou o plugin npm e a
  release #34764040619 também foi recusada, preservando apenas a tag `.2`. O mesmo commit `fix(release)` acionou a
  release estável #34763548970, publicou `0.1.1` e moveu `latest`. Essa publicação é irreversível para os propósitos do
  rollout e deve permanecer registrada, não apagada ou mascarada.
- Delegar diretamente todos os hooks de `@semantic-release/npm` por um wrapper que recebe uma cópia do contexto com a
  identidade real do workflow. Usar `ci(experimental-release)` como gatilho estreito somente no canal experimental;
  ele deve permanecer neutro em `main` para não criar outra versão estável.
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
- [x] Bootstrap experimental validado localmente, por tarball e no build hospedado da branch temporária.
- [x] Branch `experimental@cf66e1e` e proteção configuradas; execução `push` de base zero auditada sem publicação.
- [x] Segundo ciclo TDD autorizado após o bloqueio documentado do encadeamento por `GITHUB_TOKEN`.
- [x] Terceiro ciclo TDD autorizado explicitamente após o GitHub não associar o check do `workflow_dispatch` ao PR #362.
- [x] Terceiro ciclo implementado pelo PR #364 em `main@88f5060`; o build explícito #34758119261 e o check #103726590315 ficaram verdes no SHA `014bf4b`.
- [x] GitHub App dedicada autorizada; inspeção confirmou que ainda não existem variable/secret ou instalação observável configurados para a sincronização.
- [x] Quarto ciclo TDD implementado em `synchronization-app-identity`: o proposal usa token efêmero da App para checkout/push/PR, os dispatches e issues permanecem no `GITHUB_TOKEN`, e o check sintético do terceiro ciclo foi removido; `npm run test:workflows` passou com 38 testes.
- [x] Quarto ciclo publicado no PR #365 pelo commit `fba07f4`; o gate hospedado `tests` #34759347751 concluiu com sucesso. O PR permanece aberto e sem merge porque `SYNC_APP_CLIENT_ID`, `SYNC_APP_PRIVATE_KEY` e a instalação restrita da App ainda não existem.
- [x] App instalada e configuração cadastrada; PR #365 mesclado em `main@f66f54e`. O sync #34760960019 atualizou o PR #362, o `pull_request` build #34760976736 passou sem `action_required`, e o PR foi mesclado automaticamente em `experimental@7653677`.
- [x] Primeiro build pós-merge de `experimental` #34761189910 passou e solicitou a release automaticamente; a release #34761383830 falhou antes de criar pacote/tag porque inferiu `main` e propôs `0.2.0` em vez da prerelease.
- [x] Quinto ciclo TDD implementado em `experimental-release-branch-context`: a branch verificada é carregada para o processo `semantic-release` junto do SHA qualificado; `npm run test:release` passou com 19 testes.
- [x] Quinto ciclo publicado e validado no PR #366: o gate hospedado `tests` #34761683847 passou, e o merge protegido comum avançou `main` para `a96787b` sem aprovação manual nem privilégio administrativo.
- [x] Build de `main@a96787b` #34761920699 passou; a App criou o PR de sincronização #367 no SHA `6cc9c2f`, e o run `pull_request` #34762179974 iniciou automaticamente sem `action_required`.
- [x] Release #34762650737 qualificou `experimental@c5be404` e calculou `0.2.0-experimental.1`, mas o npm rejeitou a provenance com E422 antes de publicar; `latest` permaneceu em `0.1.0` e somente a tag `.1` ficou no SHA correto.
- [x] Sexto ciclo TDD implementado em `codex/experimental-provenance-context`: o contexto sintético seleciona a prerelease, um plugin valida esse vínculo e restaura a identidade real do workflow antes do npm; release 21/21, workflows 38/38, pacote 10/10, Maven 645/645, Prettier e Habit Hooks verdes.
- [x] Sexto ciclo publicado e validado no PR #368: o gate hospedado #34763044895 passou e o merge protegido comum avançou `main` para `73ae15c`, sem aprovação manual nem privilégio administrativo.
- [x] Desvio estável registrado: a release #34763548970 publicou `0.1.1` a partir de `main@73ae15c`; `latest` avançou de
      `0.1.0` para `0.1.1`, sem autorização ou mecanismo seguro para fingir que a versão pública nunca existiu.
- [x] O PR automático #369 foi criado pela App, passou sem `action_required` e foi mesclado em `experimental@ee25167`;
      a release #34764040619 calculou `.experimental.2`, mas a mesma incompatibilidade de provenance impediu o pacote.
- [x] Sétimo ciclo TDD implementado no commit `1366ce1`: wrapper npm com contexto isolado e gatilho
      `ci(experimental-release)` neutro no canal estável; release 23/23, workflows 38/38, pacote 10/10, Maven 645/645,
      Prettier e Habit Hooks verdes.
- [x] PR #370 passou no run #34764526421 e foi mesclado em `main@8710f9a`; build #34764769010 ficou verde e a release
      estável #34765085764 registrou que não havia mudança relevante, sem publicar `0.1.2`.
- [x] A App criou o PR #371, o run `pull_request` #34765098033 passou sem aprovação manual e o merge automático avançou
      `experimental` para `4592cd8`; a branch descartável foi removida.
- [x] Primeira prerelease npm pública publicada: build experimental #34765346879 verde e release #34765583503
      publicaram `seed4j-cli@0.2.0-experimental.3` por Trusted Publishing com provenance.
- [x] Instalação pública verificada em prefixo limpo: versão/canal/snapshot/SHA corretos, aviso no help e
      `seed4j-extension` bloqueado com exit 2 e nenhuma alteração. `@latest` foi instalado separadamente como `0.1.1`.
- [ ] Documentação, sincronização e limpeza concluídas.

## Validation

- Local/bootstrap: `npm ci`, `npm run test:release`, `npm run test:workflows`, `npm run test:npm-package`, `./mvnw test`, `npm run prettier:check`, `habit-hooks` e `git diff --check`.
- Hospedado: `github-actions.yml` verde no commit bootstrap e no HEAD permanente de `experimental`; `release.yml` verde no mesmo SHA.
- Registro: `npm view seed4j-cli dist-tags`, versão experimental, `_npmUser` GitHub Actions e attestation apontando para `seed4j/seed4j-cli`, `release.yml`, branch e SHA corretos.
- Smoke test: instalação global com `--prefix` temporário, `seed4j --version`, help, indisponibilidade de `seed4j-extension` e instalação estável separada.
- Estado final: proteções exatas, nenhum workflow ativo, nenhuma issue operacional nova, nenhuma branch temporária e checkout original preservado.
- Marco 1 observado: PR #360 mergeado normalmente em `main@f139885`; `tests` concluiu com sucesso e a API confirmou PR obrigatório, zero aprovações, status estrito e bloqueio de force-push/deleção.
- Marco 2 observado: Node release 17/17, workflows 37/37, pacote npm 10/10, Maven 645/645, Prettier e Habit Hooks verdes; tarball previsto `0.2.0-experimental.1` instalado em prefixo vazio com identidade e bloqueio seguros; build temporário #34753852808 verde em `cf66e1e`.
- Marco 3 observado: `experimental@cf66e1e` protegida com a mesma política de `main`; o `push` de criação #34754111178 falhou somente no range de base zero, e os workflows derivados #34754126832/#34754126860 foram ignorados sem tag ou pacote.
- Marco 4 interrompido: sincronização #34754218902 confirmou `already-contained` e o build #34754224120 ficou verde em `experimental@cf66e1e` como `github-actions[bot]`, mas nenhum `release.yml` derivado foi criado após duas janelas limitadas de observação.
- Segundo ciclo observado: `main@41b7872` corrigiu os callbacks explícitos de release e sincronização; o build exato #34756906291 ficou verde em `342da36` e a finalização #34757118524 revalidou run, SHA, branch e ator. Ainda assim, o PR #362 continuou `BLOCKED` porque o GitHub deixou o run de `pull_request` #34756906707 em `action_required` e não incluiu o job do `workflow_dispatch` no `statusCheckRollup` do PR. O mantenedor recusou corretamente a aprovação manual; o rollout foi interrompido no limite de correções, sem merge ou publicação.
- Terceiro ciclo observado: PR #364 mergeado normalmente em `main@88f5060` após `tests` verde; a sincronização gerou `014bf4b`, o build explícito #34758119261 ficou verde e a finalização #34758340725 criou `tests=success` pelo app 15368 no SHA exato. A API associou o check #103726590315 ao PR #362, mas o PR permaneceu `BLOCKED`, com `statusCheckRollup: []`, porque o run `pull_request` #34758120140 criado pelo `GITHUB_TOKEN` exige aprovação. O rollout foi interrompido sem clicar, sem merge e sem publicação.
- Publicação final observada: o tag `v0.2.0-experimental.3` aponta para `experimental@4592cd8`; npm expõe
  `experimental: 0.2.0-experimental.3` e `latest: 0.1.1`; `_npmUser` é GitHub Actions. As attestations npm publish e
  SLSA usam o digest SHA-512 do pacote e a provenance identifica `seed4j/seed4j-cli`, `.github/workflows/release.yml`,
  `refs/heads/main` e a invocation #34765583503. Não existe GitHub Release experimental.
- Smoke final observado: `seed4j --version` exibiu CLI `0.2.0-experimental.3`, canal `experimental`, snapshot
  `2.2.1-main.20260907.055800.4eebd07bce14-SNAPSHOT` e upstream
  `4eebd07bce14c9a6ac70bace157fcc616133e950`; o teste de módulo indisponível não alterou o diretório alvo.

## Outcome deviation

- O objetivo público principal foi atingido, mas o critério `latest: 0.1.0` não foi: `0.1.1` e o GitHub Release estável
  correspondente foram criados durante a correção de provenance. Remover ou ocultar esses artefatos não seria uma
  recuperação fiel nem segura; o estado final os preserva e documenta.
- As tags experimentais `.1` e `.2` permanecem como evidência imutável das tentativas recusadas antes do publish. O
  registro npm contém somente `.experimental.3`, a primeira versão experimental efetivamente publicada.

## Rollout and recovery

- Falha antes da ref permanente: corrigir uma vez ou remover somente a branch temporária; nenhuma superfície pública muda.
- Falha após criar `experimental`, mas antes de publicar: manter a branch protegida, realizar no máximo o ciclo autorizado e deixar o dist-tag ausente se continuar vermelho.
- Estado interrompido observado: manter `experimental@cf66e1e` protegida e o dist-tag ausente; não usar publicação manual, token novo nem um commit-gatilho como substituto para a correção revisada do encadeamento.
- Estado interrompido após o segundo ciclo: o PR #362 ficou sem merge e o auto-merge foi desarmado para que uma aprovação acidental posterior não publicasse o canal. `experimental` permaneceu em `cf66e1e`, protegida, e o npm permaneceu somente com `latest: 0.1.0` até o mantenedor autorizar explicitamente o terceiro ciclo.
- Estado interrompido após o terceiro ciclo: desarmar novamente o auto-merge do PR #362. Manter `experimental@cf66e1e`, proteções e npm intactos; não excluir o run `action_required`, não alterar a origem obrigatória do check e não usar credencial pessoal como bypass. Uma nova direção exige decisão explícita sobre o gate manual ou sobre uma identidade de automação diferente do `GITHUB_TOKEN`.
- Recuperação da App: se variable, secret, instalação ou permissões não estiverem exatos, não mergear o workflow dependente da App. Remover somente a branch de correção e manter o PR #362 desarmado; nunca substituir silenciosamente a App por PAT ou pelo token npm.
- Falha ou publicação parcial em npm: não repetir cegamente, não mover tags e não improvisar rollback; registrar issue e evidências para decisão manual.
- Conflitos futuros de sincronização, Renovate vermelho, issue do publisher, rotação do token ou rollback de pacote continuam sendo os únicos pontos normais de intervenção do mantenedor.
