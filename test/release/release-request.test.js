const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdtempSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
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

test('release workflow rejects deceptive source branches before privileged target checkout', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/release.yml'), 'utf8');
  const qualification = workflow.slice(workflow.indexOf('  qualify:'), workflow.indexOf('  publish:'));
  const publish = workflow.slice(workflow.indexOf('  publish:'), workflow.indexOf('  recover:'));

  assert.match(qualification, /permissions:\n      actions: read\n      contents: read/);
  assert.doesNotMatch(qualification, /contents: write|id-token: write/);
  assert.match(qualification, /head_repository\.full_name == github\.repository/);
  assert.match(qualification, /workflow_run\.event == 'push'/);
  assert.match(qualification, /workflow_run\.event == 'workflow_dispatch'/);
  assert.match(qualification, /workflow_run\.actor\.login == 'github-actions\[bot\]'/);
  assert.match(qualification, /ref: main/);
  assert.match(qualification, /persist-credentials: false/);
  assert.match(publish, /needs: qualify/);
  assert.match(publish, /needs\.qualify\.outputs\.eligible == 'true'/);
  assert.match(publish, /contents: write/);
  assert.match(publish, /id-token: write/);
  assert.match(publish, /ref: \$\{\{ needs\.qualify\.outputs\.sha \}\}/);
  assert.ok(publish.indexOf("name: 'Release: verify selected protected head'") < publish.indexOf("name: 'Setup: Node.js'"));

  for (const branch of ['main', 'experimental']) {
    const rejected = runReleaseAdapter('qualify-workflow-run', {
      BUILD_ACTOR: 'octocat',
      BUILD_CONCLUSION: 'success',
      BUILD_EVENT: 'pull_request',
      BUILD_HEAD_BRANCH: branch,
      BUILD_SOURCE_REPOSITORY: 'attacker/seed4j-cli',
      BUILT_SHA: '1111111111111111111111111111111111111111',
      GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
    });

    assert.equal(rejected.status, 1, branch);
    assert.match(rejected.stderr, /trusted release workflow/, branch);
    assert.deepEqual(rejected.outputs, {}, branch);
  }
});

test('stable qualification and recovery work without an experimental remote branch', () => {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-stable-release-'));
  const remote = join(directory, 'remote.git');
  const source = join(directory, 'source');
  const checkout = join(directory, 'checkout');
  try {
    runGit(directory, ['init', '--bare', remote]);
    runGit(directory, ['init', source]);
    runGit(source, ['config', 'user.email', 'seed4j@example.com']);
    runGit(source, ['config', 'user.name', 'Seed4J']);
    writeFileSync(join(source, 'release.txt'), 'released\n');
    runGit(source, ['add', 'release.txt']);
    runGit(source, ['commit', '-m', 'feat: released revision']);
    runGit(source, ['tag', 'v0.1.0']);
    writeFileSync(join(source, 'release.txt'), 'current main\n');
    runGit(source, ['commit', '-am', 'feat: current main']);
    runGit(source, ['branch', '-M', 'main']);
    runGit(source, ['remote', 'add', 'origin', remote]);
    runGit(source, ['push', 'origin', 'main', '--tags']);
    runGit(directory, ['clone', '--branch', 'main', remote, checkout]);
    const currentMainSha = runGit(checkout, ['rev-parse', 'HEAD']);
    const releasedSha = runGit(checkout, ['rev-list', '-n', '1', 'v0.1.0']);

    const workflowRun = runReleaseAdapter(
      'qualify-workflow-run',
      {
        BUILD_ACTOR: 'octocat',
        BUILD_CONCLUSION: 'success',
        BUILD_EVENT: 'push',
        BUILD_HEAD_BRANCH: 'main',
        BUILD_SOURCE_REPOSITORY: 'seed4j/seed4j-cli',
        BUILT_SHA: currentMainSha,
        GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
      },
      checkout,
    );
    const manual = runReleaseAdapter(
      'qualify-manual-release',
      {
        RELEASE_OPERATION: 'release',
        RELEASE_VERSION: '',
        SUCCESSFUL_BUILD_COUNT: '1',
      },
      checkout,
    );
    const recovery = runReleaseAdapter(
      'qualify-recovery',
      {
        RELEASE_OPERATION: 'recover',
        RELEASE_VERSION: '0.1.0',
      },
      checkout,
    );

    assert.equal(workflowRun.status, 0, workflowRun.stderr);
    assert.deepEqual(workflowRun.outputs, { channel: 'stable', eligible: 'true', sha: currentMainSha });
    assert.equal(manual.status, 0, manual.stderr);
    assert.deepEqual(manual.outputs, { channel: 'stable', eligible: 'true', sha: currentMainSha });
    assert.equal(recovery.status, 0, recovery.stderr);
    assert.deepEqual(recovery.outputs, { sha: releasedSha });
    assert.throws(() => runGit(checkout, ['rev-parse', '--verify', 'origin/experimental']), /git rev-parse/);
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

function runReleaseAdapter(command, environment, cwd = repositoryRoot) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-release-adapter-'));
  const output = join(directory, 'github-output');
  try {
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/release-request.cjs'), command], {
      encoding: 'utf8',
      cwd,
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

function runGit(cwd, arguments_) {
  const result = spawnSync('git', arguments_, { cwd, encoding: 'utf8' });
  assert.equal(result.status, 0, `git ${arguments_.join(' ')}: ${result.stderr}`);
  return result.stdout.trim();
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
