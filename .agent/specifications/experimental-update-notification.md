# Experimental update notifications

Status: approved normative specification based on the Seed4J CLI experimental update-notification plan and its
agent-skill follow-up. The terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative.

This specification adds discovery of newer experimental npm releases and of installed agent skills that differ from the
skill bundled with the active CLI. It supplements the [experimental channel contract](experimental-seed4j-main-channel.md)
and the [installable agent skill contract](installable-agent-skill.md).

## Purpose and scope

An experimental release has little value to an existing user who never learns that it was published. An agent can also
continue using an older installed skill after the npm package is updated, because npm does not replace skill files
installed in a project or user directory. Successful behavior is a brief, actionable notice that reaches terminal users
and agents without changing the work they asked the CLI to perform.

These notices apply when invoking the experimental CLI through its npm launcher. A direct JAR invocation and the stable
npm channel are outside this notification contract. The CLI MUST NOT install an npm release or replace an installed skill
as a side effect of checking or displaying a notice.

## New experimental release

The npm launcher MUST identify its installed package version and periodically read the `experimental` dist-tag for
`seed4j-cli` from the npm registry. It MUST notify only when the tagged experimental version is newer than the installed
experimental version. Comparison MUST respect the numeric semantic-version components and experimental prerelease
sequence; a tag moved to the same or an earlier version MUST NOT be presented as an update. An invalid or missing tag is
an unavailable check, not an update.

A successful registry result MAY be reused for up to six hours. The launcher MUST start a detached Node worker alongside
the Java command. The worker MUST use a five-second deadline covering the response body and MUST end within ten seconds
as an additional safeguard. It only checks the registry and saves the cache. After Java succeeds, the launcher MUST
read the cache again before deciding whether to display an update notice. Completion of the Java command MUST NOT wait
for the worker. A result that arrives later MAY first produce a notice on a subsequent invocation.

After consecutive registry failures, the next check MUST wait one, five, fifteen, then sixty minutes, continuing at
sixty-minute intervals. A successful check MUST reset this sequence. These waits make a check eligible on the next CLI
invocation; the launcher MUST NOT schedule additional attempts. The cache MUST retain the last successful `version` and
`checkedAt` across failures without extending their freshness or recording a notice. It MUST separately record the
attempt time, failure time and category (timeout, network, HTTP, invalid response, or worker launch), consecutive failure
count, and next eligible attempt. A legacy `failedAt` without these fields counts as the first failure with an unknown
cause and a one-minute wait.

The launcher MUST reserve one check per user atomically with a token and a 30-second lease. An expired lease MUST be
recoverable, and a worker holding an older token MUST NOT overwrite the result of a later reservation. Failed and stale
results MAY remain in the cache for investigation, but stale results MUST NOT produce update notices.

While a newer release remains available, the launcher MUST display its notice at most once per rolling 24-hour period
for that installed package and available version. A new available version MAY be reported on the next eligible
invocation. The notice MUST identify the installed and available versions and give the exact opt-in action
`npm install -g seed4j-cli@experimental`. It MUST describe that action as available after the current work, without
instructing an agent to interrupt or update automatically.

## Installed agent skill

The experimental npm invocation MUST check only these known skill destinations:

- `<current-working-directory>/.agents/skills/seed4j-cli`; and
- `<user-home>/.agents/skills/seed4j-cli`.

The CLI MUST compare an existing destination's complete skill contents with the skill bundled in the active CLI version.
It MUST NOT infer freshness from the npm version alone: an npm upgrade does not update an installed skill. A missing skill
does not require a notice. A skill with changed, missing, or extra files MUST be reported as **different from the bundled
skill**, without assuming whether it is old or manually modified. The check MUST be read-only and MUST NOT follow a
symbolic link at a skill destination into another tree. It MUST also detect symbolic links within an installed skill
without intentionally traversing their targets. On Windows, these no-follow requirements apply to links present when
each path is inspected; a symbolic link substituted concurrently between path inspection and traversal MAY be followed
during this advisory check. This narrow exception reflects the lack of a no-follow directory-handle traversal in the
Node.js Windows filesystem API and does not apply on Linux or macOS. An unreadable destination MUST NOT block the
command or be reported as confidently different.

For each differing destination, the CLI MUST display a notice at most once per rolling 24-hour period while the
difference persists. The notice MUST identify the affected destination and the appropriate later action:
`seed4j skill install` for the local destination or `seed4j skill install --global` for the user-level destination. It
MUST NOT run either command automatically. The existing installer contract still applies: installing the bundled skill
replaces the entire owned destination, including manual edits.

The bundled `SKILL.md` MUST tell an agent that, when it sees either update notice, it should finish the current task
normally, refrain from updating the CLI or skill on its own, and relay the notice and command to the user when reporting
the result. This instruction supports future skills; discovery of an old skill MUST NOT depend on the installed skill
already containing it.

## Output and failure contract

Each notice MUST be a concise, clearly informational line on `stderr`, including when the caller has no interactive
terminal. Notices MUST NOT change command `stdout`, arguments, exit code, or signal propagation. They MAY appear only
after a command succeeds. They MUST NOT appear during shell-completion generation, after a failed or signaled command,
or as part of `seed4j skill install` output. The CLI MUST preserve the current command's normal diagnostic and output
contract.

Registry errors, offline operation, malformed responses, missing or corrupt notification cache, cache write failures,
and skill inspection failures MUST remain silent and MUST NOT delay or fail the requested command. A cached newer version
MUST NOT be presented as current after its six-hour validity has elapsed without a successful refresh. Notification state
MUST be stored outside the project and MUST NOT modify generated files, project history, Git state, or an installed skill.

## Acceptance and validation

Observable tests MUST establish that:

- a newer experimental tag produces an actionable notice, while an equal, older, missing, or malformed tag does not;
- the notification and registry-check intervals hold across invocations, and network failure or slowness preserves
  command output and completion time;
- a missing or identical skill produces no notice; differing local and global skills identify their own destinations and
  refresh commands without being rewritten;
- terminal and agent-style non-interactive invocations can receive notices, but completion, failed commands, and
  signaled commands do not; and
- normal Java argument forwarding, `stdout`, exit codes, and signal behavior remain intact.

The commands reference and experimental-channel guide MUST describe both notices, the absence of automatic updates,
and how to refresh an installed skill. The npm package check and packaged-skill check MUST validate the shipped behavior
and guidance. The complete Maven verification gate remains governed by the repository's agent validation policy.
Node npm tests validate the launcher behavior; Java PIT mutation testing is outside the scope of this Node-only change.
