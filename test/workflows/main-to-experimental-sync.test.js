const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdtempSync, readFileSync, rmSync } = require('node:fs');
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
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        baseRefName: 'experimental',
        body: pullRequestBody({ headSha, sourceSha, targetSha }),
        headRefName: 'automation/sync-main-to-experimental',
        headRefOid: mergeSha,
        mergeable: 'MERGEABLE',
        number: 42,
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
    SYNC_PULL_REQUESTS: JSON.stringify([
      {
        baseRefName: 'experimental',
        body: pullRequestBody({ headSha, sourceSha, targetSha }),
        headRefName: 'automation/sync-main-to-experimental',
        headRefOid: headSha,
        mergeable: 'MERGEABLE',
        number: 42,
        state: 'OPEN',
      },
    ]),
  });

  assert.equal(invalidTopology.status, 0, invalidTopology.stderr);
  assert.equal(invalidTopology.outputs.action, 'refresh');
  assert.equal(invalidTopology.outputs.reason, 'proposal-topology-does-not-match-recorded-source-and-target');
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
    steps: ['dispatch-experimental-build', 'delete-disposable-branch', 'close-conflict-issue'],
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

  const keptNewerBranch = runWorkflowAdapter('cleanup-workflow', {
    PROPOSAL_HEAD_SHA: olderHeadSha,
    REMOTE_SYNC_SHA: headSha,
  });
  const deletedExactBranch = runWorkflowAdapter('cleanup-workflow', {
    PROPOSAL_HEAD_SHA: olderHeadSha,
    REMOTE_SYNC_SHA: olderHeadSha,
  });

  assert.equal(keptNewerBranch.status, 0, keptNewerBranch.stderr);
  assert.equal(keptNewerBranch.outputs.action, 'keep');
  assert.equal(deletedExactBranch.status, 0, deletedExactBranch.stderr);
  assert.equal(deletedExactBranch.outputs.action, 'delete');
});

test('an open auto-merge timeout durably re-enters finalization and an eventual merge completes post-merge work', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');
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
  assert.match(workflow, /gh pr list[^\n]*--state all[^\n]*--limit 100[^\n]*comments/);
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs select-finalizations/);
  assert.match(workflow, /for PR_NUMBER in "\$\{PULL_REQUEST_NUMBERS\[@\]\}"/);
  assert.match(workflow, /git fetch origin main experimental/);
  assert.match(workflow, /CURRENT_MAIN_SHA="\$\(git rev-parse origin\/main\)"/);
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs finalize-workflow/);
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs completion-body/);
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs cleanup-workflow/);
  assert.match(workflow, /if \[ "\$CLEANUP_ACTION" = 'delete' \]/);
  assert.match(workflow, /gh workflow run github-actions\.yml[^\n]*--ref experimental[\s\S]*cleanup-workflow[\s\S]*completion-body/);
  assert.doesNotMatch(workflow, /SYNC_FINALIZED_MARKER|--limit 1 --json number/);
});

test('the workflow uses only ephemeral scoped permissions, explicit recursion-safe dispatches, and no reverse synchronization', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');

  assert.match(workflow, /workflow_run:[\s\S]*workflows:\s+- build/);
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /permissions: \{\}/);
  assert.match(workflow, /contents: write/);
  assert.match(workflow, /pull-requests: write/);
  assert.match(workflow, /actions: write/);
  assert.match(workflow, /issues: write/);
  assert.match(workflow, /git switch -C "\$SYNC_BRANCH" "origin\/experimental"/);
  assert.match(workflow, /git merge --no-ff --no-edit "\$SOURCE_SHA"/);
  assert.match(workflow, /gh pr create[\s\S]*--base experimental[\s\S]*--head "\$SYNC_BRANCH"/);
  assert.match(workflow, /gh workflow run github-actions\.yml[^\n]*--ref "\$SYNC_BRANCH"/);
  assert.match(workflow, /gh pr merge[\s\S]*--auto[\s\S]*--merge/);
  assert.match(
    workflow,
    /for attempt in \$\(seq 1 12\); do[\s\S]*PR_STATE=.*gh pr view[\s\S]*if \[ "\$PR_STATE" = "MERGED" \][\s\S]*sleep 5/,
  );
  assert.match(workflow, /gh workflow run github-actions\.yml[^\n]*--ref experimental/);
  assert.match(workflow, /git push origin --delete "\$SYNC_BRANCH"/);
  assert.doesNotMatch(workflow, /\bPAT\b|DEPLOY_KEY|personal access token/i);
  assert.doesNotMatch(workflow, /merge[^\n]*experimental[^\n]*main/i);
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
    const result = spawnSync(process.execPath, [resolve(repositoryRoot, 'scripts/main-to-experimental-sync.cjs'), command], {
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
