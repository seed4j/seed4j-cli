const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const test = require('node:test');

const { distributionIdentity, validateDistributionBuild } = require('../../scripts/distribution-identity-policy.cjs');

const repositoryRoot = resolve(__dirname, '../..');

test('the current main-bound POM has one stable Maven authority and no personal snapshot declaration', () => {
  const pom = read('pom.xml');
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');

  const identity = validateDistributionBuild({ metadata, pom });

  assert.deepEqual(
    { ...identity, version: '<authoritative-version>' },
    {
      artifactId: 'seed4j',
      channel: 'stable',
      groupId: 'com.seed4j',
      repositoryUrl: 'https://repo.maven.apache.org/maven2',
      unavailableModules: '',
      upstreamCommit: '',
      version: '<authoritative-version>',
    },
  );
  assert.match(identity.version, /^\d+\.\d+\.\d+$/);
  assert.equal((pom.match(/<groupId>\$\{seed4j\.group-id\}<\/groupId>/g) ?? []).length, 2);
  assert.equal((pom.match(/<artifactId>\$\{seed4j\.artifact-id\}<\/artifactId>/g) ?? []).length, 2);
  assert.equal((pom.match(/<version>\$\{seed4j\.version\}<\/version>/g) ?? []).length, 2);
  assert.doesNotMatch(pom, /io\.github\.renanfranca|seed4j-main-snapshot/);
  assert.doesNotMatch(pom, /central\.sonatype\.com\/repository\/maven-snapshots/);
  assert.doesNotMatch(pom, /<id>experimental<\/id>/);
  assert.equal(
    metadata,
    `release-channel=@seed4j.release-channel@
seed4j-dependency-coordinate=@seed4j.group-id@:@seed4j.artifact-id@:@seed4j.version@
seed4j-upstream-commit=@seed4j.upstream-commit@
unavailable-modules=@seed4j.unavailable-modules@
`,
  );
});

test('the same policy accepts a future branch-owned experimental identity and snapshot-only repository', () => {
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const pom = experimentalBranchPom(read('pom.xml'));

  assert.deepEqual(
    {
      ...validateDistributionBuild({ metadata, pom }),
      upstreamCommit: '<authoritative-upstream-sha>',
      version: '<authoritative-version>',
    },
    {
      artifactId: 'seed4j-main-snapshot',
      channel: 'experimental',
      groupId: 'io.github.renanfranca',
      repositoryUrl: 'https://central.sonatype.com/repository/maven-snapshots/',
      unavailableModules: 'seed4j-extension',
      upstreamCommit: '<authoritative-upstream-sha>',
      version: '<authoritative-version>',
    },
  );
  assert.deepEqual(distributionIdentity(pom), validateDistributionBuild({ metadata, pom }));
  assert.match(pom, /<releases>[\s\S]*?<enabled>false<\/enabled>/);
  assert.match(pom, /<snapshots>[\s\S]*?<enabled>true<\/enabled>/);
});

test('an experimental snapshot version cannot pass with stale or incomplete full upstream provenance', () => {
  const pom = experimentalBranchPom(read('pom.xml'));
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');

  assert.throws(
    () =>
      validateDistributionBuild({
        metadata,
        pom: pom.replace('4eebd07bce14c9a6ac70bace157fcc616133e950', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'),
      }),
    /snapshot version.*full upstream SHA/i,
  );
  assert.throws(
    () =>
      validateDistributionBuild({
        metadata,
        pom: pom.replace(
          '<seed4j.upstream-commit>4eebd07bce14c9a6ac70bace157fcc616133e950</seed4j.upstream-commit>',
          '<seed4j.upstream-commit />',
        ),
      }),
    /full upstream SHA/i,
  );
});

test('Renovate updates the authoritative stable and experimental Maven properties and leaves stale provenance red', () => {
  const stablePom = read('pom.xml');
  const experimentalPom = experimentalBranchPom(stablePom);
  const renovate = JSON.parse(read('renovate.json'));
  const managers = Object.fromEntries(renovate.customManagers.map(manager => [manager.depNameTemplate, manager]));

  assert.match(stablePom, new RegExp(managers['com.seed4j:seed4j'].matchStrings[0]));
  assert.doesNotMatch(stablePom, new RegExp(managers['io.github.renanfranca:seed4j-main-snapshot'].matchStrings[0]));
  assert.doesNotMatch(experimentalPom, new RegExp(managers['com.seed4j:seed4j'].matchStrings[0]));
  assert.match(experimentalPom, new RegExp(managers['io.github.renanfranca:seed4j-main-snapshot'].matchStrings[0]));
  assert.equal(
    managers['io.github.renanfranca:seed4j-main-snapshot'].registryUrlTemplate,
    'https://central.sonatype.com/repository/maven-snapshots/',
  );
  assert.ok(renovate.extends.includes(':automergeRequireAllStatusChecks'));
});

test('builds and releases use the checked-out branch POM without profile-owned identity selection', () => {
  const build = read('.github/workflows/github-actions.yml');
  const release = read('scripts/prepare-release.js');

  assert.doesNotMatch(build, /SEED4J_MAVEN_PROFILE|-Pexperimental/);
  assert.match(build, /\.\/mvnw --batch-mode -ntp clean verify/);
  assert.doesNotMatch(release, /mavenProfile|mavenArguments|-Pexperimental/);
  assert.match(release, /run\('\.\/mvnw', \['--batch-mode', '-ntp', 'clean', 'package'\]\)/);
});

function experimentalBranchPom(stablePom) {
  const upstreamSha = '4eebd07bce14c9a6ac70bace157fcc616133e950';
  const repository = `  <repositories>
    <repository>
      <id>seed4j-main-snapshots</id>
      <url>\${seed4j.repository-url}</url>
      <releases>
        <enabled>false</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>

`;
  return stablePom
    .replace(
      '<!-- renovate: datasource=maven depName=com.seed4j:seed4j -->',
      '<!-- renovate: datasource=maven depName=io.github.renanfranca:seed4j-main-snapshot registryUrl=https://central.sonatype.com/repository/maven-snapshots/ -->',
    )
    .replace('<seed4j.group-id>com.seed4j</seed4j.group-id>', '<seed4j.group-id>io.github.renanfranca</seed4j.group-id>')
    .replace('<seed4j.artifact-id>seed4j</seed4j.artifact-id>', '<seed4j.artifact-id>seed4j-main-snapshot</seed4j.artifact-id>')
    .replace(
      /<seed4j\.version>[^<]+<\/seed4j\.version>/,
      '<seed4j.version>2.2.1-main.20260907.055800.4eebd07bce14-SNAPSHOT</seed4j.version>',
    )
    .replace('<seed4j.release-channel>stable</seed4j.release-channel>', '<seed4j.release-channel>experimental</seed4j.release-channel>')
    .replace('<seed4j.upstream-commit />', `<seed4j.upstream-commit>${upstreamSha}</seed4j.upstream-commit>`)
    .replace(
      '<seed4j.repository-url>https://repo.maven.apache.org/maven2</seed4j.repository-url>',
      '<seed4j.repository-url>https://central.sonatype.com/repository/maven-snapshots/</seed4j.repository-url>',
    )
    .replace('<seed4j.unavailable-modules />', '<seed4j.unavailable-modules>seed4j-extension</seed4j.unavailable-modules>')
    .replace('  <build>', `${repository}  <build>`);
}

function read(relativePath) {
  return readFileSync(resolve(repositoryRoot, relativePath), 'utf8');
}
