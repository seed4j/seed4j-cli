const { appendFileSync } = require('node:fs');

const SYNC_BRANCH = 'automation/sync-main-to-experimental';
const SOURCE_BRANCH = 'main';
const TARGET_BRANCH = 'experimental';
const STATE_START = '<!-- seed4j-main-to-experimental-state:start -->';
const STATE_END = '<!-- seed4j-main-to-experimental-state:end -->';

function prepareSynchronization({
  buildConclusion,
  buildEvent,
  buildHeadBranch,
  builtSha,
  currentExperimentalSha,
  currentMainSha,
  mergeResult,
  proposalHeadSha,
  proposalSourceSha,
  proposalTargetSha,
}) {
  requireSha(currentMainSha, 'current main SHA');
  requireSha(currentExperimentalSha, 'current experimental SHA');
  if (buildConclusion !== 'success' || buildEvent !== 'push' || buildHeadBranch !== SOURCE_BRANCH) {
    return Object.freeze({ action: 'ignore', reason: 'not-a-successful-main-push-build' });
  }
  requireSha(builtSha, 'built SHA');
  if (builtSha !== currentMainSha) {
    return Object.freeze({ action: 'wait', reason: 'successful-main-build-is-stale' });
  }
  requireSha(proposalSourceSha, 'proposal source SHA');
  requireSha(proposalTargetSha, 'proposal target SHA');
  if (proposalSourceSha !== currentMainSha) {
    return Object.freeze({ action: 'refresh', reason: 'main-moved-during-preparation' });
  }
  if (proposalTargetSha !== currentExperimentalSha) {
    return Object.freeze({ action: 'refresh', reason: 'experimental-moved-during-preparation' });
  }
  if (mergeResult === 'conflict') {
    return Object.freeze({
      action: 'report-conflict',
      sourceSha: currentMainSha,
      targetSha: currentExperimentalSha,
    });
  }
  if (mergeResult !== 'clean') {
    throw new Error(`Unsupported synchronization merge result '${mergeResult ?? ''}'.`);
  }
  requireSha(proposalHeadSha, 'proposal head SHA');
  return Object.freeze({
    action: 'propose',
    branch: SYNC_BRANCH,
    headSha: proposalHeadSha,
    sourceBranch: SOURCE_BRANCH,
    sourceSha: currentMainSha,
    steps: ['push-proposal', 'open-or-refresh-pr', 'close-conflict-issue', 'dispatch-tests'],
    targetBranch: TARGET_BRANCH,
    targetSha: currentExperimentalSha,
  });
}

function reviewSynchronization({
  currentExperimentalSha,
  currentMainSha,
  expectedSourceSha,
  expectedTargetSha,
  mergeable,
  proposalBaseSha,
  proposalHeadSha,
  proposalState,
  testedHeadSha,
  testsConclusion,
  testsStatus,
}) {
  for (const [value, label] of [
    [currentExperimentalSha, 'current experimental SHA'],
    [currentMainSha, 'current main SHA'],
    [expectedSourceSha, 'expected source SHA'],
    [expectedTargetSha, 'expected target SHA'],
    [proposalBaseSha, 'proposal base SHA'],
    [proposalHeadSha, 'proposal head SHA'],
    [testedHeadSha, 'tested head SHA'],
  ]) {
    requireSha(value, label);
  }
  if (currentMainSha !== expectedSourceSha) {
    return Object.freeze({ action: 'refresh', reason: 'source-branch-moved' });
  }
  if (currentExperimentalSha !== expectedTargetSha || proposalBaseSha !== currentExperimentalSha) {
    return Object.freeze({ action: 'refresh', reason: 'target-branch-moved' });
  }
  if (proposalState !== 'OPEN') {
    return Object.freeze({ action: 'refresh', reason: 'pull-request-not-open' });
  }
  if (mergeable === 'CONFLICTING') {
    return Object.freeze({ action: 'report-conflict', reason: 'pull-request-conflicts' });
  }
  if (mergeable !== 'MERGEABLE') {
    return Object.freeze({ action: 'wait', reason: 'mergeability-pending' });
  }
  if (testedHeadSha !== proposalHeadSha) {
    return Object.freeze({
      action: 'refresh',
      reason: 'tests-do-not-belong-to-current-pr-head',
    });
  }
  if (testsStatus !== 'completed') {
    return Object.freeze({ action: 'wait', reason: 'tests-pending' });
  }
  if (testsConclusion !== 'success') {
    return Object.freeze({ action: 'blocked', reason: 'tests-not-successful' });
  }
  return Object.freeze({ action: 'enable-auto-merge', headSha: proposalHeadSha });
}

function postMergeSynchronization({ branch, currentExperimentalSha, mergeCommitSha, proposalState }) {
  if (branch !== SYNC_BRANCH) {
    throw new Error(`Unexpected synchronization branch '${branch ?? ''}'.`);
  }
  if (proposalState !== 'MERGED') {
    return Object.freeze({ action: 'wait', reason: 'pull-request-not-merged' });
  }
  requireSha(currentExperimentalSha, 'current experimental SHA');
  requireSha(mergeCommitSha, 'merge commit SHA');
  if (currentExperimentalSha !== mergeCommitSha) {
    return Object.freeze({
      action: 'refresh',
      reason: 'merged-commit-is-not-current-experimental',
    });
  }
  return Object.freeze({
    action: 'complete',
    experimentalSha: currentExperimentalSha,
    steps: ['dispatch-experimental-build', 'delete-disposable-branch', 'close-conflict-issue'],
  });
}

function conflictIssueAction({ conflict, openIssueNumber }) {
  if (!conflict) {
    return openIssueNumber ? Object.freeze({ action: 'close', issueNumber: openIssueNumber }) : Object.freeze({ action: 'none' });
  }
  return Object.freeze({
    action: openIssueNumber ? 'update' : 'create',
    assignees: ['renanfranca'],
    ...(openIssueNumber ? { issueNumber: openIssueNumber } : {}),
    labels: ['synchronization-failure'],
    title: '[synchronization] main to experimental conflict',
  });
}

function pullRequestBody({ headSha, sourceSha, targetSha }) {
  requireSha(sourceSha, 'source SHA');
  requireSha(targetSha, 'target SHA');
  requireSha(headSha, 'head SHA');
  return `Synchronize the exact successful \`main\` revision into the then-current \`experimental\` branch.

This pull request is automation-owned. Its standard \`tests\` check is explicitly dispatched for the exact head commit before auto-merge is enabled.

${STATE_START}
\`\`\`json
${JSON.stringify({ headSha, sourceSha, targetSha }, null, 2)}
\`\`\`
${STATE_END}
`;
}

function synchronizationStateFromPullRequest(body) {
  const value = body ?? '';
  const start = value.indexOf(STATE_START);
  const end = value.indexOf(STATE_END);
  if (start < 0 || end < 0 || value.lastIndexOf(STATE_START) !== start || value.lastIndexOf(STATE_END) !== end) {
    throw new Error('Pull request must contain exactly one synchronization state block.');
  }
  const marked = value.slice(start + STATE_START.length, end).trim();
  const match = /^```json\n([\s\S]+)\n```$/.exec(marked);
  let state;
  try {
    state = match ? JSON.parse(match[1]) : undefined;
  } catch (_) {
    throw new Error('Pull request synchronization state is not valid JSON.');
  }
  if (!state || Array.isArray(state) || Object.keys(state).sort().join('\n') !== ['headSha', 'sourceSha', 'targetSha'].join('\n')) {
    throw new Error('Pull request synchronization state fields do not match the allowlist.');
  }
  requireSha(state.sourceSha, 'source SHA');
  requireSha(state.targetSha, 'target SHA');
  requireSha(state.headSha, 'head SHA');
  return Object.freeze(state);
}

function requireSha(value, label) {
  if (!/^[0-9a-f]{40}$/.test(value ?? '')) {
    throw new Error(`Invalid ${label} '${value ?? ''}'.`);
  }
}

function dryRun() {
  const sourceSha = '1111111111111111111111111111111111111111';
  const targetSha = '2222222222222222222222222222222222222222';
  return prepareSynchronization({
    buildConclusion: 'success',
    buildEvent: 'push',
    buildHeadBranch: SOURCE_BRANCH,
    builtSha: sourceSha,
    currentExperimentalSha: targetSha,
    currentMainSha: sourceSha,
    mergeResult: 'clean',
    proposalHeadSha: '3333333333333333333333333333333333333333',
    proposalSourceSha: sourceSha,
    proposalTargetSha: targetSha,
  });
}

function workflowReview(environment) {
  let pullRequests;
  try {
    pullRequests = JSON.parse(environment.SYNC_PULL_REQUESTS);
  } catch (_) {
    throw new Error('Synchronization pull request response is not valid JSON.');
  }
  if (!Array.isArray(pullRequests) || pullRequests.length !== 1) {
    throw new Error(
      `Synchronization requires exactly one open automation pull request; found ${Array.isArray(pullRequests) ? pullRequests.length : 0}.`,
    );
  }
  const pullRequest = pullRequests[0];
  if (pullRequest.baseRefName !== TARGET_BRANCH || pullRequest.headRefName !== SYNC_BRANCH || !Number.isSafeInteger(pullRequest.number)) {
    throw new Error('Synchronization pull request branches or number are invalid.');
  }
  const expected = synchronizationStateFromPullRequest(pullRequest.body);
  return Object.freeze({
    decision: reviewSynchronization({
      currentExperimentalSha: environment.CURRENT_EXPERIMENTAL_SHA,
      currentMainSha: environment.CURRENT_MAIN_SHA,
      expectedSourceSha: expected.sourceSha,
      expectedTargetSha: expected.targetSha,
      mergeable: pullRequest.mergeable,
      proposalBaseSha: environment.CURRENT_EXPERIMENTAL_SHA,
      proposalHeadSha: pullRequest.headRefOid,
      proposalState: pullRequest.state,
      testedHeadSha: environment.BUILT_SHA,
      testsConclusion: environment.BUILD_CONCLUSION,
      testsStatus: environment.BUILD_STATUS,
    }),
    expectedSourceSha: expected.sourceSha,
    expectedTargetSha: expected.targetSha,
    pullRequestNumber: pullRequest.number,
  });
}

function writeWorkflowOutputs(values) {
  if (!process.env.GITHUB_OUTPUT) {
    throw new Error('GITHUB_OUTPUT is required for synchronization workflow output.');
  }
  for (const [key, value] of Object.entries(values)) {
    appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
  }
}

if (require.main === module) {
  if (process.argv[2] === 'dry-run' && process.argv.length === 3) {
    console.log(JSON.stringify(dryRun(), null, 2));
  } else if (process.argv[2] === 'pr-body' && process.argv.length === 3) {
    process.stdout.write(
      pullRequestBody({
        headSha: process.env.PROPOSAL_HEAD_SHA,
        sourceSha: process.env.SOURCE_SHA,
        targetSha: process.env.TARGET_SHA,
      }),
    );
  } else if (process.argv[2] === 'review-workflow' && process.argv.length === 3) {
    try {
      const review = workflowReview(process.env);
      writeWorkflowOutputs({
        action: review.decision.action,
        head: review.decision.headSha ?? '',
        'pr-number': review.pullRequestNumber,
        reason: review.decision.reason ?? '',
        source: review.expectedSourceSha,
        target: review.expectedTargetSha,
      });
    } catch (error) {
      console.error(error.message);
      process.exitCode = 1;
    }
  } else {
    console.error('Usage: node scripts/main-to-experimental-sync.cjs dry-run');
    process.exitCode = 1;
  }
}

module.exports = {
  conflictIssueAction,
  pullRequestBody,
  postMergeSynchronization,
  prepareSynchronization,
  reviewSynchronization,
  synchronizationStateFromPullRequest,
};
