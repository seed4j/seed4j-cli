const assert = require('node:assert/strict');
const test = require('node:test');

const { analyzeCommits } = require('../../scripts/release-analyzer.cjs');
const { validateReleaseVersion } = require('../../scripts/release-version.cjs');
const releaseConfiguration = require('../../release.config.cjs');

const logger = { log() {} };

test('classifies consumer-visible commits with ordinary SemVer', async () => {
  const cases = [
    ['fix(command): reject an invalid project', 'patch'],
    ['perf(bootstrap): reduce startup work', 'patch'],
    ['revert: feat(command): add unsafe mode\n\nThis reverts commit abcdef1.', 'patch'],
    ['feat(command): add batch application', 'minor'],
    ['feat(command)!: replace the apply contract', 'major'],
    ['fix(command): change output\n\nBREAKING CHANGE: the previous output is no longer supported', 'major'],
  ];

  for (const [message, expectedRelease] of cases) {
    const release = await analyzeCommits({}, context(message));

    assert.equal(release, expectedRelease, message);
  }
});

test('publishes runtime dependency updates as patches', async () => {
  const release = await analyzeCommits({}, context('fix(deps): update dependency com.seed4j:seed4j to v2.3.0'));

  assert.equal(release, 'patch');
});

test('does not release neutral changes or build-only dependency updates', async () => {
  const messages = [
    'build: tune packaging',
    'chore(deps): update dependency prettier to v4',
    'ci: simplify build',
    'docs: clarify installation',
    'refactor(command): extract renderer',
    'style: format sources',
    'test(command): cover invalid input',
  ];

  const release = await analyzeCommits({}, context(...messages));

  assert.equal(release, null);
});

test('selects the highest release required by accumulated commits', async () => {
  const release = await analyzeCommits(
    {},
    context('fix(command): correct output', 'feat(command): add batch application', 'docs: explain batch application'),
  );

  assert.equal(release, 'minor');
});

test('manual publication changes only no-release analysis to patch', async () => {
  const neutralRelease = await analyzeCommits({}, contextWithEnvironment({ SEED4J_MANUAL_RELEASE: 'true' }, 'docs: clarify installation'));
  const fixRelease = await analyzeCommits({}, contextWithEnvironment({ SEED4J_MANUAL_RELEASE: 'true' }, 'fix(command): correct output'));
  const featureRelease = await analyzeCommits(
    {},
    contextWithEnvironment({ SEED4J_MANUAL_RELEASE: 'true' }, 'feat(command): add batch application'),
  );
  const breakingRelease = await analyzeCommits(
    {},
    contextWithEnvironment({ SEED4J_MANUAL_RELEASE: 'true' }, 'feat(command)!: replace the apply contract'),
  );

  assert.equal(neutralRelease, 'patch');
  assert.equal(fixRelease, 'patch');
  assert.equal(featureRelease, 'minor');
  assert.equal(breakingRelease, 'major');
});

test('publishes main as stable and experimental as an experimental prerelease', () => {
  assert.deepEqual(releaseConfiguration.branches, [
    'main',
    {
      channel: 'experimental',
      name: 'experimental',
      prerelease: 'experimental',
    },
  ]);
  assert.equal(releaseConfiguration.tagFormat, 'v${version}');
  assert.ok(releaseConfiguration.plugins.includes('@semantic-release/npm'));
  assert.equal(releaseConfiguration.plugins.includes('@semantic-release/github'), false);
});

test('prepares only stable versions for stable releases and experimental versions for experimental releases', () => {
  assert.equal(validateReleaseVersion('1.2.3', 'stable'), '1.2.3');
  assert.equal(validateReleaseVersion('1.3.0-experimental.4', 'experimental'), '1.3.0-experimental.4');
  assert.throws(() => validateReleaseVersion('1.2.3-experimental.1', 'stable'), /stable semantic version/);
  assert.throws(() => validateReleaseVersion('1.2.3', 'experimental'), /experimental semantic version/);
  assert.throws(() => validateReleaseVersion('1.2.3-next.1', 'experimental'), /experimental semantic version/);
  assert.throws(() => validateReleaseVersion('1.2.3-experimental.1', 'preview'), /Unknown release channel/);
});

function context(...messages) {
  return contextWithEnvironment({}, ...messages);
}

function contextWithEnvironment(environment, ...messages) {
  return {
    commits: messages.map((message, index) => ({ hash: `commit-${index}`, message })),
    cwd: process.cwd(),
    env: environment,
    logger,
  };
}
