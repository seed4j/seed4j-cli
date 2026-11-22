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
- [ ] Merge stable after the hosted `tests` check and record the green main SHA.
- [ ] Merge the green main SHA into experimental, validate the snapshot and merge the experimental PR.
- [ ] Dispatch synchronization from main and verify `already-contained`, issue #503 and the experimental build.
- [ ] Observe the first hosted modular Renovate update.

## Validation

| Scenario                          | Stable evidence                                                                                                                                                                                                                                 | Experimental evidence                                                                                                                                                                                                                                                            |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Empty Maven repository            | A newly created Maven repository resolved the public engine, API, core modules and test support at `2.4.0`.                                                                                                                                     | Pending                                                                                                                                                                                                                                                                          |
| Packaged libraries and identity   | `Seed4JPackagedJarIT` verified `--version` and exactly three Seed4J runtime libraries at `2.4.0`. The archive lists Spring Boot `4.2.0`.                                                                                                        | Pending                                                                                                                                                                                                                                                                          |
| Catalog and effective home        | `Seed4JPackagedJarIT` found core modules and the stable generator, opened the generator help and verified a hidden slug from a home path with a space.                                                                                          | Pending                                                                                                                                                                                                                                                                          |
| Project history and options       | Both aggregated and per-action legacy history passed. `apply --plan` and `apply-set --plan` preferred explicit options, reused history and preserved the complete project snapshot. `apply init --no-commit` wrote exactly one action.          | Pending                                                                                                                                                                                                                                                                          |
| Installed extension migration     | Legacy YAML with installed JAR returned `1` with empty stdout. The home snapshot matched byte for byte. Editing only the mode restored `list`. Help returned `0` and each former management command returned `2`, preserving the home snapshot. | Pending                                                                                                                                                                                                                                                                          |
| Local gates                       | `npm run test:workflows` passed 253 tests. `npm run prettier:check` and `habit-hooks` passed. `./mvnw test` passed 529 tests. Packaged Maven integration passed eight tests. The packed npm skill smoke test passed.                            | Pending                                                                                                                                                                                                                                                                          |
| Hosted `tests` and `clean verify` | Pending                                                                                                                                                                                                                                         | Pending                                                                                                                                                                                                                                                                          |
| Renovate upgrade                  | Renovate 44.257.9 extracted one active regex updater for `com.seed4j:seed4j-engine`. Its replacement changed only `seed4j.version`, and all three Maven dependency entries resolved to the candidate version.                                   | Renovate 44.257.9 extracted one active Maven updater for `io.github.renanfranca:seed4j-core-modules` from the intended experimental POM. Its replacement changed only `seed4j.version`, and all three entries resolved to the candidate snapshot. Hosted observation is pending. |

The behavior tests first reproduced the old management command exit, extension startup and loss of the effective home.
The packaged library test found the old monolith before the dependency change. After the suite was green, the design review
removed the unused bootstrap resolver seam and repeated the packaged checkpoint.
