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

test('publishes experimental release corrections only on the experimental channel', async () => {
  const message = 'ci(experimental-release): isolate npm provenance identity';

  const stableRelease = await analyzeCommits({}, contextWithEnvironment({ SEED4J_RELEASE_CHANNEL: 'stable' }, message));
  const experimentalRelease = await analyzeCommits({}, contextWithEnvironment({ SEED4J_RELEASE_CHANNEL: 'experimental' }, message));

  assert.equal(stableRelease, null);
  assert.equal(experimentalRelease, 'patch');
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
  assert.ok(releaseConfiguration.plugins.includes('./scripts/release-npm.cjs'));
  assert.equal(releaseConfiguration.plugins.includes('@semantic-release/npm'), false);
  assert.equal(releaseConfiguration.plugins.includes('./scripts/release-provenance-context.cjs'), false);
  assert.equal(releaseConfiguration.plugins.includes('@semantic-release/github'), false);
});

test('gives the delegated npm plugin the real workflow identity without changing semantic-release context', () => {
  const releaseContext = loadReleaseContext();
  const context = {
    env: {
      GITHUB_REF: 'refs/heads/experimental',
      GITHUB_SHA: '1111111111111111111111111111111111111111',
      PROVENANCE_GITHUB_REF: 'refs/heads/main',
      PROVENANCE_GITHUB_SHA: '2222222222222222222222222222222222222222',
      QUALIFIED_SHA: '1111111111111111111111111111111111111111',
      RELEASE_BRANCH: 'experimental',
    },
  };

  const npmContext = releaseContext.forNpm(context);

  assert.notEqual(npmContext, context);
  assert.notEqual(npmContext.env, context.env);
  assert.equal(npmContext.env.GITHUB_REF, 'refs/heads/main');
  assert.equal(npmContext.env.GITHUB_SHA, '2222222222222222222222222222222222222222');
  assert.equal(context.env.GITHUB_REF, 'refs/heads/experimental');
  assert.equal(context.env.GITHUB_SHA, '1111111111111111111111111111111111111111');
});

test('rejects a release context that was not bound to the qualified target', () => {
  const releaseContext = loadReleaseContext();

  assert.throws(
    () =>
      releaseContext.forNpm({
        env: {
          GITHUB_REF: 'refs/heads/main',
          GITHUB_SHA: '2222222222222222222222222222222222222222',
          PROVENANCE_GITHUB_REF: 'refs/heads/main',
          PROVENANCE_GITHUB_SHA: '2222222222222222222222222222222222222222',
          QUALIFIED_SHA: '1111111111111111111111111111111111111111',
          RELEASE_BRANCH: 'experimental',
        },
      }),
    /qualified release target/,
  );
});

test('delegates every npm semantic-release lifecycle through the provenance-aware wrapper', () => {
  const npmRelease = loadNpmRelease();

  assert.equal(typeof npmRelease.verifyConditions, 'function');
  assert.equal(typeof npmRelease.prepare, 'function');
  assert.equal(typeof npmRelease.publish, 'function');
  assert.equal(typeof npmRelease.addChannel, 'function');
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

function loadReleaseContext() {
  try {
    return require('../../scripts/release-provenance-context.cjs');
  } catch (error) {
    assert.fail(`Release provenance context is unavailable: ${error.message}`);
  }
}

function loadNpmRelease() {
  try {
    return require('../../scripts/release-npm.cjs');
  } catch (error) {
    assert.fail(`npm release wrapper is unavailable: ${error.message}`);
  }
}

function contextWithEnvironment(environment, ...messages) {
  return {
    commits: messages.map((message, index) => ({ hash: `commit-${index}`, message })),
    cwd: process.cwd(),
    env: environment,
    logger,
  };
}
