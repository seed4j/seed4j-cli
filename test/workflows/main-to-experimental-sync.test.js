const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const {
  conflictIssueAction,
  pullRequestBody,
  postMergeSynchronization,
  prepareSynchronization,
  reviewSynchronization,
  synchronizationStateFromPullRequest,
} = require('../../scripts/main-to-experimental-sync.cjs');

const repositoryRoot = resolve(__dirname, '../..');
const sourceSha = '1111111111111111111111111111111111111111';
const targetSha = '2222222222222222222222222222222222222222';
const headSha = '3333333333333333333333333333333333333333';
const mergeSha = '4444444444444444444444444444444444444444';
const currentExperimentalSha = '5555555555555555555555555555555555555555';

test('a successful exact current main build proposes one-way synchronization from current experimental and dispatches tests for the exact PR head', () => {
  assert.deepEqual(
    prepareSynchronization({
      buildConclusion: 'success',
      buildEvent: 'push',
      buildHeadBranch: 'main',
      builtSha: sourceSha,
      currentExperimentalSha: targetSha,
      currentMainSha: sourceSha,
      mergeResult: 'clean',
      proposalHeadSha: headSha,
      proposalSourceSha: sourceSha,
      proposalTargetSha: targetSha,
    }),
    {
      action: 'propose',
      branch: 'automation/sync-main-to-experimental',
      headSha,
      sourceBranch: 'main',
      sourceSha,
      steps: ['push-proposal', 'open-or-refresh-pr', 'close-conflict-issue', 'dispatch-tests'],
      targetBranch: 'experimental',
      targetSha,
    },
  );
});

test('the executable workflow preparation adapter owns proposal and already-contained decisions', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');
  const commonEnvironment = {
    BUILD_CONCLUSION: 'success',
    BUILD_EVENT: 'push',
    BUILD_HEAD_BRANCH: 'main',
    BUILT_SHA: sourceSha,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    CURRENT_MAIN_SHA: sourceSha,
    PROPOSAL_SOURCE_SHA: sourceSha,
    PROPOSAL_TARGET_SHA: targetSha,
  };
  const proposal = runWorkflowAdapter('prepare-workflow', {
    ...commonEnvironment,
    MERGE_RESULT: 'clean',
    PROPOSAL_HEAD_SHA: headSha,
  });

  assert.equal(proposal.status, 0, proposal.stderr);
  assert.deepEqual(proposal.outputs, {
    action: 'propose',
    branch: 'automation/sync-main-to-experimental',
    head: headSha,
    reason: '',
    source: sourceSha,
    target: targetSha,
  });

  const alreadyContained = runWorkflowAdapter('prepare-workflow', {
    ...commonEnvironment,
    MERGE_RESULT: 'already-contained',
  });

  assert.equal(alreadyContained.status, 0, alreadyContained.stderr);
  assert.equal(alreadyContained.outputs.action, 'already-contained');
  assert.equal(alreadyContained.outputs.target, targetSha);
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs prepare-workflow/);
  assert.match(workflow, /steps\.proposal\.outputs\.action == 'propose'/);
});

test('stale source or target evidence refreshes instead of pushing or merging', () => {
  const candidate = {
    buildConclusion: 'success',
    buildEvent: 'push',
    buildHeadBranch: 'main',
    builtSha: sourceSha,
    currentExperimentalSha: targetSha,
    currentMainSha: sourceSha,
    mergeResult: 'clean',
    proposalHeadSha: headSha,
    proposalSourceSha: sourceSha,
    proposalTargetSha: targetSha,
  };

  assert.deepEqual(prepareSynchronization({ ...candidate, currentMainSha: mergeSha }), {
    action: 'wait',
    reason: 'successful-main-build-is-stale',
  });
  assert.deepEqual(prepareSynchronization({ ...candidate, currentExperimentalSha: mergeSha }), {
    action: 'refresh',
    reason: 'experimental-moved-during-preparation',
  });
  assert.deepEqual(
    reviewSynchronization({
      currentExperimentalSha: mergeSha,
      currentMainSha: sourceSha,
      expectedSourceSha: sourceSha,
      expectedTargetSha: targetSha,
      mergeable: 'MERGEABLE',
      observedProposalHeadSha: headSha,
      proposalHeadSha: headSha,
      proposalParentShas: [targetSha, sourceSha],
      proposalState: 'OPEN',
      recordedHeadSha: headSha,
      testedHeadSha: headSha,
      testsConclusion: 'success',
      testsStatus: 'completed',
    }),
    { action: 'refresh', reason: 'target-branch-moved' },
  );
});

test('a merge conflict reports one persistent issue and leaves protected branches unchanged', () => {
  assert.deepEqual(
    prepareSynchronization({
      buildConclusion: 'success',
      buildEvent: 'push',
      buildHeadBranch: 'main',
      builtSha: sourceSha,
      currentExperimentalSha: targetSha,
      currentMainSha: sourceSha,
      mergeResult: 'conflict',
      proposalSourceSha: sourceSha,
      proposalTargetSha: targetSha,
    }),
    {
      action: 'report-conflict',
      sourceSha,
      targetSha,
    },
  );
  assert.deepEqual(conflictIssueAction({ conflict: true }), {
    action: 'create',
    assignees: ['renanfranca'],
    labels: ['synchronization-failure'],
    title: '[synchronization] main to experimental conflict',
  });
  assert.deepEqual(conflictIssueAction({ conflict: true, openIssueNumber: 42 }), {
    action: 'update',
    assignees: ['renanfranca'],
    issueNumber: 42,
    labels: ['synchronization-failure'],
    title: '[synchronization] main to experimental conflict',
  });
  assert.deepEqual(conflictIssueAction({ conflict: false, openIssueNumber: 42 }), { action: 'close', issueNumber: 42 });
});

test('the executable current-state issue policy fails closed on duplicate open issues', () => {
  const duplicates = runIssueWorkflowAdapter(false, [
    { number: 41, title: '[synchronization] main to experimental conflict' },
    { number: 42, title: '[synchronization] main to experimental conflict' },
  ]);
  const resolved = runIssueWorkflowAdapter(false, [{ number: 42, title: '[synchronization] main to experimental conflict' }]);
  const conflicting = runIssueWorkflowAdapter(true, [{ number: 42, title: '[synchronization] main to experimental conflict' }]);

  assert.equal(duplicates.status, 1);
  assert.match(duplicates.stderr, /at most one open synchronization conflict issue; found 2/);
  assert.equal(resolved.status, 0, resolved.stderr);
  assert.deepEqual(resolved.outputs, { action: 'close', issue: '42' });
  assert.equal(conflicting.status, 0, conflicting.stderr);
  assert.deepEqual(conflicting.outputs, { action: 'update', issue: '42' });
});

test('the pull request carries one strict machine-readable source, target, and head binding', () => {
  const body = pullRequestBody({ headSha, sourceSha, targetSha });

  assert.deepEqual(synchronizationStateFromPullRequest(body), {
    headSha,
    sourceSha,
    targetSha,
  });
  assert.throws(() => synchronizationStateFromPullRequest(`${body}\n${body}`), /exactly one synchronization state block/);
  assert.throws(() => synchronizationStateFromPullRequest(body.replace(sourceSha, 'main')), /source SHA/);
});

test('only current conflict-free PR evidence with the exact completed green tests check may enable auto-merge', () => {
  const current = {
    currentExperimentalSha: targetSha,
    currentMainSha: sourceSha,
    expectedSourceSha: sourceSha,
    expectedTargetSha: targetSha,
    mergeable: 'MERGEABLE',
    observedProposalHeadSha: headSha,
    proposalHeadSha: headSha,
    proposalParentShas: [targetSha, sourceSha],
    proposalState: 'OPEN',
    recordedHeadSha: headSha,
    testedHeadSha: headSha,
    testsConclusion: 'success',
    testsStatus: 'completed',
  };

  assert.deepEqual(reviewSynchronization(current), {
    action: 'enable-auto-merge',
    headSha,
  });
  assert.deepEqual(reviewSynchronization({ ...current, testedHeadSha: mergeSha }), {
    action: 'refresh',
    reason: 'tests-do-not-belong-to-current-pr-head',
  });
  assert.deepEqual(reviewSynchronization({ ...current, testsStatus: 'in_progress', testsConclusion: '' }), {
    action: 'wait',
    reason: 'tests-pending',
  });
  assert.deepEqual(reviewSynchronization({ ...current, testsConclusion: 'failure' }), {
    action: 'blocked',
    reason: 'tests-not-successful',
  });
  assert.deepEqual(reviewSynchronization({ ...current, mergeable: 'CONFLICTING' }), {
    action: 'report-conflict',
    reason: 'pull-request-conflicts',
  });
  assert.deepEqual(reviewSynchronization({ ...current, proposalHeadSha: mergeSha }), {
    action: 'refresh',
    reason: 'proposal-head-does-not-match-recorded-head',
  });
});

test('the workflow review adapter rejects a green live head that differs from the recorded generated proposal', () => {
  const result = runWorkflowAdapter('review-workflow', {
    BUILD_CONCLUSION: 'success',
    BUILD_STATUS: 'completed',
    BUILT_SHA: mergeSha,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    CURRENT_MAIN_SHA: sourceSha,
    OBSERVED_PROPOSAL_HEAD_SHA: mergeSha,
    PROPOSAL_PARENT_SHAS: `${targetSha} ${sourceSha}`,
    SYNC_PENDING_LABEL: 'synchronization-pending',
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        baseRefName: 'experimental',
        body: pullRequestBody({ headSha, sourceSha, targetSha }),
        headRefName: 'automation/sync-main-to-experimental',
        headRefOid: mergeSha,
        mergeable: 'MERGEABLE',
        number: 42,
        labels: [{ name: 'synchronization-pending' }],
        state: 'OPEN',
      },
    ]),
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.outputs.action, 'refresh');
  assert.equal(result.outputs.reason, 'proposal-head-does-not-match-recorded-head');

  const invalidTopology = runWorkflowAdapter('review-workflow', {
    BUILD_CONCLUSION: 'success',
    BUILD_STATUS: 'completed',
    BUILT_SHA: headSha,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    CURRENT_MAIN_SHA: sourceSha,
    OBSERVED_PROPOSAL_HEAD_SHA: headSha,
    PROPOSAL_PARENT_SHAS: `${targetSha} ${mergeSha}`,
    SYNC_PENDING_LABEL: 'synchronization-pending',
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        baseRefName: 'experimental',
        body: pullRequestBody({ headSha, sourceSha, targetSha }),
        headRefName: 'automation/sync-main-to-experimental',
        headRefOid: headSha,
        mergeable: 'MERGEABLE',
        number: 42,
        labels: [{ name: 'synchronization-pending' }],
        state: 'OPEN',
      },
    ]),
  });

  assert.equal(invalidTopology.status, 0, invalidTopology.stderr);
  assert.equal(invalidTopology.outputs.action, 'refresh');
  assert.equal(invalidTopology.outputs.reason, 'proposal-topology-does-not-match-recorded-source-and-target');

  const missingPendingState = runWorkflowAdapter('review-workflow', {
    BUILD_CONCLUSION: 'success',
    BUILD_STATUS: 'completed',
    BUILT_SHA: headSha,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    CURRENT_MAIN_SHA: sourceSha,
    OBSERVED_PROPOSAL_HEAD_SHA: headSha,
    PROPOSAL_PARENT_SHAS: `${targetSha} ${sourceSha}`,
    SYNC_PENDING_LABEL: 'synchronization-pending',
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        baseRefName: 'experimental',
        body: pullRequestBody({ headSha, sourceSha, targetSha }),
        headRefName: 'automation/sync-main-to-experimental',
        headRefOid: headSha,
        labels: [],
        mergeable: 'MERGEABLE',
        number: 42,
        state: 'OPEN',
      },
    ]),
  });

  assert.equal(missingPendingState.status, 1);
  assert.match(missingPendingState.stderr, /required pending label/);
});

test('post-merge build dispatch and disposable-branch cleanup occur only after the exact merge reaches experimental', () => {
  const merged = {
    branch: 'automation/sync-main-to-experimental',
    currentExperimentalSha,
    currentMainSha: mergeSha,
    expectedSourceSha: sourceSha,
    expectedTargetSha: targetSha,
    mergeCommitSha: mergeSha,
    mergeReachable: true,
    observedProposalHeadSha: headSha,
    proposalHeadSha: headSha,
    proposalParentShas: [targetSha, sourceSha],
    proposalState: 'MERGED',
    recordedHeadSha: headSha,
  };

  assert.deepEqual(postMergeSynchronization(merged), {
    action: 'complete',
    experimentalSha: currentExperimentalSha,
    steps: ['dispatch-experimental-build', 'delete-disposable-branch'],
  });
  assert.deepEqual(postMergeSynchronization({ ...merged, mergeReachable: false }), {
    action: 'refresh',
    reason: 'merged-commit-is-not-reachable-from-current-experimental',
  });
  assert.deepEqual(
    postMergeSynchronization({
      ...merged,
      currentExperimentalSha: targetSha,
      currentMainSha: sourceSha,
      proposalState: 'OPEN',
    }),
    {
      action: 'schedule-recheck',
      reason: 'pull-request-not-merged',
    },
  );
  assert.deepEqual(postMergeSynchronization({ ...merged, currentMainSha: mergeSha, proposalState: 'OPEN' }), {
    action: 'refresh',
    reason: 'source-branch-moved',
  });
  assert.deepEqual(postMergeSynchronization({ ...merged, currentMainSha: sourceSha, proposalState: 'OPEN' }), {
    action: 'refresh',
    reason: 'target-branch-moved',
  });
});

test('only exact automation-authored completion evidence suppresses durable recovery', () => {
  const completionBody = completionRecordBody({
    experimentalSha: currentExperimentalSha,
    mergeCommitSha: mergeSha,
    proposalHeadSha: headSha,
    pullRequestNumber: 42,
  });
  const pullRequest = {
    baseRefName: 'experimental',
    body: pullRequestBody({ headSha, sourceSha, targetSha }),
    comments: [],
    headRefName: 'automation/sync-main-to-experimental',
    headRefOid: headSha,
    mergeCommit: { oid: mergeSha },
    number: 42,
    state: 'MERGED',
  };
  const forged = runWorkflowAdapter('select-finalizations', {
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        ...pullRequest,
        comments: [{ author: { login: 'octocat' }, body: completionBody }],
      },
    ]),
  });

  assert.equal(forged.status, 0, forged.stderr);
  assert.equal(forged.outputs.count, '1');
  assert.equal(forged.outputs['pull-requests'], '42');

  const trusted = runWorkflowAdapter('select-finalizations', {
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        ...pullRequest,
        comments: [{ author: { login: 'github-actions[bot]' }, body: completionBody }],
      },
    ]),
  });

  assert.equal(trusted.status, 0, trusted.stderr);
  assert.equal(trusted.outputs.count, '0');
  assert.equal(trusted.outputs['pull-requests'], '');

  const mismatched = runWorkflowAdapter('select-finalizations', {
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        ...pullRequest,
        comments: [
          {
            author: { login: 'github-actions[bot]' },
            body: completionBody.replace(mergeSha, sourceSha),
          },
        ],
      },
    ]),
  });

  assert.equal(mismatched.status, 0, mismatched.stderr);
  assert.equal(mismatched.outputs.count, '1');
  assert.equal(mismatched.outputs['pull-requests'], '42');

  const notExact = runWorkflowAdapter('select-finalizations', {
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        ...pullRequest,
        comments: [
          { author: { login: 'github-actions[bot]' }, body: '<!-- seed4j-main-to-experimental-finalized -->' },
          { author: { login: 'github-actions[bot]' }, body: `${completionBody}\nadditional text` },
        ],
      },
    ]),
  });

  assert.equal(notExact.status, 0, notExact.stderr);
  assert.equal(notExact.outputs.count, '1');
  assert.equal(notExact.outputs['pull-requests'], '42');
});

test('bounded file transport carries realistic completion history across the workflow adapter process', () => {
  const history = Array.from({ length: 100 }, (_, index) => {
    const number = index + 1;
    const proposalHeadSha = number.toString(16).padStart(40, '0');
    const mergeCommitSha = (number + 100).toString(16).padStart(40, '0');
    const experimentalSha = (number + 200).toString(16).padStart(40, '0');
    return {
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha: proposalHeadSha, sourceSha, targetSha }),
      comments: [
        { author: { login: 'octocat' }, body: `ordinary discussion ${'x'.repeat(850)}` },
        {
          author: { login: 'github-actions[bot]' },
          body: completionRecordBody({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber: number }),
        },
      ],
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: proposalHeadSha,
      mergeCommit: { oid: mergeCommitSha },
      number,
      state: 'MERGED',
    };
  });
  const accepted = runWorkflowAdapterWithPayload('select-finalizations', JSON.stringify(history));
  const oversized = runWorkflowAdapterWithPayload('select-finalizations', 'x'.repeat(2 * 1024 * 1024 + 1));
  const malformed = runWorkflowAdapterWithPayload('select-finalizations', '[{"number":');

  assert.equal(accepted.status, 0, accepted.stderr);
  assert.equal(accepted.outputs.count, '0');
  assert.equal(accepted.outputs['pull-requests'], '');
  assert.equal(oversized.status, 1);
  assert.match(oversized.stderr, /exceeds.*2097152 bytes/);
  assert.equal(malformed.status, 1);
  assert.match(malformed.stderr, /history is not valid JSON/);
});

test('recovery process bounds realistic, malformed, and oversized GitHub history responses', () => {
  const history = Array.from({ length: 100 }, (_, index) => {
    const number = index + 1;
    const proposalHeadSha = number.toString(16).padStart(40, '0');
    const mergeCommitSha = (number + 100).toString(16).padStart(40, '0');
    const experimentalSha = (number + 200).toString(16).padStart(40, '0');
    return {
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha: proposalHeadSha, sourceSha, targetSha }),
      comments: [
        { author: { login: 'octocat' }, body: `ordinary discussion ${'x'.repeat(850)}` },
        {
          author: { login: 'github-actions[bot]' },
          body: completionRecordBody({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber: number }),
        },
      ],
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: proposalHeadSha,
      labels: [],
      mergeCommit: { oid: mergeCommitSha },
      number,
      state: 'MERGED',
    };
  });
  const accepted = runRawRecoveryPayload(JSON.stringify(history));
  const malformed = runRawRecoveryPayload('[{"number":');
  const oversized = runRawRecoveryPayload('x'.repeat(2 * 1024 * 1024 + 1));

  assert.equal(accepted.status, 0, accepted.stderr);
  assert.equal(malformed.status, 1);
  assert.match(malformed.stderr, /pending synchronization pull requests is not valid JSON/);
  assert.equal(oversized.status, 1);
  assert.match(oversized.stderr, /pending synchronization pull requests exceeds the maximum of 2097152 bytes/);
});

test('bounded recovery deterministically drains every older outstanding finalization', () => {
  const olderHeadSha = '6666666666666666666666666666666666666666';
  const completedHeadSha = '7777777777777777777777777777777777777777';
  const completedMergeSha = '8888888888888888888888888888888888888888';
  const completedExperimentalSha = '9999999999999999999999999999999999999999';
  const history = [
    {
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha: completedHeadSha, sourceSha, targetSha }),
      comments: [
        {
          author: { login: 'github-actions[bot]' },
          body: completionRecordBody({
            experimentalSha: completedExperimentalSha,
            mergeCommitSha: completedMergeSha,
            proposalHeadSha: completedHeadSha,
            pullRequestNumber: 43,
          }),
        },
      ],
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: completedHeadSha,
      mergeCommit: { oid: completedMergeSha },
      number: 43,
      state: 'MERGED',
    },
    {
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha, sourceSha, targetSha }),
      comments: [],
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: headSha,
      mergeCommit: null,
      number: 42,
      state: 'OPEN',
    },
    {
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha: olderHeadSha, sourceSha, targetSha }),
      comments: [],
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: olderHeadSha,
      mergeCommit: { oid: mergeSha },
      number: 40,
      state: 'MERGED',
    },
  ];
  const scheduled = runWorkflowAdapter('select-finalizations', {
    SYNC_PULL_REQUESTS: JSON.stringify(history),
  });

  assert.equal(scheduled.status, 0, scheduled.stderr);
  assert.equal(scheduled.outputs.count, '2');
  assert.equal(scheduled.outputs['pull-requests'], '40,42');

  const requested = runWorkflowAdapter('select-finalizations', {
    REQUESTED_PR_NUMBER: '42',
    SYNC_PULL_REQUESTS: JSON.stringify(history),
  });

  assert.equal(requested.status, 0, requested.stderr);
  assert.equal(requested.outputs.count, '1');
  assert.equal(requested.outputs['pull-requests'], '42');

  const completion = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), 'completion-body'], {
    encoding: 'utf8',
    env: {
      ...process.env,
      EXPERIMENTAL_SHA: currentExperimentalSha,
      MERGE_COMMIT_SHA: mergeSha,
      PROPOSAL_HEAD_SHA: olderHeadSha,
      PULL_REQUEST_NUMBER: '40',
    },
  });

  assert.equal(completion.status, 0, completion.stderr);
  assert.equal(
    completion.stdout,
    completionRecordBody({
      experimentalSha: currentExperimentalSha,
      mergeCommitSha: mergeSha,
      proposalHeadSha: olderHeadSha,
      pullRequestNumber: 40,
    }),
  );
});

test('recovery isolates candidate failures, reuses one exact build, and coalesces refresh', () => {
  const firstHeadSha = '6666666666666666666666666666666666666666';
  const secondHeadSha = '7777777777777777777777777777777777777777';
  const thirdHeadSha = '8888888888888888888888888888888888888888';
  const secondMergeSha = '9999999999999999999999999999999999999999';
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-recovery-boundary-'));
  try {
    const pullRequests = [
      null,
      { ...recoveryPullRequest(39, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', mergeSha, targetSha), body: 'invalid' },
      recoveryPullRequest(40, firstHeadSha, mergeSha, targetSha),
      recoveryPullRequest(41, secondHeadSha, secondMergeSha, targetSha),
      recoveryPullRequest(42, thirdHeadSha, null, targetSha),
    ];
    const scenarioPath = join(directory, 'scenario.json');
    const statePath = join(directory, 'state.json');
    const logPath = join(directory, 'commands.jsonl');
    writeFileSync(
      scenarioPath,
      JSON.stringify({
        currentExperimentalSha,
        currentMainSha: sourceSha,
        failComments: [40],
        proposalParents: {
          [firstHeadSha]: [targetSha, sourceSha],
          [secondHeadSha]: [targetSha, sourceSha],
          [thirdHeadSha]: [targetSha, sourceSha],
        },
        pullRequests,
        rawPullRequestList: JSON.stringify(pullRequests),
        reachableMerges: [mergeSha, secondMergeSha],
        remoteSyncSha: thirdHeadSha,
      }),
    );
    writeFileSync(statePath, JSON.stringify({ pullRequests, runs: [] }));
    installRecoveryBoundaryCommands(directory);

    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: {
        ...process.env,
        GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
        PATH: `${directory}:${process.env.PATH}`,
        SEED4J_TEST_COMMAND_LOG: logPath,
        SEED4J_TEST_SCENARIO: scenarioPath,
        SEED4J_TEST_STATE: statePath,
        SYNC_PENDING_LABEL: 'synchronization-pending',
      },
    });
    const commands = readFileSync(logPath, 'utf8')
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => JSON.parse(line));

    assert.equal(result.status, 1, result.stderr);
    assert.equal(
      commands.filter(command => command.join(' ') === 'workflow run github-actions.yml --repo seed4j/seed4j-cli --ref experimental')
        .length,
      1,
    );
    assert.equal(
      commands.filter(command => command.join(' ') === 'workflow run synchronize-experimental.yml --repo seed4j/seed4j-cli --ref main')
        .length,
      1,
    );
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'comment' && command[2] === '40'),
      true,
    );
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'comment' && command[2] === '41'),
      true,
    );
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'edit' && command[2] === '41' && command.includes('--remove-label')),
      true,
    );
    assert.match(result.stderr, /Pull request #40 finalization failed/);
    assert.match(result.stderr, /Pull request #39 finalization failed/);
    assert.equal(
      commands.some(command => command[0] === 'issue'),
      false,
    );
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('pending label is removed only after trusted completion and retry only reconciles that label', () => {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-recovery-label-'));
  try {
    const pullRequests = [recoveryPullRequest(40, headSha, mergeSha, targetSha)];
    const scenarioPath = join(directory, 'scenario.json');
    const statePath = join(directory, 'state.json');
    const logPath = join(directory, 'commands.jsonl');
    writeFileSync(
      scenarioPath,
      JSON.stringify({
        currentExperimentalSha,
        currentMainSha: sourceSha,
        failComments: [],
        failLabelRemovalOnce: true,
        proposalParents: { [headSha]: [targetSha, sourceSha] },
        pullRequests,
        reachableMerges: [mergeSha],
        remoteSyncSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      }),
    );
    writeFileSync(statePath, JSON.stringify({ pullRequests, runs: [] }));
    installRecoveryBoundaryCommands(directory);
    const environment = {
      ...process.env,
      GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
      PATH: `${directory}:${process.env.PATH}`,
      SEED4J_TEST_COMMAND_LOG: logPath,
      SEED4J_TEST_SCENARIO: scenarioPath,
      SEED4J_TEST_STATE: statePath,
      SYNC_PENDING_LABEL: 'synchronization-pending',
    };
    const first = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: environment,
    });
    const second = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: environment,
    });
    const commands = readFileSync(logPath, 'utf8')
      .trim()
      .split('\n')
      .map(line => JSON.parse(line));

    assert.equal(first.status, 1);
    assert.equal(second.status, 0, second.stderr);
    assert.equal(commands.filter(command => command[0] === 'pr' && command[1] === 'comment').length, 1);
    assert.equal(commands.filter(command => command[0] === 'workflow' && command[2] === 'github-actions.yml').length, 1);
    assert.equal(commands.filter(command => command[0] === 'push').length, 1);
    assert.equal(commands.filter(command => command[0] === 'pr' && command[1] === 'edit').length, 2);
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('server-side pending selection finds old work beyond one hundred completed pull requests', () => {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-recovery-selection-'));
  try {
    const pending = recoveryPullRequest(1, headSha, mergeSha, targetSha);
    const completed = Array.from({ length: 100 }, (_, index) => ({
      ...recoveryPullRequest(index + 2, (index + 20).toString(16).padStart(40, '0'), mergeSha, targetSha),
      labels: [],
    }));
    const scenarioPath = join(directory, 'scenario.json');
    const statePath = join(directory, 'state.json');
    const logPath = join(directory, 'commands.jsonl');
    writeFileSync(
      scenarioPath,
      JSON.stringify({
        currentExperimentalSha,
        currentMainSha: sourceSha,
        failComments: [],
        proposalParents: { [headSha]: [targetSha, sourceSha] },
        reachableMerges: [mergeSha],
        remoteSyncSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      }),
    );
    writeFileSync(statePath, JSON.stringify({ pullRequests: [...completed, pending], runs: [] }));
    installRecoveryBoundaryCommands(directory);

    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: {
        ...process.env,
        GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
        PATH: `${directory}:${process.env.PATH}`,
        SEED4J_TEST_COMMAND_LOG: logPath,
        SEED4J_TEST_SCENARIO: scenarioPath,
        SEED4J_TEST_STATE: statePath,
        SYNC_PENDING_LABEL: 'synchronization-pending',
      },
    });
    const commands = readFileSync(logPath, 'utf8')
      .trim()
      .split('\n')
      .map(line => JSON.parse(line));
    const list = commands.find(command => command[0] === 'pr' && command[1] === 'list');

    assert.equal(result.status, 0, result.stderr);
    assert.equal(list[list.indexOf('--label') + 1], 'synchronization-pending');
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'comment' && command[2] === '1'),
      true,
    );
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('explicit recovery loads the requested pull request directly instead of relying on a list window', () => {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-recovery-direct-'));
  try {
    const pullRequests = [recoveryPullRequest(42, headSha, mergeSha, targetSha)];
    const scenarioPath = join(directory, 'scenario.json');
    const statePath = join(directory, 'state.json');
    const logPath = join(directory, 'commands.jsonl');
    writeFileSync(
      scenarioPath,
      JSON.stringify({
        currentExperimentalSha,
        currentMainSha: sourceSha,
        failComments: [],
        proposalParents: { [headSha]: [targetSha, sourceSha] },
        reachableMerges: [mergeSha],
        remoteSyncSha: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      }),
    );
    writeFileSync(statePath, JSON.stringify({ pullRequests, runs: [] }));
    installRecoveryBoundaryCommands(directory);

    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: {
        ...process.env,
        GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
        PATH: `${directory}:${process.env.PATH}`,
        REQUESTED_PR_NUMBER: '42',
        SEED4J_TEST_COMMAND_LOG: logPath,
        SEED4J_TEST_SCENARIO: scenarioPath,
        SEED4J_TEST_STATE: statePath,
        SYNC_PENDING_LABEL: 'synchronization-pending',
      },
    });
    const commands = readFileSync(logPath, 'utf8')
      .trim()
      .split('\n')
      .map(line => JSON.parse(line));

    assert.equal(result.status, 0, result.stderr);
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'list'),
      false,
    );
    assert.equal(
      commands.some(command => command[0] === 'pr' && command[1] === 'view' && command[2] === '42'),
      true,
    );
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('disposable branch deletion is atomic and keeps a concurrently advanced remote head', () => {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-cleanup-lease-'));
  try {
    const remote = join(directory, 'remote.git');
    const first = join(directory, 'first');
    const second = join(directory, 'second');
    git(directory, ['init', '--bare', remote]);
    git(directory, ['init', first]);
    git(first, ['config', 'user.name', 'Seed4J Test']);
    git(first, ['config', 'user.email', 'seed4j-test@example.com']);
    writeFileSync(join(first, 'proposal.txt'), 'first\n');
    git(first, ['add', 'proposal.txt']);
    git(first, ['commit', '-m', 'first proposal']);
    git(first, ['branch', '-M', 'automation/sync-main-to-experimental']);
    git(first, ['remote', 'add', 'origin', remote]);
    git(first, ['push', '-u', 'origin', 'automation/sync-main-to-experimental']);
    const oldHead = git(first, ['rev-parse', 'HEAD']).stdout.trim();

    git(directory, ['clone', remote, second]);
    git(second, ['config', 'user.name', 'Seed4J Test']);
    git(second, ['config', 'user.email', 'seed4j-test@example.com']);
    git(second, ['switch', '-c', 'automation/sync-main-to-experimental', '--track', 'origin/automation/sync-main-to-experimental']);
    writeFileSync(join(second, 'proposal.txt'), 'second\n');
    git(second, ['add', 'proposal.txt']);
    git(second, ['commit', '-m', 'second proposal']);
    git(second, ['push', 'origin', 'automation/sync-main-to-experimental']);
    const newHead = git(second, ['rev-parse', 'HEAD']).stdout.trim();

    const rejected = runCleanupWorkflow(first, oldHead);
    const remoteAfterRejection = git(first, ['ls-remote', '--heads', 'origin', 'refs/heads/automation/sync-main-to-experimental']).stdout;
    const deleted = runCleanupWorkflow(second, newHead);
    const remoteAfterDeletion = git(second, ['ls-remote', '--heads', 'origin', 'refs/heads/automation/sync-main-to-experimental']).stdout;

    assert.equal(rejected.status, 0, rejected.stderr);
    assert.equal(rejected.outputs.action, 'keep');
    assert.match(remoteAfterRejection, new RegExp(`^${newHead}\\s`));
    assert.equal(deleted.status, 0, deleted.stderr);
    assert.equal(deleted.outputs.action, 'delete');
    assert.equal(remoteAfterDeletion, '');
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('an open auto-merge timeout durably re-enters finalization and an eventual merge completes post-merge work', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');
  const recovery = readFileSync(resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs'), 'utf8');
  const commonEnvironment = {
    CURRENT_MAIN_SHA: sourceSha,
    OBSERVED_PROPOSAL_HEAD_SHA: headSha,
    PROPOSAL_PARENT_SHAS: `${targetSha} ${sourceSha}`,
  };
  const open = runWorkflowAdapter('finalize-workflow', {
    ...commonEnvironment,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    MERGE_REACHABLE: 'false',
    SYNC_PULL_REQUEST: JSON.stringify({
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha, sourceSha, targetSha }),
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: headSha,
      mergeCommit: null,
      number: 42,
      state: 'OPEN',
    }),
  });

  assert.equal(open.status, 0, open.stderr);
  assert.equal(open.outputs.action, 'schedule-recheck');
  assert.equal(open.outputs.reason, 'pull-request-not-merged');

  const movedSource = runWorkflowAdapter('finalize-workflow', {
    ...commonEnvironment,
    CURRENT_EXPERIMENTAL_SHA: targetSha,
    CURRENT_MAIN_SHA: mergeSha,
    MERGE_REACHABLE: 'false',
    SYNC_PULL_REQUEST: JSON.stringify({
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha, sourceSha, targetSha }),
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: headSha,
      mergeCommit: null,
      number: 42,
      state: 'OPEN',
    }),
  });

  assert.equal(movedSource.status, 0, movedSource.stderr);
  assert.equal(movedSource.outputs.action, 'refresh');
  assert.equal(movedSource.outputs.reason, 'source-branch-moved');

  const movedTarget = runWorkflowAdapter('finalize-workflow', {
    ...commonEnvironment,
    CURRENT_EXPERIMENTAL_SHA: currentExperimentalSha,
    MERGE_REACHABLE: 'false',
    SYNC_PULL_REQUEST: JSON.stringify({
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha, sourceSha, targetSha }),
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: headSha,
      mergeCommit: null,
      number: 42,
      state: 'OPEN',
    }),
  });

  assert.equal(movedTarget.status, 0, movedTarget.stderr);
  assert.equal(movedTarget.outputs.action, 'refresh');
  assert.equal(movedTarget.outputs.reason, 'target-branch-moved');

  const merged = runWorkflowAdapter('finalize-workflow', {
    ...commonEnvironment,
    CURRENT_EXPERIMENTAL_SHA: currentExperimentalSha,
    CURRENT_MAIN_SHA: mergeSha,
    MERGE_REACHABLE: 'true',
    SYNC_PULL_REQUEST: JSON.stringify({
      baseRefName: 'experimental',
      body: pullRequestBody({ headSha, sourceSha, targetSha }),
      headRefName: 'automation/sync-main-to-experimental',
      headRefOid: headSha,
      mergeCommit: { oid: mergeSha },
      number: 42,
      state: 'MERGED',
    }),
  });

  assert.equal(merged.status, 0, merged.stderr);
  assert.equal(merged.outputs.action, 'complete');
  assert.equal(merged.outputs.experimental, currentExperimentalSha);
  assert.equal(merged.outputs.merge, mergeSha);
  assert.equal(merged.outputs['pr-number'], '42');
  assert.match(workflow, /schedule:\s+- cron: ['"]\*\/15 \* \* \* \*['"]/);
  assert.match(workflow, /gh workflow run synchronize-experimental\.yml[^\n]*-f finalize-pr="\$PR_NUMBER"/);
  assert.match(workflow, /node scripts\/main-to-experimental-recovery\.cjs/);
  assert.match(recovery, /'pr',\s*'list',[\s\S]*'--label',[\s\S]*pendingLabel[\s\S]*'--limit',\s*'100'/);
  assert.match(recovery, /'pr',\s*'view',[\s\S]*String\(requestedNumber\)/);
  assert.match(recovery, /ensureExactBuild[\s\S]*atomicDisposableBranchCleanup[\s\S]*'pr', 'comment'[\s\S]*removePendingLabel/);
  assert.doesNotMatch(workflow, /SYNC_FINALIZED_MARKER|--limit 1 --json number/);
});

test('the workflow uses only ephemeral scoped permissions, explicit recursion-safe dispatches, and no reverse synchronization', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');
  const prepareJob = workflow.slice(workflow.indexOf('\n  prepare:'), workflow.indexOf('\n  finalize:'));
  const finalizeJob = workflow.slice(workflow.indexOf('\n  finalize:'), workflow.indexOf('\n  recover-finalization:'));
  const recoveryJob = workflow.slice(workflow.indexOf('\n  recover-finalization:'));

  assert.match(workflow, /workflow_run:[\s\S]*workflows:\s+- build/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /permissions: \{\}/);
  assert.match(workflow, /contents: write/);
  assert.match(workflow, /pull-requests: write/);
  assert.match(workflow, /actions: write/);
  assert.match(prepareJob, /issues: write/);
  assert.match(prepareJob, /SYNC_ISSUE_TITLE:/);
  assert.doesNotMatch(finalizeJob, /issues: write|SYNC_ISSUE_TITLE:/);
  assert.doesNotMatch(recoveryJob, /issues: write|SYNC_ISSUE_TITLE:/);
  assert.match(workflow, /git switch -C "\$SYNC_BRANCH" "origin\/experimental"/);
  assert.match(workflow, /git merge --no-ff --no-edit "\$SOURCE_SHA"/);
  assert.match(workflow, /gh pr create[\s\S]*--base experimental[\s\S]*--head "\$SYNC_BRANCH"/);
  assert.match(workflow, /gh workflow run github-actions\.yml[^\n]*--ref "\$SYNC_BRANCH"/);
  assert.match(workflow, /gh pr merge[\s\S]*--auto[\s\S]*--merge/);
  assert.match(
    workflow,
    /for attempt in \$\(seq 1 12\); do[\s\S]*PR_STATE=.*gh pr view[\s\S]*if \[ "\$PR_STATE" = "MERGED" \][\s\S]*sleep 5/,
  );
  assert.match(readFileSync(resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs'), 'utf8'), /'--ref', TARGET_BRANCH/);
  assert.match(
    readFileSync(resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), 'utf8'),
    /--force-with-lease=\$\{branchReference\}:\$\{proposalHeadSha\}/,
  );
  assert.doesNotMatch(workflow, /\bPAT\b|DEPLOY_KEY|personal access token/i);
  assert.doesNotMatch(workflow, /merge[^\n]*experimental[^\n]*main/i);
});

test('the workflow persists pending state and delegates recovery side effects to executable adapters', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');

  assert.match(workflow, /SYNC_PENDING_LABEL: synchronization-pending/);
  assert.match(workflow, /Synchronize: verify durable pending label/);
  assert.match(workflow, /gh pr create[\s\S]*--label "\$SYNC_PENDING_LABEL"/);
  assert.match(workflow, /gh pr edit[\s\S]*--add-label "\$SYNC_PENDING_LABEL"/);
  assert.match(workflow, /SYNC_PULL_REQUESTS_PATH=/);
  assert.match(workflow, /node scripts\/main-to-experimental-recovery\.cjs/);
  assert.doesNotMatch(workflow, /SYNC_PULL_REQUESTS="\$\(gh pr list/);
  assert.doesNotMatch(workflow, /git push origin --delete "\$SYNC_BRANCH"/);
  assert.doesNotMatch(workflow, /recover-finalization:[\s\S]*gh issue close/);
  assert.doesNotMatch(workflow, /recover-finalization:[\s\S]*for PR_NUMBER/);
});

test('dry-run prints a deterministic main-to-experimental plan without external mutation or reverse flow', () => {
  const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), 'dry-run'], {
    encoding: 'utf8',
  });

  assert.equal(result.status, 0, result.stderr);
  const plan = JSON.parse(result.stdout);
  assert.equal(plan.sourceBranch, 'main');
  assert.equal(plan.targetBranch, 'experimental');
  assert.equal(plan.action, 'propose');
  assert.doesNotMatch(result.stdout, /"sourceBranch": "experimental"/);
});

function runWorkflowAdapter(command, environment) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-sync-adapter-'));
  const output = join(directory, 'github-output');
  try {
    const childEnvironment = { ...process.env, ...environment, GITHUB_OUTPUT: output };
    if (childEnvironment.SYNC_PULL_REQUESTS !== undefined) {
      const input = join(directory, 'pull-requests.json');
      writeFileSync(input, childEnvironment.SYNC_PULL_REQUESTS);
      delete childEnvironment.SYNC_PULL_REQUESTS;
      childEnvironment.SYNC_PULL_REQUESTS_PATH = input;
    }
    if (childEnvironment.SYNC_PULL_REQUEST !== undefined) {
      const input = join(directory, 'pull-request.json');
      writeFileSync(input, childEnvironment.SYNC_PULL_REQUEST);
      delete childEnvironment.SYNC_PULL_REQUEST;
      childEnvironment.SYNC_PULL_REQUEST_PATH = input;
    }
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), command], {
      encoding: 'utf8',
      env: childEnvironment,
    });
    return {
      ...result,
      outputs: result.status === 0 ? workflowOutputs(readFileSync(output, 'utf8')) : {},
    };
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

function runWorkflowAdapterWithPayload(command, payload) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-sync-payload-'));
  const input = join(directory, 'input.json');
  const output = join(directory, 'github-output');
  try {
    writeFileSync(input, payload);
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), command], {
      encoding: 'utf8',
      env: { ...process.env, GITHUB_OUTPUT: output, SYNC_PULL_REQUESTS_PATH: input },
    });
    return {
      ...result,
      outputs: result.status === 0 ? workflowOutputs(readFileSync(output, 'utf8')) : {},
    };
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

function runIssueWorkflowAdapter(conflict, issues) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-sync-issues-'));
  const input = join(directory, 'issues.json');
  const output = join(directory, 'github-output');
  try {
    writeFileSync(input, JSON.stringify(issues));
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), 'issue-workflow'], {
      encoding: 'utf8',
      env: {
        ...process.env,
        GITHUB_OUTPUT: output,
        SYNC_CONFLICT: String(conflict),
        SYNC_ISSUES_PATH: input,
      },
    });
    return {
      ...result,
      outputs: result.status === 0 ? workflowOutputs(readFileSync(output, 'utf8')) : {},
    };
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

function runRawRecoveryPayload(payload) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-raw-recovery-'));
  try {
    const scenarioPath = join(directory, 'scenario.json');
    const statePath = join(directory, 'state.json');
    const logPath = join(directory, 'commands.jsonl');
    writeFileSync(scenarioPath, JSON.stringify({ rawPullRequestList: payload }));
    writeFileSync(statePath, JSON.stringify({ pullRequests: [], runs: [] }));
    installRecoveryBoundaryCommands(directory);
    return spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-recovery.cjs')], {
      encoding: 'utf8',
      env: {
        ...process.env,
        GITHUB_REPOSITORY: 'seed4j/seed4j-cli',
        PATH: `${directory}:${process.env.PATH}`,
        SEED4J_TEST_COMMAND_LOG: logPath,
        SEED4J_TEST_SCENARIO: scenarioPath,
        SEED4J_TEST_STATE: statePath,
        SYNC_PENDING_LABEL: 'synchronization-pending',
      },
    });
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
}

function runCleanupWorkflow(directory, proposalHeadSha) {
  const output = join(directory, `github-output-${proposalHeadSha}`);
  const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), 'cleanup-workflow'], {
    cwd: directory,
    encoding: 'utf8',
    env: { ...process.env, GITHUB_OUTPUT: output, PROPOSAL_HEAD_SHA: proposalHeadSha },
  });
  return {
    ...result,
    outputs: result.status === 0 ? workflowOutputs(readFileSync(output, 'utf8')) : {},
  };
}

function git(directory, arguments_) {
  const result = spawnSync('git', arguments_, { cwd: directory, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  return result;
}

function recoveryPullRequest(number, proposalHeadSha, mergeCommitSha, proposalTargetSha) {
  return {
    baseRefName: 'experimental',
    body: pullRequestBody({ headSha: proposalHeadSha, sourceSha, targetSha: proposalTargetSha }),
    comments: [],
    headRefName: 'automation/sync-main-to-experimental',
    headRefOid: proposalHeadSha,
    labels: [{ name: 'synchronization-pending' }],
    mergeCommit: mergeCommitSha ? { oid: mergeCommitSha } : null,
    number,
    state: mergeCommitSha ? 'MERGED' : 'OPEN',
  };
}

function installRecoveryBoundaryCommands(directory) {
  const support = String.raw`#!/usr/bin/env node
const { appendFileSync, readFileSync, writeFileSync } = require('node:fs');
const args = process.argv.slice(2);
const scenario = JSON.parse(readFileSync(process.env.SEED4J_TEST_SCENARIO, 'utf8'));
const state = JSON.parse(readFileSync(process.env.SEED4J_TEST_STATE, 'utf8'));
appendFileSync(process.env.SEED4J_TEST_COMMAND_LOG, JSON.stringify(args) + '\n');
function save() { writeFileSync(process.env.SEED4J_TEST_STATE, JSON.stringify(state)); }
if (process.argv[1].endsWith('/gh')) {
  if (args[0] === 'pr' && args[1] === 'list') {
    if (scenario.rawPullRequestList !== undefined) process.stdout.write(scenario.rawPullRequestList);
    else {
      const label = args.includes('--label') ? args[args.indexOf('--label') + 1] : undefined;
      process.stdout.write(JSON.stringify(label ? state.pullRequests.filter(pr => pr.labels.some(item => item.name === label)) : state.pullRequests));
    }
  }
  else if (args[0] === 'pr' && args[1] === 'view') process.stdout.write(JSON.stringify(state.pullRequests.find(pr => String(pr?.number) === args[2])));
  else if (args[0] === 'run' && args[1] === 'list') process.stdout.write(JSON.stringify(state.runs));
  else if (args[0] === 'workflow' && args[1] === 'run' && args[2] === 'github-actions.yml') {
    state.runs.push({ conclusion: '', event: 'workflow_dispatch', headBranch: 'experimental', headSha: scenario.currentExperimentalSha, status: 'queued' });
    save();
  } else if (args[0] === 'workflow' && args[1] === 'run') {
  } else if (args[0] === 'pr' && args[1] === 'comment') {
    if (scenario.failComments.includes(Number(args[2]))) process.exit(1);
    const pullRequest = state.pullRequests.find(pr => String(pr?.number) === args[2]);
    pullRequest.comments.push({ author: { login: 'github-actions[bot]' }, body: readFileSync(args[args.indexOf('--body-file') + 1], 'utf8') });
    save();
  } else if (args[0] === 'pr' && args[1] === 'edit' && args.includes('--remove-label')) {
    if (scenario.failLabelRemovalOnce && !state.labelFailureConsumed) {
      state.labelFailureConsumed = true;
      save();
      process.exit(1);
    }
    const pullRequest = state.pullRequests.find(pr => String(pr?.number) === args[2]);
    pullRequest.labels = pullRequest.labels.filter(label => label.name !== args[args.indexOf('--remove-label') + 1]);
    save();
  } else process.exit(2);
} else {
  if (args[0] === 'fetch') {
  } else if (args[0] === 'rev-parse' && args[1] === 'origin/main') process.stdout.write(scenario.currentMainSha + '\n');
  else if (args[0] === 'rev-parse' && args[1] === 'origin/experimental') process.stdout.write(scenario.currentExperimentalSha + '\n');
  else if (args[0] === 'rev-parse' && args[1] === 'FETCH_HEAD') {
    const number = Number(state.lastFetchedPullRequest);
    process.stdout.write(state.pullRequests.find(pr => pr?.number === number).headRefOid + '\n');
  } else if (args[0] === 'show') {
    const head = args.at(-1);
    process.stdout.write(scenario.proposalParents[head].join(' ') + '\n');
  } else if (args[0] === 'merge-base') {
    process.exit(scenario.reachableMerges.includes(args[2]) ? 0 : 1);
  } else if (args[0] === 'push') {
  } else if (args[0] === 'ls-remote') process.stdout.write(scenario.remoteSyncSha + '\trefs/heads/automation/sync-main-to-experimental\n');
  else process.exit(2);
  if (args[0] === 'fetch' && args[2] && args[2].startsWith('pull/')) {
    state.lastFetchedPullRequest = Number(args[2].split('/')[1]);
    save();
  }
}`;
  for (const command of ['gh', 'git']) {
    const path = join(directory, command);
    writeFileSync(path, support);
    chmodSync(path, 0o755);
  }
}

function completionRecordBody({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber }) {
  return `Synchronization finalization completed by the trusted repository workflow.

<!-- seed4j-main-to-experimental-completion:start -->
\`\`\`json
${JSON.stringify({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber }, null, 2)}
\`\`\`
<!-- seed4j-main-to-experimental-completion:end -->
`;
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
