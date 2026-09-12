const { appendFileSync, readFileSync, statSync } = require('node:fs');
const { spawnSync } = require('node:child_process');

const SYNC_BRANCH = 'automation/sync-main-to-experimental';
const SOURCE_BRANCH = 'main';
const TARGET_BRANCH = 'experimental';
const STATE_START = '<!-- seed4j-main-to-experimental-state:start -->';
const STATE_END = '<!-- seed4j-main-to-experimental-state:end -->';
const COMPLETION_START = '<!-- seed4j-main-to-experimental-completion:start -->';
const COMPLETION_END = '<!-- seed4j-main-to-experimental-completion:end -->';
const AUTOMATION_LOGIN = 'github-actions[bot]';
const MAXIMUM_JSON_BYTES = 2 * 1024 * 1024;
const SYNC_ISSUE_TITLE = '[synchronization] main to experimental conflict';

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
  if (mergeResult === 'already-contained') {
    return Object.freeze({
      action: 'already-contained',
      sourceSha: currentMainSha,
      targetSha: currentExperimentalSha,
    });
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
    steps: ['push-proposal', 'open-or-refresh-pr', 'dispatch-tests', 'close-conflict-issue'],
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
  observedProposalHeadSha,
  proposalHeadSha,
  proposalParentShas,
  proposalState,
  recordedHeadSha,
  testedHeadSha,
  testsConclusion,
  testsStatus,
}) {
  for (const [value, label] of [
    [currentExperimentalSha, 'current experimental SHA'],
    [currentMainSha, 'current main SHA'],
    [expectedSourceSha, 'expected source SHA'],
    [expectedTargetSha, 'expected target SHA'],
    [observedProposalHeadSha, 'observed proposal head SHA'],
    [proposalHeadSha, 'proposal head SHA'],
    [recordedHeadSha, 'recorded proposal head SHA'],
    [testedHeadSha, 'tested head SHA'],
  ]) {
    requireSha(value, label);
  }
  requireProposalParents(proposalParentShas);
  if (currentMainSha !== expectedSourceSha) {
    return Object.freeze({ action: 'refresh', reason: 'source-branch-moved' });
  }
  if (currentExperimentalSha !== expectedTargetSha) {
    return Object.freeze({ action: 'refresh', reason: 'target-branch-moved' });
  }
  const invalidEvidence = proposalEvidence({
    expectedSourceSha,
    expectedTargetSha,
    observedProposalHeadSha,
    proposalHeadSha,
    proposalParentShas,
    recordedHeadSha,
  });
  if (invalidEvidence) {
    return invalidEvidence;
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

function requireProposalParents(proposalParentShas) {
  if (!Array.isArray(proposalParentShas) || proposalParentShas.length !== 2) {
    throw new Error('Synchronization proposal must have exactly two parents.');
  }
  requireSha(proposalParentShas[0], 'proposal target parent SHA');
  requireSha(proposalParentShas[1], 'proposal source parent SHA');
}

function proposalEvidence({
  expectedSourceSha,
  expectedTargetSha,
  observedProposalHeadSha,
  proposalHeadSha,
  proposalParentShas,
  recordedHeadSha,
}) {
  for (const [value, label] of [
    [expectedSourceSha, 'expected source SHA'],
    [expectedTargetSha, 'expected target SHA'],
    [observedProposalHeadSha, 'observed proposal head SHA'],
    [proposalHeadSha, 'proposal head SHA'],
    [recordedHeadSha, 'recorded proposal head SHA'],
  ]) {
    requireSha(value, label);
  }
  requireProposalParents(proposalParentShas);
  if (proposalHeadSha !== recordedHeadSha || observedProposalHeadSha !== recordedHeadSha) {
    return Object.freeze({ action: 'refresh', reason: 'proposal-head-does-not-match-recorded-head' });
  }
  if (proposalParentShas[0] !== expectedTargetSha || proposalParentShas[1] !== expectedSourceSha) {
    return Object.freeze({ action: 'refresh', reason: 'proposal-topology-does-not-match-recorded-source-and-target' });
  }
  return undefined;
}

function postMergeSynchronization({
  branch,
  currentExperimentalSha,
  currentMainSha,
  expectedSourceSha,
  expectedTargetSha,
  mergeCommitSha,
  mergeReachable,
  observedProposalHeadSha,
  proposalHeadSha,
  proposalParentShas,
  proposalState,
  recordedHeadSha,
}) {
  if (branch !== SYNC_BRANCH) {
    throw new Error(`Unexpected synchronization branch '${branch ?? ''}'.`);
  }
  requireSha(currentExperimentalSha, 'current experimental SHA');
  requireSha(currentMainSha, 'current main SHA');
  const invalidEvidence = proposalEvidence({
    expectedSourceSha,
    expectedTargetSha,
    observedProposalHeadSha,
    proposalHeadSha,
    proposalParentShas,
    recordedHeadSha,
  });
  if (invalidEvidence) {
    return invalidEvidence;
  }
  if (proposalState === 'OPEN') {
    if (currentMainSha !== expectedSourceSha) {
      return Object.freeze({ action: 'refresh', reason: 'source-branch-moved' });
    }
    if (currentExperimentalSha !== expectedTargetSha) {
      return Object.freeze({ action: 'refresh', reason: 'target-branch-moved' });
    }
    return Object.freeze({ action: 'schedule-recheck', reason: 'pull-request-not-merged' });
  }
  if (proposalState !== 'MERGED') {
    return Object.freeze({ action: 'blocked', reason: 'pull-request-closed-without-merge' });
  }
  requireSha(mergeCommitSha, 'merge commit SHA');
  if (mergeReachable !== true) {
    return Object.freeze({
      action: 'refresh',
      reason: 'merged-commit-is-not-reachable-from-current-experimental',
    });
  }
  return Object.freeze({
    action: 'complete',
    experimentalSha: currentExperimentalSha,
    steps: ['dispatch-experimental-build', 'delete-disposable-branch'],
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

function workflowConflictIssue(environment) {
  if (!['true', 'false'].includes(environment.SYNC_CONFLICT)) {
    throw new Error(`Invalid synchronization conflict state '${environment.SYNC_CONFLICT ?? ''}'.`);
  }
  const issues = readBoundedJson(environment.SYNC_ISSUES_PATH, 'Synchronization conflict issue response');
  if (!Array.isArray(issues) || issues.length > 100) {
    throw new Error('Synchronization conflict issue response must contain at most 100 entries.');
  }
  const matchingIssues = issues.filter(issue => issue?.title === SYNC_ISSUE_TITLE);
  if (matchingIssues.some(issue => !Number.isSafeInteger(issue.number) || issue.number <= 0)) {
    throw new Error('Synchronization conflict issue response contains an invalid issue number.');
  }
  if (matchingIssues.length > 1) {
    throw new Error(`Expected at most one open synchronization conflict issue; found ${matchingIssues.length}.`);
  }
  return conflictIssueAction({
    conflict: environment.SYNC_CONFLICT === 'true',
    openIssueNumber: matchingIssues[0]?.number,
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
  const pullRequests = readBoundedJson(environment.SYNC_PULL_REQUESTS_PATH, 'Synchronization pull request response');
  if (!Array.isArray(pullRequests) || pullRequests.length !== 1) {
    throw new Error(
      `Synchronization requires exactly one open automation pull request; found ${Array.isArray(pullRequests) ? pullRequests.length : 0}.`,
    );
  }
  const pullRequest = pullRequests[0];
  if (pullRequest.baseRefName !== TARGET_BRANCH || pullRequest.headRefName !== SYNC_BRANCH || !Number.isSafeInteger(pullRequest.number)) {
    throw new Error('Synchronization pull request branches or number are invalid.');
  }
  const pendingLabel = environment.SYNC_PENDING_LABEL;
  if (!pendingLabel || !(pullRequest.labels ?? []).some(label => label?.name === pendingLabel)) {
    throw new Error('Synchronization pull request is missing its required pending label.');
  }
  const expected = synchronizationStateFromPullRequest(pullRequest.body);
  return Object.freeze({
    decision: reviewSynchronization({
      currentExperimentalSha: environment.CURRENT_EXPERIMENTAL_SHA,
      currentMainSha: environment.CURRENT_MAIN_SHA,
      expectedSourceSha: expected.sourceSha,
      expectedTargetSha: expected.targetSha,
      mergeable: pullRequest.mergeable,
      observedProposalHeadSha: environment.OBSERVED_PROPOSAL_HEAD_SHA,
      proposalHeadSha: pullRequest.headRefOid,
      proposalParentShas: proposalParents(environment.PROPOSAL_PARENT_SHAS),
      proposalState: pullRequest.state,
      recordedHeadSha: expected.headSha,
      testedHeadSha: environment.BUILT_SHA,
      testsConclusion: environment.BUILD_CONCLUSION,
      testsStatus: environment.BUILD_STATUS,
    }),
    expectedSourceSha: expected.sourceSha,
    expectedTargetSha: expected.targetSha,
    pullRequestNumber: pullRequest.number,
  });
}

function workflowPreparation(environment) {
  return prepareSynchronization({
    buildConclusion: environment.BUILD_CONCLUSION,
    buildEvent: environment.BUILD_EVENT,
    buildHeadBranch: environment.BUILD_HEAD_BRANCH,
    builtSha: environment.BUILT_SHA,
    currentExperimentalSha: environment.CURRENT_EXPERIMENTAL_SHA,
    currentMainSha: environment.CURRENT_MAIN_SHA,
    mergeResult: environment.MERGE_RESULT,
    proposalHeadSha: environment.PROPOSAL_HEAD_SHA,
    proposalSourceSha: environment.PROPOSAL_SOURCE_SHA,
    proposalTargetSha: environment.PROPOSAL_TARGET_SHA,
  });
}

function trustedCompletionForPullRequest(pullRequest) {
  if (!pullRequest || Array.isArray(pullRequest)) {
    return false;
  }
  const expected = synchronizationStateFromPullRequest(pullRequest.body);
  return (pullRequest.comments ?? []).some(comment => trustedCompletion(comment, pullRequest, expected));
}

function trustedCompletion(comment, pullRequest, expected) {
  if (comment?.author?.login !== AUTOMATION_LOGIN || pullRequest.state !== 'MERGED') {
    return false;
  }
  const completion = completionStateFromBody(comment.body);
  return (
    completion?.pullRequestNumber === pullRequest.number
    && completion.proposalHeadSha === expected.headSha
    && completion.mergeCommitSha === pullRequest.mergeCommit?.oid
  );
}

function completionStateFromBody(body) {
  const value = body ?? '';
  const start = value.indexOf(COMPLETION_START);
  const end = value.indexOf(COMPLETION_END);
  if (start < 0 || end < 0 || value.lastIndexOf(COMPLETION_START) !== start || value.lastIndexOf(COMPLETION_END) !== end) {
    return undefined;
  }
  const marked = value.slice(start + COMPLETION_START.length, end).trim();
  const match = /^```json\n([\s\S]+)\n```$/.exec(marked);
  let state;
  try {
    state = match ? JSON.parse(match[1]) : undefined;
  } catch (_) {
    return undefined;
  }
  if (
    !state
    || Array.isArray(state)
    || Object.keys(state).sort().join('\n') !== ['experimentalSha', 'mergeCommitSha', 'proposalHeadSha', 'pullRequestNumber'].join('\n')
    || !Number.isSafeInteger(state.pullRequestNumber)
    || state.pullRequestNumber <= 0
  ) {
    return undefined;
  }
  try {
    requireSha(state.experimentalSha, 'completed experimental SHA');
    requireSha(state.mergeCommitSha, 'completed merge commit SHA');
    requireSha(state.proposalHeadSha, 'completed proposal head SHA');
  } catch (_) {
    return undefined;
  }
  if (value !== completionBody(state)) {
    return undefined;
  }
  return Object.freeze(state);
}

function completionBody({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber }) {
  requireSha(experimentalSha, 'completed experimental SHA');
  requireSha(mergeCommitSha, 'completed merge commit SHA');
  requireSha(proposalHeadSha, 'completed proposal head SHA');
  if (!Number.isSafeInteger(pullRequestNumber) || pullRequestNumber <= 0) {
    throw new Error(`Invalid completed pull request number '${pullRequestNumber ?? ''}'.`);
  }
  return `Synchronization finalization completed by the trusted repository workflow.

${COMPLETION_START}
\`\`\`json
${JSON.stringify({ experimentalSha, mergeCommitSha, proposalHeadSha, pullRequestNumber }, null, 2)}
\`\`\`
${COMPLETION_END}
`;
}

function disposableBranchCleanup({ proposalHeadSha, remoteBranchSha }) {
  requireSha(proposalHeadSha, 'completed proposal head SHA');
  if (remoteBranchSha === undefined || remoteBranchSha === '') {
    return Object.freeze({ action: 'none' });
  }
  requireSha(remoteBranchSha, 'remote synchronization branch SHA');
  return Object.freeze({ action: remoteBranchSha === proposalHeadSha ? 'delete' : 'keep' });
}

function atomicDisposableBranchCleanup({ proposalHeadSha, remote = 'origin' }) {
  requireSha(proposalHeadSha, 'completed proposal head SHA');
  const branchReference = `refs/heads/${SYNC_BRANCH}`;
  const deletion = spawnSync('git', ['push', `--force-with-lease=${branchReference}:${proposalHeadSha}`, remote, `:${branchReference}`], {
    encoding: 'utf8',
  });
  if (deletion.error) {
    throw new Error(`Atomic synchronization branch deletion could not start: ${deletion.error.message}`);
  }
  if (deletion.status === 0) {
    return Object.freeze({ action: 'delete' });
  }
  const inspection = spawnSync('git', ['ls-remote', '--heads', remote, branchReference], { encoding: 'utf8' });
  if (inspection.error || inspection.status !== 0) {
    throw new Error(
      `Unable to inspect synchronization branch after rejected deletion: ${inspection.stderr?.trim() || inspection.error?.message}`,
    );
  }
  const remoteBranchSha = inspection.stdout.trim().split(/\s+/)[0] || '';
  const state = disposableBranchCleanup({ proposalHeadSha, remoteBranchSha });
  if (state.action === 'delete') {
    throw new Error('Atomic synchronization branch deletion was rejected without a competing update.');
  }
  return state;
}

function proposalParents(value) {
  return String(value ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
}

function readBoundedJson(path, label) {
  if (!path) {
    throw new Error(`${label} path is required.`);
  }
  let statistics;
  try {
    statistics = statSync(path);
  } catch (_) {
    throw new Error(`${label} file cannot be read.`);
  }
  if (!statistics.isFile()) {
    throw new Error(`${label} path must identify a file.`);
  }
  if (statistics.size > MAXIMUM_JSON_BYTES) {
    throw new Error(`${label} exceeds the maximum of ${MAXIMUM_JSON_BYTES} bytes.`);
  }
  let value;
  try {
    value = JSON.parse(readFileSync(path, 'utf8'));
  } catch (_) {
    throw new Error(`${label} is not valid JSON.`);
  }
  return value;
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
  } else if (process.argv[2] === 'completion-body' && process.argv.length === 3) {
    try {
      process.stdout.write(
        completionBody({
          experimentalSha: process.env.EXPERIMENTAL_SHA,
          mergeCommitSha: process.env.MERGE_COMMIT_SHA,
          proposalHeadSha: process.env.PROPOSAL_HEAD_SHA,
          pullRequestNumber: Number(process.env.PULL_REQUEST_NUMBER),
        }),
      );
    } catch (error) {
      console.error(error.message);
      process.exitCode = 1;
    }
  } else if (process.argv[2] === 'prepare-workflow' && process.argv.length === 3) {
    try {
      const preparation = workflowPreparation(process.env);
      writeWorkflowOutputs({
        action: preparation.action,
        branch: preparation.branch ?? '',
        head: preparation.headSha ?? '',
        reason: preparation.reason ?? '',
        source: preparation.sourceSha ?? '',
        target: preparation.targetSha ?? '',
      });
    } catch (error) {
      console.error(error.message);
      process.exitCode = 1;
    }
  } else if (process.argv[2] === 'issue-workflow' && process.argv.length === 3) {
    try {
      const issue = workflowConflictIssue(process.env);
      writeWorkflowOutputs({ action: issue.action, issue: issue.issueNumber ?? '' });
    } catch (error) {
      console.error(error.message);
      process.exitCode = 1;
    }
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
    console.error(
      'Usage: node scripts/main-to-experimental-sync.cjs dry-run|pr-body|completion-body|prepare-workflow|issue-workflow|review-workflow',
    );
    process.exitCode = 1;
  }
}

module.exports = {
  atomicDisposableBranchCleanup,
  completionBody,
  conflictIssueAction,
  disposableBranchCleanup,
  pullRequestBody,
  postMergeSynchronization,
  prepareSynchronization,
  readBoundedJson,
  reviewSynchronization,
  synchronizationStateFromPullRequest,
  trustedCompletionForPullRequest,
};
