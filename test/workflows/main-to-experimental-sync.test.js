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
  assert.deepEqual(postMergeSynchronization({ ...merged, proposalState: 'OPEN' }), {
    action: 'schedule-recheck',
    reason: 'pull-request-not-merged',
  });
});

test('an open auto-merge timeout durably re-enters finalization and an eventual merge completes post-merge work', () => {
  const workflow = readFileSync(resolve(repositoryRoot, '.github/workflows/synchronize-experimental.yml'), 'utf8');
  const commonEnvironment = {
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

  const merged = runWorkflowAdapter('finalize-workflow', {
    ...commonEnvironment,
    CURRENT_EXPERIMENTAL_SHA: currentExperimentalSha,
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
  assert.match(workflow, /node scripts\/main-to-experimental-sync\.cjs finalize-workflow/);
  assert.match(workflow, /steps\.finalization\.outputs\.action == 'complete'[\s\S]*gh workflow run github-actions\.yml/);
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
