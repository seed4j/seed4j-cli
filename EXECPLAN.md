# Migrate the experimental channel to full-SHA versions

## Purpose and success

Replace the Pages/custom-datasource pilot with a self-contained Maven identity:

```text
<upstream-base>-main.<yyyyMMdd>.<HHmmss>.<40-character-sha>-SNAPSHOT
```

Renovate will use only its native Maven manager and datasource. The pilot is complete when a real snapshot creates exactly one pull request against `experimental`, changes only `seed4j.version`, passes the protected checks, and automerges when compatible.

## Context and limits

- The work spans this repository and the adjacent `../seed4j-main-snapshots` publisher repository.
- Remove all uncommitted Pages, `latest.json`, `digest`, and custom-datasource pilot changes.
- Preserve the public CLI output `Seed4J upstream commit`, deriving the value from the Maven version.
- Remove `seed4j.upstream-commit` from the POM and packaged metadata as an independent source.
- Keep the full SHA in the publisher manifest, JAR provenance, and published snapshot POM `<scm><tag>`.
- Existing short-SHA snapshots remain in Central and are neither republished nor deleted.
- Do not change CLI commands, options, or public APIs.
- Hosted branch synchronization, Central publication, and Renovate acceptance require external execution after the corresponding local changes are integrated.

## Decisions

- Use the complete SHA in the Maven version. The resulting version is approximately 76 characters and remains below Maven's 256-character limit.
- Keep the timestamp before the SHA to preserve chronological ordering.
- Migrate in stages to prevent duplicate pull requests and conflicts between `main` and `experimental`.
- Preserve compatibility with short-SHA versions only during migration; no transitional support remains in the final state.
- Do not enable GitHub Pages or create another external feed.

## Milestones

1. **Pause Renovate and prepare compatibility on CLI `main`.** Remove the experimental regex manager and temporarily disable Maven updates for the personal coordinate. Accept either a short-SHA version accompanied by `seed4j.upstream-commit` or a full-SHA version whose provenance is derived from the version; when both exist, require exact equality. Keep the current stable POM property until `main` has synchronized to `experimental`. Validate locally, then integrate and wait for protected synchronization. Before publishing the new format, confirm no experimental snapshot PR is open and at least one hosted Renovate cycle has read the paused configuration.
2. **Change and prove the publisher contract.** Use the full SHA in deterministic version derivation and reject versions longer than 256 characters. Update the manifest, overlay, expected names, fixtures, dry run, README, and tests. Remove the feed job, scripts, tests, Pages permissions, and feed-specific reporting changes. Validate locally, integrate on publisher `main`, manually run `operation=head`, and verify the public POM, JAR, and tests JAR, including GAV, full version, `<scm><tag>`, embedded SHA, and the absence of sources/Javadoc.
3. **Migrate CLI `experimental`.** Open a dedicated PR against `experimental` using the proven full-SHA snapshot. Change only `seed4j.version` for the dependency identity, remove `seed4j.upstream-commit` and its packaged metadata token, and prove that CLI output still shows the full SHA derived from the version. Require the protected `tests` check before merge.
4. **Finalize CLI `main` and reactivate Renovate.** Remove the empty stable POM property and transitional short-version/legacy-metadata support. Require full-SHA versions for experimental distributions while stable distributions remain SHA-free. Keep `Seed4JUpstreamCommit` as an internal derived concept. Configure one active experimental Maven rule using the Central snapshot repository, `ignoreUnstable=false`, and protected automerge. Ensure no custom datasource, experimental regex manager, or competing Maven rule exists. Integrate and verify clean synchronization to `experimental`.
5. **Run hosted acceptance.** Publish the next eligible full-SHA snapshot, wait for Renovate's daily cadence, and accept only one PR based on `experimental` whose diff is restricted to `seed4j.version`. Verify that the version SHA matches the official commit, published POM, manifest, and CLI metadata; then verify checks, automerge, and compatible experimental npm publication.

## Progress

- [x] Full-SHA version contract selected.
- [x] Maven size, format, and ordering constraints confirmed.
- [x] Staged migration selected.
- [x] Local Renovate pause and transitional CLI compatibility implemented and validated.
- [ ] Pause integrated on CLI `main`, synchronized, and observed by one hosted Renovate cycle.
- [x] Local full-SHA publisher contract implemented and validated; Pages/feed pilot changes removed.
- [ ] First full-SHA snapshot published and validated from publisher `main`.
- [ ] `experimental` migrated.
- [ ] Final CLI contract installed and native Maven Renovate rule reactivated.
- [ ] One real hosted Renovate PR observed and recorded.

## Documentation

- This `EXECPLAN.md` is the durable migration plan.
- Update the experimental specification, channel runbook, and workflow guide so the Maven version is the indivisible identity.
- Update the publisher README with the 40-character format.
- Remove every normative reference to Pages, `latest.json`, `digest`, two-field updates, and custom datasources.
- Document rollback as pausing the Maven rule and manually updating one version.

## Rollout and recovery

The required order is: pause Renovate, prepare compatibility, change the publisher, publish and validate, migrate `experimental`, finalize `main`, and reactivate Renovate.

- If Central rejects the new format, keep Renovate paused and restore the short-SHA publisher format; do not enable Pages automatically.
- If the Maven manager does not detect the snapshot, keep it paused and update `seed4j.version` manually pending a new decision.
- If duplicate pull requests appear, disable the experimental rule immediately and inspect their origin; do not restore a regex manager.
- If a snapshot is incompatible, leave its PR red and adapt the CLI there or in a replacement PR; never bypass protected checks.
- Remove short-version compatibility only after a full-SHA snapshot resolves and builds successfully on `experimental`.

## Validation

Publisher validation:

- `npm test`
- `npm run prettier:check`
- `npm run dry-run`
- `./mvnw --version`
- `habit-hooks`
- Public resolution of the POM, JAR, and tests JAR from Central.

CLI validation:

- `npm run test:workflows`
- `npm run prettier:check`
- `./mvnw test`
- Renovate configuration validation with the pinned diagnostic version.
- `habit-hooks`

Required behavior:

- Accept an experimental version containing exactly 40 lowercase hexadecimal SHA characters.
- Reject a short, invalid, or published-POM-divergent SHA in the final contract.
- Keep stable distributions SHA-free.
- Derive and display the complete SHA in CLI output.
- Detect exactly one experimental dependency.
- Prove that Renovate changes only `seed4j.version`.
- Reject any reintroduction of Pages, a custom datasource, `digest`, or a second experimental manager.

Observed at the current milestone boundary:

- CLI: 39 workflow-policy tests and 648 Maven tests passed; repository-wide Prettier, Renovate 44.103.6 validation, and `habit-hooks` passed.
- Publisher: 49 tests, changed-file Prettier, dry run, Maven wrapper check, and `habit-hooks` passed. The repository-wide Prettier command remains red only for 17 pre-existing `.agent/tmp` evidence files outside this migration.
- Hosted read-only check: no open pull request currently targets `experimental`, but remote `main` still contains the old active experimental rule. A hosted Renovate cycle cannot observe the pause until these CLI changes are integrated.
