const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
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
      proposalBaseSha: targetSha,
      proposalHeadSha: headSha,
      proposalState: 'OPEN',
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
    proposalBaseSha: targetSha,
    proposalHeadSha: headSha,
    proposalState: 'OPEN',
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
    reason: 'tests-do-not-belong-to-current-pr-head',
  });
});

test('post-merge build dispatch and disposable-branch cleanup occur only after the exact merge reaches experimental', () => {
  const merged = {
    branch: 'automation/sync-main-to-experimental',
    currentExperimentalSha: mergeSha,
    mergeCommitSha: mergeSha,
    proposalState: 'MERGED',
  };

  assert.deepEqual(postMergeSynchronization(merged), {
    action: 'complete',
    experimentalSha: mergeSha,
    steps: ['dispatch-experimental-build', 'delete-disposable-branch', 'close-conflict-issue'],
  });
  assert.deepEqual(postMergeSynchronization({ ...merged, currentExperimentalSha: sourceSha }), {
    action: 'refresh',
    reason: 'merged-commit-is-not-current-experimental',
  });
  assert.deepEqual(postMergeSynchronization({ ...merged, proposalState: 'OPEN' }), { action: 'wait', reason: 'pull-request-not-merged' });
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
