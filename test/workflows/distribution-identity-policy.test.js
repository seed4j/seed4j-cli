const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const test = require('node:test');

const { distributionIdentity, validateDistributionBuild } = require('../../scripts/distribution-identity-policy.cjs');

const repositoryRoot = resolve(__dirname, '../..');

test('the current main-bound POM has one stable Maven authority and no personal snapshot declaration', () => {
  const pom = read('pom.xml');
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const expectedVersion = /<seed4j\.version>([^<]+)<\/seed4j\.version>/.exec(pom)[1].trim();

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
  assert.equal(identity.version, expectedVersion);
  assert.match(identity.version, /^\d+\.\d+\.\d+$/);
  const dependencies = seed4jDependencies(pom);
  assert.equal(dependencies.length, 2);
  assert.match(dependencies[0], /<groupId>\$\{seed4j\.group-id\}<\/groupId>/);
  assert.match(dependencies[0], /<artifactId>\$\{seed4j\.artifact-id\}<\/artifactId>/);
  assert.match(dependencies[0], /<version>\$\{seed4j\.version\}<\/version>/);
  assert.doesNotMatch(dependencies[0], /<scope>|<type>|<classifier>/);
  assert.match(dependencies[1], /<groupId>\$\{seed4j\.group-id\}<\/groupId>/);
  assert.match(dependencies[1], /<artifactId>seed4j-test<\/artifactId>/);
  assert.match(dependencies[1], /<version>\$\{seed4j\.version\}<\/version>/);
  assert.match(dependencies[1], /<scope>test<\/scope>/);
  assert.doesNotMatch(dependencies[1], /<type>|<classifier>/);
  assert.doesNotMatch(pom, /io\.github\.renanfranca|seed4j-main-snapshot/);
  assert.doesNotMatch(pom, /central\.sonatype\.com\/repository\/maven-snapshots/);
  assert.doesNotMatch(pom, /<id>experimental<\/id>/);
  assert.equal(
    metadata,
    `release-channel=@seed4j.release-channel@
seed4j-dependency-coordinate=@seed4j.group-id@:@seed4j.artifact-id@:@seed4j.version@
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
  const dependencies = seed4jDependencies(pom);
  assert.equal(dependencies.length, 2);
  assert.match(dependencies[1], /<artifactId>\$\{seed4j\.artifact-id\}<\/artifactId>/);
  assert.match(dependencies[1], /<type>test-jar<\/type>/);
  assert.match(dependencies[1], /<classifier>tests<\/classifier>/);
  assert.match(dependencies[1], /<scope>test<\/scope>/);
  assert.match(pom, /<releases>[\s\S]*?<enabled>false<\/enabled>/);
  assert.match(pom, /<snapshots>[\s\S]*?<enabled>true<\/enabled>/);
});

test('the policy derives full provenance from a full-SHA snapshot version without legacy metadata', () => {
  const upstreamSha = '4eebd07bce14c9a6ac70bace157fcc616133e950';
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const pom = experimentalBranchPom(read('pom.xml'));

  assert.deepEqual(validateDistributionBuild({ metadata, pom }), {
    artifactId: 'seed4j-main-snapshot',
    channel: 'experimental',
    groupId: 'io.github.renanfranca',
    repositoryUrl: 'https://central.sonatype.com/repository/maven-snapshots/',
    unavailableModules: 'seed4j-extension',
    upstreamCommit: upstreamSha,
    version: `2.2.1-main.20260907.055800.${upstreamSha}-SNAPSHOT`,
  });
});

test('an experimental snapshot version cannot pass with incomplete or invalid full upstream provenance', () => {
  const pom = experimentalBranchPom(read('pom.xml'));
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');

  assert.throws(
    () =>
      validateDistributionBuild({
        metadata,
        pom: pom.replace('4eebd07bce14c9a6ac70bace157fcc616133e950', '4eebd07bce14'),
      }),
    /full upstream SHA/i,
  );
  assert.throws(
    () =>
      validateDistributionBuild({
        metadata,
        pom: pom.replace('4eebd07bce14c9a6ac70bace157fcc616133e950', '4EEBD07BCE14C9A6AC70BACE157FCC616133E950'),
      }),
    /full upstream SHA/i,
  );
  assert.throws(
    () =>
      validateDistributionBuild({
        metadata,
        pom: pom.replace(
          '<seed4j.release-channel>experimental</seed4j.release-channel>',
          '<seed4j.release-channel>experimental</seed4j.release-channel>\n    <seed4j.upstream-commit />',
        ),
      }),
    /legacy upstream commit/i,
  );
});

for (const channel of ['stable', 'experimental']) {
  const pom = channel === 'stable' ? read('pom.xml') : experimentalBranchPom(read('pom.xml'));
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');

  for (const [index, role] of ['runtime', 'test support'].entries()) {
    for (const [field, property, literal] of [
      ['groupId', 'seed4j.group-id', channel === 'stable' ? 'com.seed4j' : 'io.github.renanfranca'],
      ['version', 'seed4j.version', distributionIdentity(pom).version],
    ]) {
      for (const [label, replacement] of [
        ['literal', `<${field}>${literal}</${field}>`],
        ['divergent', `<${field}>different-value</${field}>`],
        ['missing', ''],
        ['empty', `<${field} />`],
        ['duplicate', `<${field}>${'${' + property + '}'}</${field}>`.repeat(2)],
      ]) {
        test(`${channel} ${role} rejects ${label} ${field} with a field-specific diagnostic`, () => {
          assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
          const invalidPom = changeDependency(pom, index, dependency =>
            dependency.replace(new RegExp(`<${field}>[^<]*</${field}>`), replacement),
          );

          assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`${role} dependency.*${field}`, 'i'));
        });
      }
    }

    for (const operation of ['missing', 'duplicate']) {
      test(`${channel} requires exactly one direct ${role} dependency: ${operation}`, () => {
        assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
        const invalidPom = changeDependency(pom, index, dependency => (operation === 'missing' ? '' : dependency.repeat(2)));

        assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`${role} dependency.*exactly once`, 'i'));
      });
    }

    test(`${channel} ${role} rejects a divergent artifactId`, () => {
      assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
      const invalidPom = changeDependency(pom, index, dependency =>
        dependency.replace(/<artifactId>[^<]*<\/artifactId>/, '<artifactId>different-artifact</artifactId>'),
      );

      assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`${role} dependency.*artifactId`, 'i'));
    });

    for (const context of ['comment', 'dependency management', 'plugin', 'profile']) {
      test(`${channel} ${role} cannot be supplied only by ${context}`, () => {
        assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
        const [runtime, support] = seed4jDependencies(pom);
        const dependency = index === 0 ? runtime : support;
        const invalidPom = addDependencyDecoy(
          changeDependency(pom, index, () => ''),
          context,
          dependency,
        );

        assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`${role} dependency.*exactly once`, 'i'));
      });

      test(`${channel} ${role} field tokens in ${context} cannot compensate for a literal groupId`, () => {
        assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
        const original = seed4jDependencies(pom)[index];
        const invalidPom = addDependencyDecoy(
          changeDependency(pom, index, dependency =>
            dependency.replace('<groupId>${seed4j.group-id}</groupId>', `<groupId>${distributionIdentity(pom).groupId}</groupId>`),
          ),
          context,
          original,
        );

        assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`${role} dependency.*groupId`, 'i'));
      });
    }
  }

  for (const scope of ['', 'compile', 'runtime', 'provided']) {
    test(`${channel} test support rejects scope '${scope}'`, () => {
      assert.equal(validateDistributionBuild({ metadata, pom }).channel, channel);
      const invalidPom = changeDependency(pom, 1, dependency =>
        dependency.replace('<scope>test</scope>', scope ? `<scope>${scope}</scope>` : ''),
      );

      assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), /test support dependency.*scope/i);
    });
  }

  test(`${channel} ignores extra Seed4J tokens outside direct project dependencies`, () => {
    const expectedIdentity = validateDistributionBuild({ metadata, pom });
    const withDecoys = ['comment', 'dependency management', 'plugin', 'profile'].reduce(
      (currentPom, context) => addDependencyDecoy(currentPom, context, seed4jDependencies(pom).join('\n')),
      pom,
    );

    assert.deepEqual(validateDistributionBuild({ metadata, pom: withDecoys }), expectedIdentity);
  });
}

for (const [label, change] of [
  [
    'old test-jar',
    dependency =>
      dependency
        .replace('<artifactId>seed4j-test</artifactId>', '<artifactId>${seed4j.artifact-id}</artifactId>')
        .replace('<scope>test</scope>', '<type>test-jar</type><classifier>tests</classifier><scope>test</scope>'),
  ],
  ['test-jar type', dependency => dependency.replace('<scope>', '<type>test-jar</type><scope>')],
  ['tests classifier', dependency => dependency.replace('<scope>', '<classifier>tests</classifier><scope>')],
]) {
  test(`stable test support rejects ${label}`, () => {
    const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
    const pom = read('pom.xml');
    assert.equal(validateDistributionBuild({ metadata, pom }).channel, 'stable');
    const invalidPom = changeDependency(pom, 1, change);

    assert.throws(
      () => validateDistributionBuild({ metadata, pom: invalidPom }),
      /test support dependency.*(?:artifactId|type|classifier)/i,
    );
  });
}

for (const [label, change, field] of [
  [
    'stable seed4j-test format',
    dependency =>
      dependency
        .replace('<artifactId>${seed4j.artifact-id}</artifactId>', '<artifactId>seed4j-test</artifactId>')
        .replace('<type>test-jar</type>', '')
        .replace('<classifier>tests</classifier>', ''),
    'artifactId',
  ],
  ['missing type', dependency => dependency.replace('<type>test-jar</type>', ''), 'type'],
  ['jar type', dependency => dependency.replace('<type>test-jar</type>', '<type>jar</type>'), 'type'],
  ['missing classifier', dependency => dependency.replace('<classifier>tests</classifier>', ''), 'classifier'],
  ['wrong classifier', dependency => dependency.replace('<classifier>tests</classifier>', '<classifier>other</classifier>'), 'classifier'],
]) {
  test(`experimental test support rejects ${label}`, () => {
    const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
    const pom = experimentalBranchPom(read('pom.xml'));
    assert.equal(validateDistributionBuild({ metadata, pom }).channel, 'experimental');
    const invalidPom = changeDependency(pom, 1, change);

    assert.throws(() => validateDistributionBuild({ metadata, pom: invalidPom }), new RegExp(`test support dependency.*${field}`, 'i'));
  });
}

test('stable test support accepts an explicit jar type and the identity still describes the runtime', () => {
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const pom = read('pom.xml');
  const explicitJar = changeDependency(pom, 1, dependency => dependency.replace('<scope>', '<type>jar</type><scope>'));

  assert.deepEqual(validateDistributionBuild({ metadata, pom: explicitJar }), distributionIdentity(pom));
});

test('Renovate tracks the experimental snapshot with one active native Maven rule', () => {
  const stablePom = read('pom.xml');
  const experimentalPom = experimentalBranchPom(stablePom);
  const renovate = JSON.parse(read('renovate.json'));
  const managers = Object.fromEntries(renovate.customManagers.map(manager => [manager.depNameTemplate, manager]));
  const rule = renovate.packageRules.find(rule => rule.description === 'Track personal Seed4J snapshots on experimental');

  assert.match(stablePom, new RegExp(managers['com.seed4j:seed4j'].matchStrings[0]));
  assert.doesNotMatch(experimentalPom, new RegExp(managers['com.seed4j:seed4j'].matchStrings[0]));
  assert.equal(managers['io.github.renanfranca:seed4j-main-snapshot'], undefined);
  assert.equal('customDatasources' in renovate, false);
  assert.deepEqual(rule, {
    description: 'Track personal Seed4J snapshots on experimental',
    enabled: true,
    matchBaseBranches: ['experimental'],
    matchDatasources: ['maven'],
    matchManagers: ['maven'],
    matchPackageNames: ['io.github.renanfranca:seed4j-main-snapshot'],
    registryUrls: ['https://central.sonatype.com/repository/maven-snapshots/'],
    ignoreUnstable: false,
    automerge: true,
    automergeType: 'pr',
    rebaseWhen: 'behind-base-branch',
    semanticCommitType: 'fix',
    semanticCommitScope: 'deps',
  });
  assert.equal(
    renovate.packageRules.filter(
      candidate =>
        candidate.matchPackageNames?.includes('io.github.renanfranca:seed4j-main-snapshot')
        && candidate.matchBaseBranches?.includes('experimental'),
    ).length,
    1,
  );
  assert.ok(renovate.extends.includes(':automergeRequireAllStatusChecks'));
});

test('builds and releases use the checked-out branch POM without profile-owned identity selection', () => {
  const build = read('.github/workflows/github-actions.yml');
  const release = read('scripts/prepare-release.js');

  assert.doesNotMatch(build, /SEED4J_MAVEN_PROFILE|-Pexperimental/);
  assert.match(build, /\.\/mvnw --batch-mode clean verify/);
  assert.doesNotMatch(release, /mavenProfile|mavenArguments|-Pexperimental/);
  assert.match(release, /run\('\.\/mvnw', \['--batch-mode', 'clean', 'package'\]\)/);
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
    .replace('    <!-- renovate: datasource=maven depName=com.seed4j:seed4j -->\n', '')
    .replace('<seed4j.group-id>com.seed4j</seed4j.group-id>', '<seed4j.group-id>io.github.renanfranca</seed4j.group-id>')
    .replace('<seed4j.artifact-id>seed4j</seed4j.artifact-id>', '<seed4j.artifact-id>seed4j-main-snapshot</seed4j.artifact-id>')
    .replace(
      /<seed4j\.version>[^<]+<\/seed4j\.version>/,
      `<seed4j.version>2.2.1-main.20260907.055800.${upstreamSha}-SNAPSHOT</seed4j.version>`,
    )
    .replace('<seed4j.release-channel>stable</seed4j.release-channel>', '<seed4j.release-channel>experimental</seed4j.release-channel>')
    .replace(
      '<seed4j.repository-url>https://repo.maven.apache.org/maven2</seed4j.repository-url>',
      '<seed4j.repository-url>https://central.sonatype.com/repository/maven-snapshots/</seed4j.repository-url>',
    )
    .replace('<seed4j.unavailable-modules />', '<seed4j.unavailable-modules>seed4j-extension</seed4j.unavailable-modules>')
    .replace('<artifactId>seed4j-test</artifactId>', '<artifactId>${seed4j.artifact-id}</artifactId>')
    .replace(
      '      <version>${seed4j.version}</version>\n      <scope>test</scope>',
      '      <version>${seed4j.version}</version>\n      <type>test-jar</type>\n      <classifier>tests</classifier>\n      <scope>test</scope>',
    )
    .replace('  <build>', `${repository}  <build>`);
}

function seed4jDependencies(pom) {
  const directDependencies = pom.slice(pom.indexOf('</dependencyManagement>')).match(/<dependencies>([\s\S]*?)<\/dependencies>/)[1];
  return [...directDependencies.matchAll(/<dependency>[\s\S]*?<\/dependency>/g)]
    .map(match => match[0])
    .filter(dependency => /<artifactId>(?:\$\{seed4j\.artifact-id\}|seed4j-test)<\/artifactId>/.test(dependency));
}

function changeDependency(pom, index, change) {
  const dependency = seed4jDependencies(pom)[index];
  assert.ok(dependency, `fixture must contain dependency ${index}`);
  const changed = change(dependency);
  assert.notEqual(changed, dependency, 'negative fixture must change its target dependency');
  return pom.replace(dependency, changed);
}

function addDependencyDecoy(pom, context, dependency) {
  switch (context) {
    case 'comment':
      return pom.replace(/^  <dependencies>/m, `  <dependencies><!-- ${dependency} -->`);
    case 'dependency management':
      return pom.replace(/<dependencyManagement>[\s\S]*?<\/dependencies>/, section =>
        section.replace('</dependencies>', `${dependency}</dependencies>`),
      );
    case 'plugin':
      return pom.replace(
        /^    <plugins>/m,
        `    <plugins><plugin><artifactId>example</artifactId><dependencies>${dependency}</dependencies></plugin>`,
      );
    case 'profile':
      return pom.replace('<profiles>', `<profiles><profile><id>example</id><dependencies>${dependency}</dependencies></profile>`);
    default:
      throw new Error(`Unknown fixture context: ${context}`);
  }
}

function read(relativePath) {
  return readFileSync(resolve(repositoryRoot, relativePath), 'utf8');
}
