# Applying an individual module

## Inspect and plan

Inspect the active module interface before constructing an invocation:

```text
seed4j apply <module> --help
seed4j apply <module> --plan
```

The plan is read-only and can return exit code `0` while parameters or dependencies are pending. Inspect the rendered
dependency and parameter states; code `0` alone does not mean that the module is ready to apply.

A normal `seed4j apply <module>` validates dependencies before required parameters. A pending module or feature dependency
blocks execution with exit code `2` and creates no generated files, history entries, or commits. Never choose a dependency
or feature provider implicitly. Ask when the visible alternatives remain materially ambiguous.

Reuse compatible values identified as coming from project history instead of redundantly passing them. Explicit user
input takes precedence when the user intentionally overrides history.

## Authorization and execution

A request to implement or change the project authorizes execution after a valid plan; do not request redundant
confirmation solely because `--plan` ran. A request for inspection, explanation, or planning stops after reporting the
plan. Ask before execution when the plan reveals a material unresolved choice.

Commits are enabled by default. For normal project creation, omit `--no-commit` and first establish both project-write and
Git-metadata-write capability. Existing working-tree changes neither authorize `--no-commit` nor prove that a module
commit is safe. Use `--no-commit` only when the user explicitly asks Seed4J not to initialize Git or create commits.

## Codex permissions

Codex approvals and permissions are different. `approval_policy = "never"` prevents approval prompts; it does not grant
filesystem or network access. The `:workspace` permission profile and the classic `workspace-write` sandbox protect
`.git` recursively as read-only, so a Seed4J invocation with commits enabled cannot complete its Git write there.

For Codex versions using permission profiles, Full Access is configured with:

```toml
approval_policy = "never"
default_permissions = ":danger-full-access"
```

For versions using the classic sandbox setting, use this mutually exclusive alternative:

```toml
approval_policy = "never"
sandbox_mode = "danger-full-access"
```

Do not combine `default_permissions` with `sandbox_mode` or `[sandbox_workspace_write]`. Full Access removes the Codex
sandbox barrier but does not prevent failures from Git hooks, signing, locks, credentials, operating-system permissions,
or repository configuration. Managed requirements may prohibit Full Access; report that blocker without weakening the
requested Git behavior.

Current references:

- [Configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Permission profiles](https://learn.chatgpt.com/docs/permissions)
- [Agent approvals and security](https://learn.chatgpt.com/docs/agent-approvals-security)

Codex workspace permissions can also protect an existing `.agents` directory. Installing this skill may therefore need
Full Access or an invocation outside the restricted agent sandbox.

## Verify

After execution, inspect the generated outcome and Seed4J project history. If commits were enabled, inspect the working
tree and Git history as appropriate. For an explicitly requested `--no-commit` execution, do not prescribe Git-specific
recovery steps.
