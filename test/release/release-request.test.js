const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdtempSync, readFileSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const {
  validateDispatchRequest,
  validateExperimentalRelease,
  validateManualRelease,
  validateRecoveryVersion,
} = require('../../scripts/release-request.cjs');

const repositoryRoot = resolve(__dirname, '../..');

test('accepts release dispatch without a manually selected version', () => {
  const request = validateDispatchRequest({ operation: 'release', version: '' });

  assert.deepEqual(request, { operation: 'release' });
  assert.throws(() => validateDispatchRequest({ operation: 'release', version: '9.9.9' }), /calculates its version automatically/);
});

test('requires an exact stable version for recovery dispatch', () => {
  const request = validateDispatchRequest({ operation: 'recover', version: '0.1.0' });

  assert.deepEqual(request, { operation: 'recover', version: '0.1.0' });
  assert.throws(() => validateDispatchRequest({ operation: 'recover', version: '' }), /stable semantic version/);
  assert.throws(() => validateRecoveryVersion('v0.1.0'), /stable semantic version/);
  assert.throws(() => validateRecoveryVersion('0.1.0-rc.1'), /stable semantic version/);
  assert.throws(() => validateDispatchRequest({ operation: 'publish', version: '' }), /Unsupported release operation/);
});

test('accepts a manual release only for untagged current main with a green push build', () => {
  assert.doesNotThrow(() =>
    validateManualRelease({
      checkedOutSha: 'current-main',
      currentMainSha: 'current-main',
      releaseTags: [],
      successfulBuildCount: 1,
    }),
  );
});

test('rejects stale, unverified, or already released manual revisions', () => {
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'older-main',
        currentMainSha: 'current-main',
        releaseTags: [],
        successfulBuildCount: 1,
      }),
    /current main/,
  );
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'current-main',
        currentMainSha: 'current-main',
        releaseTags: [],
        successfulBuildCount: 0,
      }),
    /successful push build/,
  );
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'current-main',
        currentMainSha: 'current-main',
        releaseTags: ['preview', 'v0.1.0'],
        successfulBuildCount: 1,
      }),
    /operation=recover.*0\.1\.0/,
  );
});

test('accepts only an unreleased successful push build of current experimental HEAD', () => {
  const eligible = {
    buildConclusion: 'success',
    buildEvent: 'push',
    buildHeadBranch: 'experimental',
    builtSha: 'current-experimental',
    checkedOutSha: 'current-experimental',
    currentExperimentalSha: 'current-experimental',
    releaseTags: [],
  };

  assert.doesNotThrow(() => validateExperimentalRelease(eligible));
  assert.throws(
    () => validateExperimentalRelease({ ...eligible, currentExperimentalSha: 'newer-experimental' }),
    /current experimental HEAD/,
  );
  assert.throws(() => validateExperimentalRelease({ ...eligible, buildHeadBranch: 'main' }), /experimental push build/);
  assert.throws(() => validateExperimentalRelease({ ...eligible, buildEvent: 'pull_request' }), /experimental push build/);
  assert.throws(() => validateExperimentalRelease({ ...eligible, buildConclusion: 'failure' }), /successful/);
  assert.throws(
    () => validateExperimentalRelease({ ...eligible, releaseTags: ['v1.2.3-experimental.4'] }),
    /already has v1\.2\.3-experimental\.4/,
  );
});

test('release workflow admits only trusted exact-head build provenance for each channel', () => {
  const currentSha = '1111111111111111111111111111111111111111';
  const common = {
    BUILD_ACTOR: 'github-actions[bot]',
    BUILD_CONCLUSION: 'success',
    BUILT_SHA: currentSha,
    CHECKED_OUT_SHA: currentSha,
    CURRENT_EXPERIMENTAL_SHA: currentSha,
    CURRENT_MAIN_SHA: currentSha,
    RELEASE_TAGS: '',
  };
  const synchronizedExperimental = runReleaseAdapter('workflow-run', {
    ...common,
    BUILD_EVENT: 'workflow_dispatch',
    BUILD_HEAD_BRANCH: 'experimental',
  });
  const pushedExperimental = runReleaseAdapter('workflow-run', {
    ...common,
    BUILD_ACTOR: 'octocat',
    BUILD_EVENT: 'push',
    BUILD_HEAD_BRANCH: 'experimental',
  });
  const pushedMain = runReleaseAdapter('workflow-run', {
    ...common,
    BUILD_ACTOR: 'octocat',
    BUILD_EVENT: 'push',
    BUILD_HEAD_BRANCH: 'main',
  });

  assert.equal(synchronizedExperimental.status, 0, synchronizedExperimental.stderr);
  assert.deepEqual(synchronizedExperimental.outputs, { channel: 'experimental', reason: '', release: 'true' });
  assert.equal(pushedExperimental.status, 0, pushedExperimental.stderr);
  assert.equal(pushedExperimental.outputs.release, 'true');
  assert.equal(pushedMain.status, 0, pushedMain.stderr);
  assert.deepEqual(pushedMain.outputs, { channel: 'stable', reason: '', release: 'true' });

  for (const [name, environment] of [
    ['stable dispatch', { ...common, BUILD_EVENT: 'workflow_dispatch', BUILD_HEAD_BRANCH: 'main' }],
    [
      'untrusted experimental dispatch',
      { ...common, BUILD_ACTOR: 'octocat', BUILD_EVENT: 'workflow_dispatch', BUILD_HEAD_BRANCH: 'experimental' },
    ],
    ['pull request revision', { ...common, BUILD_EVENT: 'pull_request', BUILD_HEAD_BRANCH: 'experimental' }],
    ['red build', { ...common, BUILD_CONCLUSION: 'failure', BUILD_EVENT: 'push', BUILD_HEAD_BRANCH: 'experimental' }],
  ]) {
    const rejected = runReleaseAdapter('workflow-run', environment);

    assert.equal(rejected.status, 0, `${name}: ${rejected.stderr}`);
    assert.equal(rejected.outputs.release, 'false', name);
  }

  for (const [name, environment] of [
    [
      'stale synchronized experimental',
      {
        ...common,
        BUILD_EVENT: 'workflow_dispatch',
        BUILD_HEAD_BRANCH: 'experimental',
        CURRENT_EXPERIMENTAL_SHA: '2222222222222222222222222222222222222222',
      },
    ],
    [
      'already tagged experimental',
      {
        ...common,
        BUILD_EVENT: 'workflow_dispatch',
        BUILD_HEAD_BRANCH: 'experimental',
        RELEASE_TAGS: 'v1.2.3-experimental.4',
      },
    ],
  ]) {
    const rejected = runReleaseAdapter('workflow-run', environment);

    assert.equal(rejected.status, 1, name);
    assert.equal(rejected.outputs.release, undefined, name);
  }
});

function runReleaseAdapter(command, environment) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-release-adapter-'));
  const output = join(directory, 'github-output');
  try {
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/release-request.cjs'), command], {
      encoding: 'utf8',
      env: { ...process.env, ...environment, GITHUB_OUTPUT: output },
    });
    return {
      ...result,
      outputs: result.status === 0 ? workflowOutputs(readFileSync(output, 'utf8')) : {},
    };
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

function workflowOutputs(output) {
  return Object.fromEntries(
    output
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
}
