const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');

test('build validates stable and experimental branches while SonarCloud remains stable-only', () => {
  const workflow = read('.github/workflows/github-actions.yml');

  assert.match(workflow, /push:\s+branches:\s+- main\s+- experimental/);
  assert.match(workflow, /pull_request:\s+branches:\s+- main\s+- experimental/);
  assert.match(workflow, /Analysis: SonarCloud[\s\S]*github\.ref == 'refs\/heads\/main'/);
  assert.doesNotMatch(workflow, /Analysis: SonarCloud[\s\S]*refs\/heads\/experimental/);
});

test('PR-controlled builds cannot inherit repository write authority or persisted credentials', () => {
  const build = read('.github/workflows/github-actions.yml');
  const release = read('.github/workflows/release.yml');
  const synchronization = read('.github/workflows/synchronize-experimental.yml');

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
    assert.doesNotMatch(build, new RegExp(`${permission}:\\s*write`));
  }
  assert.match(build, /actions\/checkout@[0-9a-f]{40}[\s\S]*?with:\s+fetch-depth:\s*0\s+persist-credentials:\s*false/);
  assert.doesNotMatch(build, /GITHUB_TOKEN/);
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

test('Renovate isolates official stable updates from current green experimental snapshots', () => {
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
  assert.deepEqual(rules['Track personal Seed4J snapshots on experimental'], {
    automerge: true,
    automergeType: 'pr',
    description: 'Track personal Seed4J snapshots on experimental',
    ignoreUnstable: false,
    matchBaseBranches: ['experimental'],
    matchDatasources: ['maven'],
    matchPackageNames: ['io.github.renanfranca:seed4j-main-snapshot'],
    rebaseWhen: 'behind-base-branch',
    registryUrls: ['https://central.sonatype.com/repository/maven-snapshots/'],
    semanticCommitScope: 'deps',
    semanticCommitType: 'fix',
  });
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
