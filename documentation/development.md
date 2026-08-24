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

Release Drafter keeps a draft GitHub Release updated from pull requests merged into `main`. The draft changelog is grouped by PR labels, so release notes are only as precise as the labels applied before merging.

The categories currently used by the draft are:

- `theme: important`
- `area: feature request :bulb:`
- `area: enhancement :wrench:`
- `area: refactoring`
- `area: bug :bug:`
- `area: breaking change`
- `server: spring boot`
- `theme: security`
- `theme: maven`
- `theme: gradle`
- `area: documentation :books:`
- `area: remove`
- `area: invalid`
- `area: spam`
- `area: dependencies`

`pom.xml` is the source of truth for the release version. Prepare a release by committing the Maven version and matching npm metadata, then push a tag with the same version:

```bash
git switch main
./mvnw versions:set -DnewVersion=0.0.2 -DgenerateBackupPoms=false
npm version 0.0.2 --no-git-tag-version
git add pom.xml package.json package-lock.json
git commit -m "chore(release): prepare 0.0.2"
git tag v0.0.2
git push origin main
git push origin v0.0.2
```

The release workflow reads the version from `pom.xml`, rejects snapshot versions, requires the tag to match `v<project.version>`, checks that `package.json` and `package-lock.json` match the Maven version, runs the wrapper tests, builds the JAR, prepares and smoke-tests the npm package, publishes with `npm publish --provenance`, then publishes the matching GitHub Release from the maintained draft. The GitHub Release attaches the versioned JAR, such as `seed4j-cli-0.0.2.jar`, but npm remains the primary installation channel:

```bash
npm install -g seed4j-cli
```

After the release is published, bump `pom.xml`, `package.json`, and `package-lock.json` to the next snapshot development version, for example `0.0.3-SNAPSHOT`, in a follow-up commit.

Before the first Trusted Publishing release, configure the `seed4j-cli` package on npm with this GitHub publisher:

- Repository: `seed4j/seed4j-cli`
- Workflow: `.github/workflows/release.yml`
- Environment: leave empty unless the workflow is later changed to use one

If the package does not exist yet and npm requires a manual first publish, build from source, run `npm run package:prepare`, inspect `npm pack --dry-run`, then publish once from a maintainer machine with `npm publish --provenance` or, if provenance is not available locally, `npm publish`. After that, use the tag workflow for releases.

## Local Sonar analysis

Follow the dedicated [Sonar procedure](sonar.md) for its Docker setup, token handling, analysis command, and local result URL.
