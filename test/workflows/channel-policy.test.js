const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');

test('build validates stable and experimental branches while SonarCloud remains stable-only', () => {
  const workflow = read('.github/workflows/github-actions.yml');
  const testsJob = workflow.slice(workflow.indexOf('\n  tests:'), workflow.indexOf('\n  request-experimental-release:'));

  assert.match(workflow, /push:\s+branches:\s+- main\s+- experimental/);
  assert.match(workflow, /pull_request:\s+branches:\s+- main\s+- experimental/);
  assert.match(testsJob, /Analysis: SonarCloud[\s\S]*github\.ref == 'refs\/heads\/main'/);
  assert.doesNotMatch(testsJob, /Analysis: SonarCloud[\s\S]*refs\/heads\/experimental/);
});

test('only a trusted completed experimental build may request release with its immutable identity', () => {
  const build = read('.github/workflows/github-actions.yml');
  const release = read('.github/workflows/release.yml');
  const synchronization = read('.github/workflows/synchronize-experimental.yml');
  const testsJob = build.slice(build.indexOf('\n  tests:'), build.indexOf('\n  request-experimental-release:'));
  const releaseRequestJob = build.slice(build.indexOf('\n  request-experimental-release:'));

  assert.match(build, /^permissions:\s+contents:\s*read$/m);
  for (const permission of [
    'contents',
    'actions',
    'issues',
    'checks',
    'pull-requests',
    'deployments',
    'packages',
    'security-events',
    'id-token',
  ]) {
    assert.doesNotMatch(testsJob, new RegExp(`${permission}:\\s*write`));
  }
  assert.match(testsJob, /actions\/checkout@[0-9a-f]{40}[\s\S]*?with:\s+fetch-depth:\s*0\s+persist-credentials:\s*false/);
  assert.doesNotMatch(testsJob, /GITHUB_TOKEN/);
  assert.match(releaseRequestJob, /needs:\s+tests/);
  assert.match(
    releaseRequestJob,
    /github\.repository == 'seed4j\/seed4j-cli'[\s\S]*github\.event_name == 'workflow_dispatch'[\s\S]*github\.ref == 'refs\/heads\/experimental'[\s\S]*github\.actor == 'github-actions\[bot\]'/,
  );
  assert.match(releaseRequestJob, /permissions:\s+actions:\s*write\s+contents:\s*read/);
  assert.doesNotMatch(releaseRequestJob, /contents:\s*write|id-token:\s*write|actions\/checkout@/);
  assert.match(
    releaseRequestJob,
    /gh workflow run release\.yml[\s\S]*--ref main[\s\S]*-f operation=experimental[\s\S]*-f experimental-sha="\$GITHUB_SHA"[\s\S]*-f build-id="\$GITHUB_RUN_ID"/,
  );
  assert.match(release, /operation:[\s\S]*options:[\s\S]*- experimental/);
  assert.match(release, /experimental-sha:[\s\S]*build-id:/);
  assert.match(
    release,
    /inputs\.operation == 'experimental'[\s\S]*github\.actor == 'github-actions\[bot\]'[\s\S]*qualify-experimental-dispatch/,
  );
  assert.match(
    release,
    /workflow_run\.event == 'workflow_dispatch'[\s\S]*workflow_run\.head_branch == 'experimental'[\s\S]*workflow_run\.actor\.login == 'github-actions\[bot\]'/,
  );
  assert.match(
    synchronization,
    /finalize:[\s\S]*workflow_run\.event == 'workflow_dispatch'[\s\S]*workflow_run\.head_branch == 'automation\/sync-main-to-experimental'[\s\S]*workflow_run\.actor\.login == 'github-actions\[bot\]'/,
  );
});

test('workflows pin every third-party action to an immutable commit', () => {
  const workflows = [
    '.github/workflows/github-actions.yml',
    '.github/workflows/release-drafter.yml',
    '.github/workflows/release.yml',
    '.github/workflows/synchronize-experimental.yml',
  ].map(read);
  const uses = workflows.flatMap(workflow => [...workflow.matchAll(/^\s*uses:\s*([^\s#]+)(?:\s+#.*)?$/gm)].map(match => match[1]));

  assert.ok(uses.length > 0);
  assert.deepEqual(
    [...new Set(uses)].sort(),
    [
      'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1',
      'actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1',
      'actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6',
      'actions/setup-node@820762786026740c76f36085b0efc47a31fe5020',
      'release-drafter/release-drafter@34d80673e067bdc0c24568d3af899c216adcfaa9',
    ].sort(),
  );
  assert.ok(uses.every(action => /@[0-9a-f]{40}$/.test(action)));
});

test('experimental releases require their exact green HEAD and cannot mutate stable release surfaces', () => {
  const release = read('.github/workflows/release.yml');
  const releaseDrafter = read('.github/workflows/release-drafter.yml');

  assert.match(release, /workflow_run:[\s\S]*branches:\s+- main\s+- experimental/);
  assert.match(release, /head_branch == 'experimental'/);
  assert.match(release, /node scripts\/release-request\.cjs qualify-workflow-run/);
  assert.match(release, /SEED4J_RELEASE_CHANNEL:.*needs\.qualify\.outputs\.channel/);
  assert.match(release, /id-token:\s*write/);
  assert.match(release, /NPM_CONFIG_PROVENANCE:\s*true/);
  assert.doesNotMatch(release, /NPM_TOKEN|NODE_AUTH_TOKEN/);
  assert.match(release, /Release: publish drafted GitHub release[\s\S]*if:.*released == 'true'.*channel == 'stable'/);
  assert.match(release, /Release: upload CLI JAR[\s\S]*if:.*released == 'true'.*channel == 'stable'/);
  assert.match(release, /recover:[\s\S]*github\.ref == 'refs\/heads\/main'/);
  assert.match(releaseDrafter, /push:\s+branches:\s+- main/);
  assert.doesNotMatch(releaseDrafter, /experimental/);
});

test('Renovate keeps experimental snapshot updates paused during the full-SHA migration', () => {
  const renovate = JSON.parse(read('renovate.json'));
  const rules = Object.fromEntries(renovate.packageRules.map(rule => [rule.description, rule]));

  assert.deepEqual(renovate.baseBranches, ['main', 'experimental']);
  assert.deepEqual(rules['Track official Seed4J releases on main'], {
    description: 'Track official Seed4J releases on main',
    ignoreUnstable: true,
    matchBaseBranches: ['main'],
    matchDatasources: ['maven'],
    matchPackageNames: ['com.seed4j:seed4j'],
    semanticCommitScope: 'deps',
    semanticCommitType: 'fix',
  });
  assert.deepEqual(rules['Ignore personal snapshots on main'], {
    description: 'Ignore personal snapshots on main',
    enabled: false,
    matchBaseBranches: ['main'],
    matchPackageNames: ['io.github.renanfranca:seed4j-main-snapshot'],
  });
  assert.deepEqual(rules['Ignore official Seed4J releases on experimental'], {
    description: 'Ignore official Seed4J releases on experimental',
    enabled: false,
    matchBaseBranches: ['experimental'],
    matchPackageNames: ['com.seed4j:seed4j'],
  });
  assert.deepEqual(rules['Pause personal snapshots on experimental during full-SHA migration'], {
    description: 'Pause personal snapshots on experimental during full-SHA migration',
    enabled: false,
    matchBaseBranches: ['experimental'],
    matchDatasources: ['maven'],
    matchManagers: ['maven'],
    matchPackageNames: ['io.github.renanfranca:seed4j-main-snapshot'],
  });
  assert.equal('customDatasources' in renovate, false);
  assert.equal(
    renovate.customManagers.some(manager => manager.depNameTemplate === 'io.github.renanfranca:seed4j-main-snapshot'),
    false,
  );
  assert.ok(renovate.extends.includes(':automergeRequireAllStatusChecks'));
});

test('the standard build enforces release and workflow policy tests on both channels', () => {
  const packageMetadata = JSON.parse(read('package.json'));
  const build = read('.github/workflows/github-actions.yml');

  assert.equal(packageMetadata.scripts['test:workflows'], 'node --test test/workflows/*.test.js');
  assert.match(build, /npm run test:release\s+npm run test:workflows\s+npm run test:npm-package/);
});

function read(relativePath) {
  return readFileSync(resolve(repositoryRoot, relativePath), 'utf8');
}
