# Technical Debt

## Open items

### 1. Maven dependency convergence warnings

- Status: open
- First noted: 2026-03-12
- Reproduce: `./mvnw clean verify`

The Maven Enforcer plugin reports dependency convergence warnings during the build. Current conflicts include:

- `org.checkerframework:checker-qual`
- `org.codehaus.plexus:plexus-utils`
- `com.google.errorprone:error_prone_annotations`
- `commons-io:commons-io`
- `org.apache.commons:commons-text`

Why this matters:

- version drift can hide classpath instability;
- the warnings make build output noisier and easier to ignore;
- this can become a CI failure if enforcement is tightened later.

Suggested follow-up:

- inspect the full dependency tree with `./mvnw dependency:tree`;
- align transitive versions explicitly in `pom.xml` where needed;
- decide whether the project wants strict convergence or documented exceptions.

### 2. Mockito self-attach warning on JDK 25

- Status: open
- First noted: 2026-03-12
- Reproduce: `./mvnw clean verify`

Test execution prints a warning that Mockito is self-attaching to enable the inline mock maker, and that this will stop working in a future JDK release.

Why this matters:

- future JDK updates can break test execution;
- the warning adds noise to every test run;
- it signals that test runtime configuration is incomplete.

Suggested follow-up:

- configure Mockito as a proper Java agent in the Maven test setup;
- verify whether the project still needs the inline mock maker everywhere;
- document the final setup so future JDK upgrades do not reintroduce the warning.
