# Habit Hooks

Habit Hooks analyzes only Java production sources under `src/main`. The Java plugin runs PMD with
the repository ruleset in `.habit-hooks/pmd-ruleset.xml`.

The target is zero raw production findings. Findings should be resolved in the code or configuration
rather than hidden behind a checked-in baseline.

Run the production gate with:

```bash
habit-hooks
```

Snoozing a finding or creating a snooze baseline is allowed only with explicit user authorization.
