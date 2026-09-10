# Contributor and maintainer guide

Use this guide when changing, validating, packaging, or releasing `seed4j-cli`. The [commands reference](Commands.md) remains the canonical source for user-facing CLI behavior.

## Prerequisites

Use Java 25 and Node.js 22 or higher before running the toolchain.

[Node.js](https://nodejs.org/) is used to install development tools, format the repository, and prepare the npm package. Depending on your system, you can install Node either from source or as a pre-packaged bundle.

After installing Node, run this command to install development tools. You only need to run it again when dependencies change in [`package.json`](../package.json):

```
npm install
```

## Local startup

Start the CLI from the repository root with:

```bash
./mvnw
```

## Build from source

Clone this project and go into the folder:

```bash
git clone https://github.com/seed4j/seed4j-cli
cd seed4j-cli
```

Build the Java package and prepare the npm package contents:

```bash
./mvnw --batch-mode -ntp clean package
npm run package:prepare
npm run test:npm-packed-skill
```

The preparation step copies the Maven-built JAR to `dist/seed4j-cli.jar`, which is the JAR shipped by npm.

To inspect the npm package before publishing:

```bash
npm pack --dry-run
```

To install the local package globally for a smoke test:

```bash
npm install -g .
seed4j --version
```

## Mutation testing

Mutation testing with PIT is opt-in because it runs the test suite repeatedly and can take significantly longer than a normal build. Run it explicitly with:

```bash
./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage
```

PIT writes its HTML report under `target/pit-reports/`. A surviving mutant is a small behavioral change that the current tests did not detect; investigate whether it reveals a meaningful missing behavior test, but do not add implementation-detail tests solely to increase the mutation score.

## Release to npm

The npm package name is `seed4j-cli`, and it exposes the command `seed4j`.

Release Drafter updates the next draft GitHub Release whenever a pull request is merged into `main`. It groups pull
requests using the labels and categories in `.github/release-drafter.yml`, preserving the categorized release notes,
links, and contributors until that draft is published.

Every successful `main` build also starts the automatic publication workflow. Semantic-release examines commits after
the latest `v*` tag and applies ordinary Semantic Versioning:

| Change                                                                  | Release |
| ----------------------------------------------------------------------- | ------- |
| `fix`, `perf`, `revert`, or a shipped runtime dependency update         | patch   |
| `feat`                                                                  | minor   |
| `!` after the type/scope or a `BREAKING CHANGE:` footer                 | major   |
| `build`, `chore`, `ci`, `docs`, `refactor`, `style`, or `test` alone    | none    |
| Build, test, formatting, and lock-file dependency maintenance by itself | none    |

There is no special rule keeping the project below `1.0.0`. For example, a breaking change after `0.1.0` releases
`1.0.0`. Conventional Commit validation runs in CI so malformed commit messages cannot silently bypass release
classification.

Renovate uses `fix(deps)` for Maven dependencies shipped at runtime and `chore(deps)` for build or test tooling. An
update to `com.seed4j:seed4j`, Spring Boot, JGit, or another runtime dependency therefore releases a patch after its
checks pass. If a major dependency update changes the CLI contract incompatibly, mark the merge commit with `!` or a
`BREAKING CHANGE:` footer instead.

Git tags are the release-version source of truth. `pom.xml`, `package.json`, and `package-lock.json` intentionally stay
at `0.0.0-SNAPSHOT` in source. When a release is required, semantic-release calculates the stable version, prepares the
ephemeral checkout, creates the tag, and publishes the npm package through Trusted Publishing. Release Drafter then
refreshes and publishes the existing draft with that exact version, and the workflow attaches a versioned JAR such as
`seed4j-cli-0.1.0.jar`.

npm remains the primary stable installation channel:

```bash
npm install -g seed4j-cli
```

There is no npm `next` channel. To inspect or run an unreleased `main` revision, check out that source revision and use
the build instructions in this guide.

The `seed4j-cli` npm Trusted Publisher must keep this GitHub identity:

- Repository: `seed4j/seed4j-cli`
- Workflow: `.github/workflows/release.yml`
- Environment: empty

Keep the existing Release Drafter draft. It will continue accumulating merged pull requests and will be published by the
next automatic or manual release.

### Publishing current main manually

Automatic publication is the normal path. To explicitly publish the current `main`, open **Actions**, select the
**release** workflow, choose **Run workflow** from `main`, select `operation: release`, leave `version` empty, and run the
workflow.

The manual operation uses the same Conventional Commit analysis as automatic publication. A fix remains patch, a
feature remains minor, and a breaking change remains major. Only when the accumulated commits would normally produce no
release does the manual operation force a patch. The workflow rejects manual publication unless the selected revision
is the current `main`, its push build succeeded, and no stable release tag already points to it.

Do not use the GitHub draft's **Publish release** button. That would bypass the coordinated npm publication, artifact
preparation, and recovery guarantees.

### Recovering a partial release

Publication spans several systems and can fail after the tag exists but before npm, the GitHub Release, or the JAR is
available. Recovery completes that existing version; it never chooses or creates a new version.

If publication fails before pushing its tag, rerun the normal release for the same successful `main` revision. If the
`v*` tag already exists, open **Actions**, select the **release** workflow, choose **Run workflow** from `main`, select
`operation: recover`, enter the stable `version` without the `v` prefix, and run the workflow. Recovery verifies that
the immutable tag belongs to `main`, rebuilds from that tag, and then:

- publishes the npm package only when that exact version is absent;
- publishes the maintained Release Drafter draft only when the GitHub Release is absent;
- uploads the versioned JAR only when the release does not already contain it.

If the GitHub Release already exists, recovery preserves its published notes. Never move or delete the release tag,
unpublish a released npm version, or select a replacement version during recovery.

## Local Sonar analysis

Follow the dedicated [Sonar procedure](sonar.md) for its Docker setup, token handling, analysis command, and local result URL.
