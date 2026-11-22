# Adopt the Seed4J engine in the CLI

## Purpose and success

Both CLI channels use the modular Seed4J engine. Existing projects retain their history and plans remain read only. Users with an installed runtime extension get an actionable migration diagnostic while keeping their original files. The stable channel still lists the `seed4j-extension` generator. The experimental channel retains its existing restriction.

## Context and limits

Start from stable `main`. Integrate its green GitHub SHA into `experimental` with a merge commit, then retain the experimental snapshot identity in `pom.xml`. Maven coordinates for custom extensions remain tracked in [Seed4J #14926](https://github.com/seed4j/seed4j/issues/14926) and [CLI #6](https://github.com/seed4j/seed4j-cli/issues/6). The previous full SHA migration plan is available in Git at `ef6307c:EXECPLAN.md`.

## Milestones

1. Preserve the transition at the public CLI and launcher boundaries. Replace the extension management handlers, reject `seed4j.runtime.mode: extension` before Spring, and retain the standard subprocess with the effective home. Exercise exit codes and preservation of legacy files through the packaged JAR.
2. Adopt the engine with Spring Boot 4.2.0, Maven Central release 2.4.0, and engine history reads. Update distribution policy, Renovate, README, command and architecture guides, and release notes. Run workflow tests, formatting, Maven tests, packaged acceptance and Habit Hooks before integration.
3. Merge the green main SHA into experimental. Set the published modular snapshot, Sonatype repository and generator restriction in the POM. Repeat acceptance with an empty Maven cache. After the experimental check and merge, dispatch synchronization on main and verify its result, issue #503 and the downstream build.

## Progress

- [x] Created `feat/adopt-seed4j-engine` from `ef6307c` and advanced it to current upstream `main`, `9987cf1`.
- [x] Removed the old runtime extension paths and verified the transition.
- [x] Adopted the engine and validated the stable distribution locally.
- [x] Exercised both Renovate managers locally with candidate version changes.
- [x] Merged [PR #511](https://github.com/seed4j/seed4j-cli/pull/511). The exact main SHA `f503bbfe963f9295240393beaa633f054c5b7595` passed its push build.
- [x] Merged the green main SHA into `feat/experimental-seed4j-engine` and validated the snapshot locally.
- [x] Merged [PR #512](https://github.com/seed4j/seed4j-cli/pull/512) after hosted `tests`. Merge SHA `03123b1e69cac4f0ef44a7a303c4078dc6ae73cf` contains `f503bbf` through the `29df9d7` merge commit.
- [x] Dispatched [synchronization](https://github.com/seed4j/seed4j-cli/actions/runs/45021784126) from `main`. It found `c47cdaf` already contained, closed [issue #503](https://github.com/seed4j/seed4j-cli/issues/503) and dispatched [the experimental build](https://github.com/seed4j/seed4j-cli/actions/runs/45021802370).
- [x] The dispatched experimental build passed `tests` on `03123b1` and requested the experimental release.
- [ ] Observe the first hosted modular Renovate update.

## Validation

| Scenario                          | Stable evidence                                                                                                                                                                                                                                 | Experimental evidence                                                                                                                                                                                                                                                            |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Empty Maven repository            | A newly created Maven repository resolved the public engine, API, core modules and test support at `2.4.0`.                                                                                                                                     | A second empty Maven repository resolved the four modular artifacts from Sonatype at `2.4.1-main.20261121.231244.65403861f94c000ecf2e0db50ed53e030b39d290-SNAPSHOT`.                                                                                                             |
| Packaged libraries and identity   | `Seed4JPackagedJarIT` verified `--version` and exactly three Seed4J runtime libraries at `2.4.0`. The archive lists Spring Boot `4.2.0`.                                                                                                        | The JAR lists exactly the API, engine and core modules at that snapshot, plus Spring Boot `4.2.0`. The CLI reported the experimental channel and upstream SHA `65403861f94c000ecf2e0db50ed53e030b39d290`.                                                                        |
| Catalog and effective home        | `Seed4JPackagedJarIT` found core modules and the stable generator, opened the generator help and verified a hidden slug from a home path with a space.                                                                                          | Core catalog and effective home passed. The catalog omitted the generator, and direct `apply` and `apply-set` returned `2` without creating the destination.                                                                                                                     |
| Project history and options       | Both aggregated and per-action legacy history passed. `apply --plan` and `apply-set --plan` preferred explicit options, reused history and preserved the complete project snapshot. `apply init --no-commit` wrote exactly one action.          | Both legacy history layouts, explicit precedence in both plans, byte-for-byte project preservation and one recorded init action passed.                                                                                                                                          |
| Installed extension migration     | Legacy YAML with installed JAR returned `1` with empty stdout. The home snapshot matched byte for byte. Editing only the mode restored `list`. Help returned `0` and each former management command returned `2`, preserving the home snapshot. | The same packaged test passed both exit codes and preservation checks, including recovery with `standard`.                                                                                                                                                                       |
| Local gates                       | `npm run test:workflows` passed 253 tests. `npm run prettier:check` and `habit-hooks` passed. `./mvnw test` passed 529 tests. Packaged Maven integration passed eight tests. The packed npm skill smoke test passed.                            | 253 workflow tests, Prettier, 529 Maven tests, eight Maven integration tests, the packed npm skill smoke test and Habit Hooks passed.                                                                                                                                            |
| Hosted `tests` and `clean verify` | [PR #511 build](https://github.com/seed4j/seed4j-cli/actions/runs/45020125548) and [exact main push build](https://github.com/seed4j/seed4j-cli/actions/runs/45020584577) both passed.                                                          | [PR #512 build](https://github.com/seed4j/seed4j-cli/actions/runs/45021375894) passed. The [dispatched build on the merged SHA](https://github.com/seed4j/seed4j-cli/actions/runs/45021802370) also passed.                                                                      |
| Renovate upgrade                  | Renovate 44.257.9 extracted one active regex updater for `com.seed4j:seed4j-engine`. Its replacement changed only `seed4j.version`, and all three Maven dependency entries resolved to the candidate version.                                   | Renovate 44.257.9 extracted one active Maven updater for `io.github.renanfranca:seed4j-core-modules` from the intended experimental POM. Its replacement changed only `seed4j.version`, and all three entries resolved to the candidate snapshot. Hosted observation is pending. |

To repeat the packaged acceptance, create an empty Maven local repository and run the package and Failsafe goals from
the channel checkout.

```bash
maven_repository="$(mktemp -d)"
./mvnw -B -ntp -Dmaven.repo.local="$maven_repository" clean package failsafe:integration-test failsafe:verify
```

The behavior tests first reproduced the old management command exit, extension startup and loss of the effective home.
The packaged library test found the old monolith before the dependency change. After the suite was green, the design review
removed the unused bootstrap resolver seam and repeated the packaged checkpoint.

The experimental merge resolves branch-specific identity and test conflicts using the common modular implementation. Its
POM retains the snapshot repository and the generator restriction. A packaged test expectation was corrected to compare
the version's final component with the reported upstream SHA.

Main advanced to `c47cdaf` through [PR #510](https://github.com/seed4j/seed4j-cli/pull/510), a concurrent Java formatter
update. The experimental PR contained that exact main SHA through merge `5b18991` and passed Prettier with the updated
formatter. The stable Prettier evidence applies to the adoption SHA `f503bbf`.

The public repositories listed no later modular version at validation time. Hosted Renovate remains pending until a
new candidate is available. The local Renovate replacement was verified for both managers.
