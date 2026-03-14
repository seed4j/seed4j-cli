# Runtime Bootstrap Next Slice

## Baseline used by this spec

This slice specification is based on `LOCAL_IMPLEMENTED_SPEC.md` (updated on 2026-03-12).

Implemented baseline:

- Runtime selection domain exists (`standard` and `extension` modes).
- Extension metadata validation and CLI compatibility checks exist.
- Launcher pre-bootstrap decision logic exists and is tested.
- `--version` output already supports runtime mode plus distribution id/version.

Current gap summary from that baseline:

- Main entrypoint still not delegated to the launcher in the real production path.
- Real Java child JVM bootstrap is still missing.
- Runtime identity in the active Spring process is not sourced from launcher-provided values.
- Extended metadata contract (`runtime-version`, `bootstrap-class`, `runtime-contract-version`) is not implemented.

## Progress vs overall objective

Estimated progress from the baseline: around 60%.

- Completed: modeling, validation, launcher decision rules, and output shape.
- Missing for full objective: production wiring and runtime handoff across JVM boundary.

## Goal of this next slice

Deliver the first end-to-end production bootstrap flow so the public entrypoint effectively runs through runtime selection and launches a child JVM when needed.

The outcome must make runtime mode and distribution identity observable inside the effective Spring runtime process.

## In scope for this slice

- Wire `Seed4JCliApp.main(String[] args)` to `Seed4JCliLauncher`.
- Implement concrete Java child-process launching with `ProcessBuilder`.
- Launch child JVM through `PropertiesLauncher`.
- Propagate `seed4j.cli.runtime.child=true`.
- Propagate runtime mode and active distribution id/version as JVM system properties.
- Propagate `loader.path` for extension mode.
- Propagate child stdio and child exit code back to parent process.
- Source runtime identity from propagated system properties inside Spring.

## Out of scope for this slice

- New metadata fields: `distribution.runtime-version`, `distribution.bootstrap-class`, `distribution.runtime-contract-version`.
- Runtime mode rename from `standard` to `base`.
- `seed4j.runtime.fail-on-invalid-extension` behavior and fallback branching.
- Runtime-contract handshake semantics beyond current id/version propagation.

## Required behavior (acceptance criteria)

- Public entrypoint:
  - Calling `Seed4JCliApp.main(String[] args)` must delegate to launcher bootstrap and exit with launcher return code.
- Standard mode:
  - Without runtime mode override, launcher must start a Java child process in `standard` mode.
- Extension mode:
  - With valid extension metadata and jar, launcher must start child process with `loader.path`, runtime mode, and distribution id/version properties.
- Invalid extension setup:
  - Launcher must fail before child launch and return non-zero.
- Child mode recursion guard:
  - When `seed4j.cli.runtime.child=true`, process must run local Spring path without spawning another child.
- Runtime identity observability:
  - In the effective Spring runtime process, `--version` must reflect propagated runtime mode and distribution identity.

## TDD execution plan

### Cycle 1: Public entrypoint wiring

Test first:

- `Seed4JCliApp.main(String[] args)` forwards args to production bootstrap.
- Exit handler receives exactly the bootstrap return code.

Minimal green:

- Add production composition seam and route public `main` through it.

### Cycle 2: Real child JVM launcher

Test first:

- `JavaProcessChildLauncher` builds command with deterministic property ordering.
- Command includes `-cp`, executable jar, `PropertiesLauncher`, and forwarded args.
- Process execution result is returned as launcher exit code.

Minimal green:

- Implement `ProcessBuilder(...).inheritIO().start().waitFor()` integration path.

### Cycle 3: Launcher handoff contract

Test first:

- Standard mode request includes `seed4j.cli.runtime.child=true` and runtime mode property.
- Extension mode request additionally includes distribution id/version and `loader.path`.

Minimal green:

- Build child process request from resolved runtime selection and launch it.

### Cycle 4: Runtime identity inside Spring

Test first:

- Runtime selection provider reads from runtime system properties.
- `--version` output reflects values from propagated selection in child execution context.

Minimal green:

- Replace static standard-only provider behavior with system-property-backed selection.

### Vertical checkpoint

Run:

- `./mvnw clean verify`

## Definition of done

- Public CLI entrypoint no longer boots Spring directly in parent for normal mode.
- Launcher controls runtime selection and child-process bootstrap in production flow.
- Runtime identity is visible in the effective Spring process via propagated properties.
- All existing runtime-selection and launcher tests remain green.
- Full repository validation passes with `./mvnw clean verify`.
