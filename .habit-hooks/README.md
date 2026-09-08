# Habit Hooks

Habit Hooks analyzes only Java production sources under `src/main`. The Java plugin runs PMD with
the repository ruleset in `.habit-hooks/pmd-ruleset.xml`.

Habit Hooks 1.5.0 or newer is required. Install it with the Java plugin using:

```bash
uv tool install "habit-hooks[java]>=1.5.0"
```

Upgrade an existing `uv` tool installation using:

```bash
uv tool upgrade habit-hooks
```

The repository ruleset replaces the Java plugin's bundled ruleset, so it explicitly enables PMD's
`AvoidDeeplyNestedIfStmts` with `problemDepth=3`. Habit Hooks 1.5.0 and newer report violations of
that rule as `deep-nesting`.

The target is zero raw production findings. Findings should be resolved in the code or configuration
rather than hidden behind a checked-in baseline.

Run the production gate with:

```bash
habit-hooks
```

Snoozing a finding or creating a snooze baseline is allowed only with explicit user authorization.
