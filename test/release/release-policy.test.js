const assert = require('node:assert/strict');
const test = require('node:test');

const { analyzeCommits } = require('../../scripts/release-analyzer.cjs');

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
