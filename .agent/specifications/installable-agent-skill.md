# Installable Seed4J CLI agent skill

Status: normative implementation specification for
[seed4j-cli#321](https://github.com/seed4j/seed4j-cli/issues/321).

This specification defines the first official `seed4j-cli` agent skill, its installation contract, and the operational
guidance that the installed skill gives coding agents. The terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are
normative.

The guiding principle is that the skill teaches an agent how to ask the installed Seed4J CLI what to do next. It does not
try to contain everything an agent could know about Seed4J.

## Purpose and scope

The skill helps a coding agent use Seed4J CLI as the chosen project generator. It covers initializing a project from a
specification, discovering and applying visible modules, adding Seed4J capabilities to an existing project, and operating
the `seed4j` command safely.

The skill MUST be optional. Installing or running Seed4J CLI MUST NOT depend on a previously installed skill, and the CLI
MUST remain usable by humans, scripts, and agents that read command help directly.

The skill governs only the Seed4J portion of a task. After Seed4J modules have completed, the agent SHOULD verify the
observable result and resume the remaining implementation with its ordinary tools and instructions.

Only the portable `.agents/skills` convention is supported in this increment. The installed skill MUST NOT contain
Codex-specific UI metadata or require a particular agent host.

## Public command contract

The new public interface is:

```text
seed4j skill install [--global]
```

`skill` MUST be a top-level command group, and `install` MUST use the standard help options. Its help MUST describe the
local destination as the default and `--global` as an explicit user-level alternative.

The command MUST NOT accept `--project-path`, search upward for a Git repository, inspect Seed4J history to select a root,
or infer another project directory.

### Destination resolution

| Invocation                      | Destination                                             |
| ------------------------------- | ------------------------------------------------------- |
| `seed4j skill install`          | `<current-working-directory>/.agents/skills/seed4j-cli` |
| `seed4j skill install --global` | `<user-home>/.agents/skills/seed4j-cli`                 |

The local base MUST be the process working directory at invocation time. The global base MUST be the configured
`Seed4JCliHome` derived from `user.home`. The result path MUST be absolute and lexically normalized; resolving symbolic
links or requiring the destination to exist is not necessary.

The local form is the recommended workflow because it scopes discovery to a project that chose Seed4J. The global form
is opt-in for users who want agents to consider Seed4J before a project-local skill exists.

### Success output and status

A successful first installation MUST write exactly one status line to `stdout`:

```text
Installed Seed4J CLI skill at <absolute-normalized-path>.
```

If any filesystem entry already existed at the owned destination when the invocation began, a successful replacement
MUST instead write:

```text
Updated Seed4J CLI skill at <absolute-normalized-path>.
```

An existing installation that already has identical content is still `Updated`. The command does not need a third
`unchanged` state.

### Exit codes and failures

| Exit code | Meaning                                                                                                |
| --------- | ------------------------------------------------------------------------------------------------------ |
| `0`       | The complete bundled skill was installed or updated successfully.                                      |
| `2`       | Picocli usage was invalid. No installation was attempted.                                              |
| `1`       | A bundled-resource, path, staging, publication, restoration, or cleanup operation failed unexpectedly. |

On exit code `1`, the command MUST write a concise diagnostic to `stderr`, MUST NOT print a success line, and MUST NOT
classify the failure as a reason to initialize a project or retry with unrelated options. Stack traces remain available
only through the CLI's existing diagnostic mechanisms.

## Ownership and replacement semantics

The CLI owns the complete `seed4j-cli` entry below the selected `.agents/skills` directory. A successful update MUST
replace that complete entry with the bundled version. Consequently, it MUST:

- remove references or other files that existed in an older installed version but no longer exist in the bundle;
- overwrite manual modifications within the owned entry;
- preserve every sibling skill and every entry outside the owned destination; and
- avoid following a symbolic link found at the owned destination while deleting or replacing that entry.

The command MUST create missing `.agents` and `skills` parent directories. It MUST NOT initialize a Seed4J project, apply
a module, create Seed4J project history, initialize Git, create a Git commit, or perform another workspace setup action.

### Recoverable publication

Before replacing an existing installation, the installer MUST prepare the complete new tree in a temporary sibling under
the selected `skills` directory. A failure reported while reading or staging the bundle MUST leave the previous entry
untouched.

Publication is committed only when the complete staged tree occupies the owned destination. Before that commit point,
any recoverable failure MUST restore the previous installation byte for byte and remove temporary and backup entries.
This includes a failure after the previous entry was moved to a backup but before the staged tree fully occupies the
destination.

After commit, the installer MUST delete the previous-installation backup before reporting success. If that cleanup fails,
the command MUST return exit code `1`, MUST NOT print a success line, and MUST diagnose that the updated skill remains
installed. When a residual backup exists, the diagnostic MUST identify its path. The new complete tree and every sibling
skill MUST remain untouched.

Once deletion of the previous-installation backup has started, cleanup failure is not recoverable within that invocation:
the installer MUST NOT roll the new destination back because the backup may already be partial. Such a failure may leave
only installer-owned operational residue. Temporary and backup entries MUST otherwise be cleaned after success and after
a recoverable failure. These guarantees apply to failures observed and reported by the command; the specification does
not claim crash consistency across process termination, operating-system failure, concurrent installations, or storage
loss.

## Bundling and version correspondence

The canonical skill source MUST live at:

```text
src/main/resources/skills/seed4j-cli/
├── SKILL.md
└── references/
    ├── applying-modules.md
    └── module-set-planning.md
```

These files MUST be copied unfiltered into the Spring Boot JAR. The installer MUST read them from that packaged resource
and MUST NOT download skill files from GitHub or another remote source.

The npm package executes the same JAR, so it MUST NOT carry a second independently maintained copy of the skill. This
single-resource design establishes the version correspondence:

```text
Seed4J CLI version X
        ↓
bundled seed4j-cli skill for version X
```

The first installed tree contains exactly the three files shown above. A future file addition requires updating the
bundle contract so the installer still publishes the complete tree and removes stale files deterministically.

## Skill discovery contract

`SKILL.md` MUST start with this frontmatter:

```yaml
---
name: seed4j-cli
description: Use Seed4J CLI to initialize or modify projects by discovering the active runtime and safely planning and applying modules. Use when Seed4J is the chosen project generator, including implementing a new-project specification, adding Seed4J capabilities, or working with the seed4j command.
---
```

The description enables normal semantic discovery. The skill MUST NOT require explicit-only invocation.

The intended routing boundary is:

| Request or context                                                                   | Expected routing |
| ------------------------------------------------------------------------------------ | ---------------- |
| Implement a new-project specification with Seed4J as the chosen generator            | Trigger          |
| Discover, plan, or apply Seed4J modules                                              | Trigger          |
| Add a Seed4J capability or work directly with the `seed4j` command                   | Trigger          |
| Implement a project specification where the local context already establishes Seed4J | Trigger          |
| Fix an ordinary application bug that does not involve Seed4J modules                 | Do not trigger   |
| Author a new Seed4J module or runtime extension                                      | Do not trigger   |
| Use another project generator explicitly                                             | Do not trigger   |

Agent hosts may load newly installed skills at different times. Portable acceptance requires discovery in a newly
started agent session; this specification does not promise hot reload in an already running session.

## Core workflow in `SKILL.md`

The entrypoint MUST stay concise and route detail progressively. It MUST teach this decision workflow:

1. Establish whether the user asked only for inspection or authorized project changes.
2. Discover the active CLI and runtime with `seed4j --version`, `seed4j list`, and `seed4j --help`.
3. Infer candidate modules only from the user's requirements and the visible active catalog.
4. Inspect `seed4j apply <module> --help` before constructing a module invocation.
5. Ask the user only when a requirement, parameter, or feature-provider choice remains materially ambiguous.
6. Route an individual module to `references/applying-modules.md` and a multi-module outcome to
   `references/module-set-planning.md`.
7. Plan before mutation, evaluate the rendered plan rather than only its exit code, and execute only within the user's
   existing authorization and the host's effective permissions.
8. Verify the outcome, then return control to the surrounding implementation task.

The skill MUST NOT embed a static module catalog, copy the commands guide, select a missing dependency or feature
provider implicitly, or convert a user request for inspection into authorization to mutate.

### Plan-first authorization

Planning is the default before a module mutation. A user request to implement or change the project already authorizes
execution after a valid plan; the agent MUST NOT request a redundant confirmation solely because it ran `--plan`. A user
request for analysis, explanation, or planning does not authorize execution and MUST stop after reporting the plan.

If the plan exposes a material choice not resolved by the request, such as multiple compatible feature providers, the
agent MUST ask for that choice before execution. A plan is a read-only snapshot, not an authorization token or a reserved
future execution.

## Host permissions and commit preflight

Before any mutating Seed4J invocation, `SKILL.md` MUST direct the agent to establish that its host can write the target
project. When commits are enabled, the agent MUST additionally establish that the invoked process can write Git metadata.
A successful read-only plan does not prove either capability.

If commits are enabled and Git-metadata write access is absent or cannot be established, the agent MUST stop before
module execution, explain the missing capability, and request Full Access or an equivalent host permission. It MUST NOT:

- execute once merely to observe the expected permission failure;
- reinterpret pre-existing project changes as permission to continue;
- add `--no-commit` as a workaround; or
- modify agent-host configuration on the user's behalf.

When the user explicitly requests `--no-commit`, Git-metadata write access is not required, but project write access
remains required. The existing meaning of `--no-commit` is unchanged: it skips both Git initialization and Seed4J commit
creation.

### Codex-specific guidance

The detailed Codex note belongs in `references/applying-modules.md`, while `SKILL.md` keeps only the portable capability
check. The reference MUST accurately distinguish approvals from permissions:

- `approval_policy = "never"` prevents approval prompts; it does not grant filesystem or network access.
- The Codex `:workspace` permission profile and `workspace-write` sandbox protect `.git` recursively as read-only, so a
  Seed4J invocation with commits enabled cannot complete its Git write in that mode.
- Full Access removes the Codex sandbox barrier but cannot prevent failures from Git hooks, signing, locks, credentials,
  operating-system permissions, or repository configuration.
- Managed Codex requirements can prohibit Full Access. In that case the agent reports the blocker and does not weaken the
  requested Git behavior.

For Codex versions using permission profiles, the reference MUST show:

```toml
approval_policy = "never"
default_permissions = ":danger-full-access"
```

For Codex versions using the classic sandbox setting, it MUST show the mutually exclusive alternative:

```toml
approval_policy = "never"
sandbox_mode = "danger-full-access"
```

The reference MUST say not to combine `default_permissions` with `sandbox_mode` or `[sandbox_workspace_write]`. It SHOULD
link to the current official documentation rather than reproduce broader Codex configuration guidance:

- [Configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference)
- [Permission profiles](https://learn.chatgpt.com/docs/permissions)
- [Agent approvals and security](https://learn.chatgpt.com/docs/agent-approvals-security)

Codex workspace permissions can also protect an existing `.agents` directory. Public installation documentation SHOULD
therefore explain that running `seed4j skill install` from Codex may require Full Access or an invocation outside the
restricted agent sandbox.

## Individual-module guidance

`references/applying-modules.md` MUST contain the detailed individual-module, parameter-history, dependency, permission,
and Git guidance.

For an individual module, the normal inspection command is:

```text
seed4j apply <module> --plan
```

The reference MUST explain that this plan is read-only and returns exit code `0` even when parameters or dependencies are
pending. The agent MUST inspect the rendered dependency and parameter states; code `0` alone does not mean the module is
ready to apply.

A normal `seed4j apply <module>` checks dependencies before required parameters. A pending module or feature dependency
blocks execution with exit code `2` and no generated files, history entries, or commits.

The agent SHOULD reuse compatible values already shown as coming from project history instead of redundantly passing
them again. Explicit user input takes precedence when the user intentionally overrides history.

Commits are enabled by default. For normal project creation, the skill MUST direct the agent to omit `--no-commit` and
first satisfy the host-permission preflight. It may use `--no-commit` only when the user explicitly asks Seed4J not to
initialize Git or create Seed4J commits.

## Multi-module guidance

`references/module-set-planning.md` MUST contain detailed selection, preflight, provider, exit-code, reapplication, and
partial-failure guidance. When the outcome needs multiple modules, the normal plan is:

```text
seed4j apply-set <modules...> --plan
```

The reference MUST teach these contracts:

- requested order is preserved for reporting, while the Seed4J landscape calculates execution order;
- dependencies and feature providers are never selected implicitly;
- the agent may infer an explicit provider from an unambiguous user requirement, but it must ask when visible candidates
  remain materially ambiguous;
- a plan is read-only and does not authorize, reserve, or cache execution;
- execution performs a fresh preflight and can be invalidated by intervening changes;
- explicitly requested modules remain in the execution order and are reapplied even if history records them;
- explicit CLI input takes precedence over compatible project history; and
- metadata defaults displayed by `apply-set --plan` are informational and are not executed or persisted as effective
  values unless supplied explicitly or obtained from compatible history.

The host permission and default-commit rules apply equally to `apply-set`. The module-set reference MUST route the agent
to the shared Codex permission explanation without duplicating it.

### Exit codes

The skill MUST treat nonzero results as possible command contracts rather than automatically classifying them as a broken
tool:

| Exit code | `apply-set` meaning                                                                      |
| --------- | ---------------------------------------------------------------------------------------- |
| `0`       | The plan is valid, or every selected module succeeded.                                   |
| `2`       | Usage or predictable preflight validation failed before any module or Git mutation.      |
| `1`       | An unexpected pre-execution failure occurred, or execution ended with a partial failure. |

### Git and partial failure

With commits enabled, each successful module creates one commit. Execution is sequential and non-atomic: successes before
the first failure are preserved, the failed module's effects are indeterminate, later modules are skipped, and no
automatic rollback occurs.

After partial failure, the skill MUST direct the agent to inspect the working tree and Seed4J project history. It MUST
also inspect Git history when commits were enabled. For a user-requested `--no-commit` execution, it MUST NOT give
Git-specific recovery guidance.

The skill MUST preserve the CLI's dirty-worktree guidance. Existing changes do not authorize `--no-commit`, do not prove
that a module commit is safe, and do not override the host-permission preflight.

## Responsibility boundaries

| Responsibility                                                                                    | Owner                                 |
| ------------------------------------------------------------------------------------------------- | ------------------------------------- |
| `skill install` syntax, help, option mapping, output streams, and exit codes                      | `seed4j-cli` primary adapter          |
| Typed installation scope, status, result, and installation capability                             | `seed4j-cli` application/domain       |
| Working-directory and home resolution, classpath reads, filesystem staging, publication, recovery | `seed4j-cli` secondary adapter        |
| Canonical skill content and progressive-disclosure routing                                        | bundled skill resources               |
| Runtime, visible catalog, module help, dependency planning, parameters, application, and commits  | installed Seed4J CLI and Seed4J core  |
| Agent-host filesystem, Git-metadata, approval, and sandbox capabilities                           | the active agent host and user policy |

Application and domain code MUST NOT access the filesystem, classpath resources, Spring configuration,
`System.getProperty`, or infrastructure implementations. A raw `--global` boolean belongs only to the primary adapter;
the inward-facing request uses a named scope. A returned path is allowed as a user-visible installation result, but
hidden temporary and backup layouts remain secondary-infrastructure details.

## Documentation requirements

The implementation MUST update the public documentation to:

- present skill installation as optional and local installation as the recommended default;
- document the exact local and global destinations, replacement ownership, output, and exit behavior;
- warn that updates replace manual changes inside the owned `seed4j-cli` directory;
- include an agent-oriented example that installs locally and asks an agent to implement a Seed4J project specification;
- explain that restricted Codex workspace permissions can block `.agents` installation and Git commits;
- state that missing Git permission is fixed by changing host permissions, not by silently adding `--no-commit`; and
- keep detailed command behavior authoritative in CLI help and `documentation/Commands.md` rather than copying that guide
  into the skill.

## Acceptance scenarios

Automated tests MUST validate observable behavior rather than constructor wiring or annotation presence.

| Scenario                                      | Required observation                                                                                                    |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Local first install                           | Exact three-file tree under the isolated working directory; absolute `Installed` output; exit `0`.                      |
| Global first install                          | Exact tree under an isolated `user.home`; working directory untouched; absolute `Installed` output; exit `0`.           |
| Reinstall identical bundle                    | `Updated` output and exit `0`; no `unchanged` state.                                                                    |
| Reinstall over stale and modified content     | Stale files removed and owned files restored from the bundle; sibling skill preserved.                                  |
| Staging failure                               | Exit `1`, no success output, and byte-equivalent previous installation preserved.                                       |
| Publication failure with recoverable previous | Exit `1` and previous installation restored; sibling skills preserved.                                                  |
| Backup cleanup failure after commit           | Exit `1`, no success output, complete updated tree preserved, sibling skills preserved, and residual backup diagnosed.  |
| Installation side effects                     | No `.seed4j` history, Git initialization, commit, module application, or network dependency.                            |
| Packaged JAR                                  | Real Spring Boot JAR contains the exact canonical skill resources and installs them offline.                            |
| Packed npm artifact                           | Real tarball installs into an isolated prefix; its `seed4j` binary performs local and global skill installation.        |
| Semantic trigger                              | Approved trigger cases match the frontmatter boundary; ordinary edits, module authorship, and another generator do not. |
| Codex Full Access with commits                | Skill guidance permits execution after a valid plan and ordinary authorization checks.                                  |
| Codex workspace-only with commits             | Skill stops before module application, explains Git permission, and does not add `--no-commit`.                         |
| Codex `never` without Full Access             | Skill recognizes that no elevation prompt is available and reports the permission blocker.                              |
| Explicit user `--no-commit`                   | Skill requires project writes but does not require Git-metadata writes or give Git-specific recovery advice.            |

The packaging-level npm test MUST pack and install the real prepared artifact rather than inspect repository source paths
or substitute a test fixture for the JAR.

## Rejected alternatives and explicit limits

The following are rejected for this increment:

- installing globally by default;
- searching for a Git or Seed4J root instead of using the exact working directory;
- downloading the latest skill independently from the installed CLI version;
- preserving arbitrary files or manual changes inside the owned `seed4j-cli` entry;
- leaving stale references after an update;
- duplicating the skill in npm outside the packaged JAR;
- installing into `.claude/skills` or another agent-specific destination;
- adding Codex-only `agents/openai.yaml` metadata;
- embedding the module catalog or copying `documentation/Commands.md`;
- authoring Seed4J modules or runtime extensions;
- selecting dependencies or feature providers implicitly;
- treating `approval_policy = "never"` as a filesystem permission;
- attempting a commit under known-insufficient host permissions;
- automatically adding `--no-commit` to bypass a sandbox; and
- defining an E2E agent harness, subagent model, or task-coordination architecture.

After implementation, automated tests become the executable proof of installation and packaging behavior, CLI help and
public documentation become the operational reference, and this specification remains the canonical record of product
decisions, safety guarantees, trigger boundaries, permission requirements, and rejected alternatives.
