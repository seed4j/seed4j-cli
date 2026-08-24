# Habit Hooks snooze baseline

The snooze baseline records findings that have been reviewed but are not being changed now. It has
two classifications:

- **No action:** the finding reflects an intentional design, boundary, symmetry, or explicit test
  style. No change is planned while that rationale remains valid.
- **Deferred debt:** the finding identifies a possible improvement that is not required for the
  current refactoring. Reassess it when related code is changed.

A snoozed finding has not disappeared and is not necessarily a false positive. Snoozing records the
review decision while keeping the original sensor output intact.

The `snooze-until-changed` transformer ties entries to their files. Any change to a snoozed file
reopens all findings anchored to that file, so new work cannot inherit an old review automatically.
Findings in new files also remain active.

To audit every finding, including the reviewed baseline, run:

```bash
habit-hooks --all --no-snooze
```

After files or findings are removed, prune obsolete baseline entries with:

```bash
habit-snooze --prune
```
