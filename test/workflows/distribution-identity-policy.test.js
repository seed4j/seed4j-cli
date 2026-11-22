const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const test = require('node:test');

const { distributionIdentity, validateDistributionBuild } = require('../../scripts/distribution-identity-policy.cjs');

const repositoryRoot = resolve(__dirname, '../..');

test('the checked-out POM owns a complete distribution identity for its channel', () => {
  const pom = read('pom.xml');
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const identity = validateDistributionBuild({ metadata, pom });

  assert.equal(identity.artifactId, 'seed4j-engine');
  assert.equal(seed4jDependencies(pom).length, 3);
});

test('the stable POM has one official Maven authority and no personal snapshot declaration', () => {
  const pom = stableBranchPom(read('pom.xml'));
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const expectedVersion = /<seed4j\.version>([^<]+)<\/seed4j\.version>/.exec(pom)[1].trim();

  const identity = validateDistributionBuild({ metadata, pom });

  assert.deepEqual(
    { ...identity, version: '<authoritative-version>' },
    {
      artifactId: 'seed4j-engine',
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
  assert.equal(dependencies.length, 3);
  assert.match(dependencies[0], /<groupId>\$\{seed4j\.group-id\}<\/groupId>/);
  assert.match(dependencies[0], /<artifactId>\$\{seed4j\.artifact-id\}<\/artifactId>/);
  assert.match(dependencies[0], /<version>\$\{seed4j\.version\}<\/version>/);
  assert.doesNotMatch(dependencies[0], /<scope>|<type>|<classifier>/);
  assert.match(dependencies[1], /<artifactId>seed4j-core-modules<\/artifactId>/);
  assert.match(dependencies[1], /<version>\$\{seed4j\.version\}<\/version>/);
  assert.doesNotMatch(dependencies[1], /<scope>|<type>|<classifier>/);
  assert.match(dependencies[2], /<artifactId>seed4j-test<\/artifactId>/);
  assert.match(dependencies[2], /<version>\$\{seed4j\.version\}<\/version>/);
  assert.match(dependencies[2], /<scope>test<\/scope>/);
  assert.doesNotMatch(dependencies[2], /<type>|<classifier>/);
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
      artifactId: 'seed4j-engine',
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
  assert.equal(dependencies.length, 3);
  assert.match(dependencies[1], /<artifactId>seed4j-core-modules<\/artifactId>/);
  assert.match(dependencies[2], /<artifactId>seed4j-test<\/artifactId>/);
  assert.match(dependencies[2], /<scope>test<\/scope>/);
  assert.doesNotMatch(dependencies[2], /<type>|<classifier>/);
  assert.match(pom, /<releases>[\s\S]*?<enabled>false<\/enabled>/);
  assert.match(pom, /<snapshots>[\s\S]*?<enabled>true<\/enabled>/);
});

test('the policy derives full provenance from a full-SHA snapshot version without legacy metadata', () => {
  const upstreamSha = '4eebd07bce14c9a6ac70bace157fcc616133e950';
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  const pom = experimentalBranchPom(read('pom.xml'));

  assert.deepEqual(validateDistributionBuild({ metadata, pom }), {
    artifactId: 'seed4j-engine',
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
  const pom = channel === 'stable' ? stableBranchPom(read('pom.xml')) : experimentalBranchPom(read('pom.xml'));
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');

  for (const [index, role] of ['engine', 'core modules', 'test support'].entries()) {
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
        const dependency = seed4jDependencies(pom)[index];
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
      const invalidPom = changeDependency(pom, 2, dependency =>
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
        .replace('<artifactId>seed4j-test</artifactId>', '<artifactId>seed4j</artifactId>')
        .replace('<scope>test</scope>', '<type>test-jar</type><classifier>tests</classifier><scope>test</scope>'),
  ],
  ['test-jar type', dependency => dependency.replace('<scope>', '<type>test-jar</type><scope>')],
  ['tests classifier', dependency => dependency.replace('<scope>', '<classifier>tests</classifier><scope>')],
]) {
  test(`stable test support rejects ${label}`, () => {
    const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
    const pom = stableBranchPom(read('pom.xml'));
    assert.equal(validateDistributionBuild({ metadata, pom }).channel, 'stable');
    const invalidPom = changeDependency(pom, 2, change);

    assert.throws(
      () => validateDistributionBuild({ metadata, pom: invalidPom }),
      /test support dependency.*(?:artifactId|type|classifier)/i,
    );
  });
}

test('test support accepts explicit jar type in both channels', () => {
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  for (const pom of [stableBranchPom(read('pom.xml')), experimentalBranchPom(read('pom.xml'))]) {
    const explicitJar = changeDependency(pom, 2, dependency => dependency.replace('<scope>', '<type>jar</type><scope>'));

    assert.deepEqual(validateDistributionBuild({ metadata, pom: explicitJar }), distributionIdentity(pom));
  }
});

test('each version update changes one Maven property shared by all three modular dependencies', () => {
  const metadata = read('src/main/resources/META-INF/seed4j-cli-distribution.properties');
  for (const [pom, nextVersion] of [
    [stableBranchPom(read('pom.xml')), '99.0.0'],
    [experimentalBranchPom(read('pom.xml')), '99.0.0-main.20261122.130000.9876543210987654321098765432109876543210-SNAPSHOT'],
  ]) {
    const before = validateDistributionBuild({ metadata, pom });
    const updatedPom = pom.replace(`<seed4j.version>${before.version}</seed4j.version>`, `<seed4j.version>${nextVersion}</seed4j.version>`);
    const updated = validateDistributionBuild({ metadata, pom: updatedPom });

    assert.equal(updated.version, nextVersion);
    assert.deepEqual(seed4jDependencies(updatedPom), seed4jDependencies(pom));
    for (const dependency of seed4jDependencies(updatedPom)) {
      assert.match(dependency, /<version>\$\{seed4j\.version\}<\/version>/);
    }
  }
});

test('Renovate tracks the experimental snapshot with one active native Maven rule', () => {
  const stablePom = stableBranchPom(read('pom.xml'));
  const experimentalPom = experimentalBranchPom(stablePom);
  const renovate = JSON.parse(read('renovate.json'));
  const managers = Object.fromEntries(renovate.customManagers.map(manager => [manager.depNameTemplate, manager]));
  const rule = renovate.packageRules.find(rule => rule.description === 'Track personal Seed4J snapshots on experimental');

  assert.match(stablePom, new RegExp(managers['com.seed4j:seed4j-engine'].matchStrings[0]));
  assert.doesNotMatch(experimentalPom, new RegExp(managers['com.seed4j:seed4j-engine'].matchStrings[0]));
  assert.equal(managers['io.github.renanfranca:seed4j-core-modules'], undefined);
  assert.equal('customDatasources' in renovate, false);
  assert.deepEqual(rule, {
    description: 'Track personal Seed4J snapshots on experimental',
    enabled: true,
    matchBaseBranches: ['experimental'],
    matchDatasources: ['maven'],
    matchManagers: ['maven'],
    matchPackageNames: ['io.github.renanfranca:seed4j-core-modules'],
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
        candidate.matchPackageNames?.includes('io.github.renanfranca:seed4j-core-modules')
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

function stableBranchPom(pom) {
  if (distributionIdentity(pom).channel === 'stable') {
    return pom;
  }
  return pom
    .replace('    <seed4j.group-id>', '    <!-- renovate: datasource=maven depName=com.seed4j:seed4j-engine -->\n    <seed4j.group-id>')
    .replace('<seed4j.group-id>io.github.renanfranca</seed4j.group-id>', '<seed4j.group-id>com.seed4j</seed4j.group-id>')
    .replace(/<seed4j\.version>[^<]+<\/seed4j\.version>/, '<seed4j.version>2.4.0</seed4j.version>')
    .replace('<seed4j.release-channel>experimental</seed4j.release-channel>', '<seed4j.release-channel>stable</seed4j.release-channel>')
    .replace(
      '<seed4j.repository-url>https://central.sonatype.com/repository/maven-snapshots/</seed4j.repository-url>',
      '<seed4j.repository-url>https://repo.maven.apache.org/maven2</seed4j.repository-url>',
    )
    .replace('<seed4j.unavailable-modules>seed4j-extension</seed4j.unavailable-modules>', '<seed4j.unavailable-modules />')
    .replace(/  <repositories>[\s\S]*?<\/repositories>\n\n/, '');
}

function experimentalBranchPom(pom) {
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
  return stableBranchPom(pom)
    .replace('    <!-- renovate: datasource=maven depName=com.seed4j:seed4j-engine -->\n', '')
    .replace('<seed4j.group-id>com.seed4j</seed4j.group-id>', '<seed4j.group-id>io.github.renanfranca</seed4j.group-id>')
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
    .replace('  <build>', `${repository}  <build>`);
}

function seed4jDependencies(pom) {
  const directDependencies = pom.slice(pom.indexOf('</dependencyManagement>')).match(/<dependencies>([\s\S]*?)<\/dependencies>/)[1];
  return [...directDependencies.matchAll(/<dependency>[\s\S]*?<\/dependency>/g)]
    .map(match => match[0])
    .filter(dependency => /<artifactId>(?:\$\{seed4j\.artifact-id\}|seed4j-core-modules|seed4j-test)<\/artifactId>/.test(dependency));
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
