const { closeSync, mkdtempSync, openSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { spawnSync } = require('node:child_process');

const {
  atomicDisposableBranchCleanup,
  completionBody,
  postMergeSynchronization,
  readBoundedJson,
  synchronizationStateFromPullRequest,
  trustedCompletionForPullRequest,
} = require('./main-to-experimental-sync.cjs');

const SYNC_BRANCH = 'automation/sync-main-to-experimental';
const SOURCE_BRANCH = 'main';
const TARGET_BRANCH = 'experimental';

function recover(environment = process.env) {
  const repository = required(environment.GITHUB_REPOSITORY, 'GITHUB_REPOSITORY');
  const pendingLabel = required(environment.SYNC_PENDING_LABEL, 'SYNC_PENDING_LABEL');
  const directory = mkdtempSync(join(environment.RUNNER_TEMP || tmpdir(), 'seed4j-sync-recovery-'));
  const requestedBuildShas = new Set();
  let refreshRequired = false;
  let failed = false;
  try {
    const pullRequests = loadCandidates({ directory, environment, pendingLabel, repository });
    for (const pullRequest of pullRequests) {
      try {
        const outcome = recoverCandidate({
          directory,
          pendingLabel,
          pullRequest,
          repository,
          requestedBuildShas,
        });
        refreshRequired ||= outcome === 'refresh';
      } catch (error) {
        failed = true;
        console.error(`Pull request #${pullRequest?.number ?? '?'} finalization failed: ${error.message}`);
      }
    }
    if (refreshRequired) {
      try {
        run('gh', ['workflow', 'run', 'synchronize-experimental.yml', '--repo', repository, '--ref', SOURCE_BRANCH]);
      } catch (error) {
        failed = true;
        console.error(`Synchronization refresh dispatch failed: ${error.message}`);
      }
    }
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
  return failed ? 1 : 0;
}

function loadCandidates({ directory, environment, pendingLabel, repository }) {
  const requestedNumber = requestedPullRequestNumber(environment.REQUESTED_PR_NUMBER);
  let pullRequests;
  if (requestedNumber === undefined) {
    pullRequests = runJson(
      'gh',
      [
        'pr',
        'list',
        '--repo',
        repository,
        '--state',
        'all',
        '--base',
        TARGET_BRANCH,
        '--head',
        SYNC_BRANCH,
        '--label',
        pendingLabel,
        '--search',
        'sort:created-asc',
        '--limit',
        '100',
        '--json',
        'number,body,baseRefName,headRefName,headRefOid,state,mergeCommit,comments,labels',
      ],
      directory,
      'pending synchronization pull requests',
    );
  } else {
    pullRequests = [
      runJson(
        'gh',
        [
          'pr',
          'view',
          String(requestedNumber),
          '--repo',
          repository,
          '--json',
          'number,body,baseRefName,headRefName,headRefOid,state,mergeCommit,comments,labels',
        ],
        directory,
        `synchronization pull request #${requestedNumber}`,
      ),
    ];
  }
  if (!Array.isArray(pullRequests) || pullRequests.length > 100) {
    throw new Error('Pending synchronization pull requests must contain at most 100 entries.');
  }
  const numbers = new Set();
  for (const pullRequest of pullRequests) {
    if (Number.isSafeInteger(pullRequest?.number) && numbers.has(pullRequest.number)) {
      throw new Error(`Pending synchronization pull requests contain duplicate number '${pullRequest.number}'.`);
    }
    if (Number.isSafeInteger(pullRequest?.number)) {
      numbers.add(pullRequest.number);
    }
  }
  return pullRequests.sort(
    (left, right) =>
      finalizationPriority(left) - finalizationPriority(right) || pullRequestNumberForOrdering(left) - pullRequestNumberForOrdering(right),
  );
}

function requestedPullRequestNumber(value) {
  if (value === undefined || value === '') {
    return undefined;
  }
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0 || String(number) !== value) {
    throw new Error(`Invalid requested pull request number '${value}'.`);
  }
  return number;
}

function validatePullRequest(pullRequest) {
  if (
    !pullRequest
    || Array.isArray(pullRequest)
    || pullRequest.baseRefName !== TARGET_BRANCH
    || pullRequest.headRefName !== SYNC_BRANCH
    || !Number.isSafeInteger(pullRequest.number)
    || pullRequest.number <= 0
    || !['MERGED', 'OPEN'].includes(pullRequest.state)
  ) {
    throw new Error('Pending synchronization pull request has invalid branches, state, or number.');
  }
  synchronizationStateFromPullRequest(pullRequest.body);
}

function finalizationPriority(pullRequest) {
  return pullRequest?.state === 'MERGED' ? 0 : 1;
}

function pullRequestNumberForOrdering(pullRequest) {
  return Number.isSafeInteger(pullRequest?.number) ? pullRequest.number : Number.MAX_SAFE_INTEGER;
}

function recoverCandidate({ directory, pendingLabel, pullRequest, repository, requestedBuildShas }) {
  validatePullRequest(pullRequest);
  const pending = (pullRequest.labels ?? []).some(label => label?.name === pendingLabel);
  if (trustedCompletionForPullRequest(pullRequest)) {
    if (pending) {
      removePendingLabel(pullRequest.number, pendingLabel, repository);
    }
    return 'complete';
  }
  if (!pending) {
    throw new Error(`required label '${pendingLabel}' is absent and no trusted completion exists`);
  }

  run('git', ['fetch', 'origin', SOURCE_BRANCH, TARGET_BRANCH]);
  run('git', ['fetch', 'origin', `pull/${pullRequest.number}/head`]);
  const observedProposalHeadSha = gitOutput(['rev-parse', 'FETCH_HEAD']);
  const currentMainSha = gitOutput(['rev-parse', `origin/${SOURCE_BRANCH}`]);
  const currentExperimentalSha = gitOutput(['rev-parse', `origin/${TARGET_BRANCH}`]);
  const proposalParentShas = gitOutput(['show', '-s', '--format=%P', observedProposalHeadSha]).split(/\s+/).filter(Boolean);
  const mergeCommitSha = pullRequest.mergeCommit?.oid;
  const mergeReachable = mergeCommitSha
    ? run('git', ['merge-base', '--is-ancestor', mergeCommitSha, currentExperimentalSha], [0, 1]).status === 0
    : false;
  const expected = synchronizationStateFromPullRequest(pullRequest.body);
  const decision = postMergeSynchronization({
    branch: pullRequest.headRefName,
    currentExperimentalSha,
    currentMainSha,
    expectedSourceSha: expected.sourceSha,
    expectedTargetSha: expected.targetSha,
    mergeCommitSha,
    mergeReachable,
    observedProposalHeadSha,
    proposalHeadSha: pullRequest.headRefOid,
    proposalParentShas,
    proposalState: pullRequest.state,
    recordedHeadSha: expected.headSha,
  });
  if (decision.action === 'refresh') {
    return 'refresh';
  }
  if (decision.action === 'schedule-recheck' || decision.action === 'blocked') {
    return decision.action;
  }
  if (decision.action !== 'complete') {
    throw new Error(`unexpected finalization action '${decision.action}'`);
  }

  ensureExactBuild({ directory, experimentalSha: decision.experimentalSha, repository, requestedBuildShas });
  atomicDisposableBranchCleanup({ proposalHeadSha: expected.headSha });
  const completionPath = join(directory, `completion-${pullRequest.number}.md`);
  writeFileSync(
    completionPath,
    completionBody({
      experimentalSha: decision.experimentalSha,
      mergeCommitSha,
      proposalHeadSha: expected.headSha,
      pullRequestNumber: pullRequest.number,
    }),
  );
  run('gh', ['pr', 'comment', String(pullRequest.number), '--repo', repository, '--body-file', completionPath]);
  removePendingLabel(pullRequest.number, pendingLabel, repository);
  return 'complete';
}

function ensureExactBuild({ directory, experimentalSha, repository, requestedBuildShas }) {
  if (requestedBuildShas.has(experimentalSha) || reusableBuildRuns(directory, experimentalSha, repository)) {
    return;
  }
  run('gh', ['workflow', 'run', 'github-actions.yml', '--repo', repository, '--ref', TARGET_BRANCH]);
  run('git', ['fetch', 'origin', TARGET_BRANCH]);
  if (gitOutput(['rev-parse', `origin/${TARGET_BRANCH}`]) !== experimentalSha) {
    throw new Error('experimental moved while dispatching its exact standard build');
  }
  requestedBuildShas.add(experimentalSha);
}

function reusableBuildRuns(directory, experimentalSha, repository) {
  const commonArguments = [
    'run',
    'list',
    '--repo',
    repository,
    '--workflow',
    'github-actions.yml',
    '--branch',
    TARGET_BRANCH,
    '--commit',
    experimentalSha,
    '--limit',
    '20',
    '--json',
    'databaseId,status,conclusion,event,headSha,headBranch',
  ];
  const pushRuns = runJson('gh', [...commonArguments, '--event', 'push'], directory, 'experimental push builds');
  const dispatchRuns = runJson(
    'gh',
    [...commonArguments, '--event', 'workflow_dispatch', '--user', 'github-actions[bot]'],
    directory,
    'trusted experimental workflow-dispatch builds',
  );
  return [...validatedRunList(pushRuns), ...validatedRunList(dispatchRuns)].some(
    runRecord =>
      runRecord.headSha === experimentalSha
      && runRecord.headBranch === TARGET_BRANCH
      && ['push', 'workflow_dispatch'].includes(runRecord.event)
      && (['queued', 'in_progress'].includes(runRecord.status) || (runRecord.status === 'completed' && runRecord.conclusion === 'success')),
  );
}

function validatedRunList(value) {
  if (!Array.isArray(value) || value.length > 20) {
    throw new Error('Standard build response must contain at most 20 entries.');
  }
  return value;
}

function removePendingLabel(number, pendingLabel, repository) {
  run('gh', ['pr', 'edit', String(number), '--repo', repository, '--remove-label', pendingLabel]);
}

function runJson(command, arguments_, directory, label) {
  const responseDirectory = mkdtempSync(join(directory, 'response-'));
  const path = join(responseDirectory, 'body.json');
  const descriptor = openSync(path, 'w');
  let result;
  try {
    result = spawnSync(command, arguments_, {
      encoding: 'utf8',
      maxBuffer: 64 * 1024,
      stdio: ['ignore', descriptor, 'pipe'],
    });
  } finally {
    closeSync(descriptor);
  }
  commandSucceeded(command, arguments_, result, [0]);
  return readBoundedJson(path, label);
}

function gitOutput(arguments_) {
  return run('git', arguments_).stdout.trim();
}

function run(command, arguments_, acceptedStatuses = [0]) {
  const result = spawnSync(command, arguments_, { encoding: 'utf8', maxBuffer: 64 * 1024 });
  commandSucceeded(command, arguments_, result, acceptedStatuses);
  return result;
}

function commandSucceeded(command, arguments_, result, acceptedStatuses) {
  if (result.error) {
    throw new Error(`${command} ${arguments_.join(' ')} could not start: ${result.error.message}`);
  }
  if (!acceptedStatuses.includes(result.status)) {
    const detail = result.stderr?.trim() || `exit ${result.status}`;
    throw new Error(`${command} ${arguments_.join(' ')} failed: ${detail}`);
  }
}

function required(value, name) {
  if (!value) {
    throw new Error(`${name} is required.`);
  }
  return value;
}

if (require.main === module) {
  try {
    process.exitCode = recover();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = { recover };
