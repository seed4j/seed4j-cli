const { mkdtempSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { spawn, spawnSync } = require('node:child_process');

const {
  atomicDisposableBranchCleanup,
  completionBody,
  postMergeSynchronization,
  synchronizationStateFromPullRequest,
  trustedCompletionForPullRequest,
} = require('./main-to-experimental-sync.cjs');

const SYNC_BRANCH = 'automation/sync-main-to-experimental';
const SOURCE_BRANCH = 'main';
const TARGET_BRANCH = 'experimental';

const MAX_JSON_BYTES = 2 * 1024 * 1024;

async function recover(environment = process.env) {
  const repository = required(environment.GITHUB_REPOSITORY, 'GITHUB_REPOSITORY');
  const pendingLabel = required(environment.SYNC_PENDING_LABEL, 'SYNC_PENDING_LABEL');
  const directory = mkdtempSync(join(environment.RUNNER_TEMP || tmpdir(), 'seed4j-sync-recovery-'));
  const requestedBuildShas = new Set();
  const requestedProposalBuildShas = new Set();
  let refreshRequired = false;
  let failed = false;
  try {
    const candidates = await loadCandidateIndexes({ environment, pendingLabel, repository });
    for (const candidate of candidates) {
      try {
        const pullRequest = await loadPullRequest(candidate.number, repository);
        const outcome = await recoverCandidate({
          allowPendingLabelRepair: candidate.allowPendingLabelRepair,
          directory,
          expectedNumber: candidate.number,
          pendingLabel,
          pullRequest,
          repository,
          requestedBuildShas,
          requestedProposalBuildShas,
        });
        refreshRequired ||= outcome === 'refresh';
      } catch (error) {
        failed = true;
        console.error(`Pull request #${candidate?.number ?? '?'} finalization failed: ${error.message}`);
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

async function loadCandidateIndexes({ environment, pendingLabel, repository }) {
  const requestedNumber = requestedPullRequestNumber(environment.REQUESTED_PR_NUMBER);
  let candidates;
  if (requestedNumber === undefined) {
    const pendingCandidates = await runJson(
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
        'number,state',
      ],
      'pending synchronization pull request index',
    );
    validateCandidateIndex(pendingCandidates, 100, ['MERGED', 'OPEN'], 'Pending synchronization pull requests');
    const repairCandidates = await runJson(
      'gh',
      [
        'pr',
        'list',
        '--repo',
        repository,
        '--state',
        'open',
        '--base',
        TARGET_BRANCH,
        '--head',
        SYNC_BRANCH,
        '--limit',
        '2',
        '--json',
        'number,state',
      ],
      'exact open synchronization pull request index',
    );
    validateCandidateIndex(repairCandidates, 1, ['OPEN'], 'Exact open synchronization pull requests');
    const pendingNumbers = new Set(pendingCandidates.map(candidate => candidate.number));
    candidates = pendingCandidates.map(candidate => ({ ...candidate, allowPendingLabelRepair: false }));
    if (repairCandidates.length === 1 && !pendingNumbers.has(repairCandidates[0].number)) {
      candidates.push({ ...repairCandidates[0], allowPendingLabelRepair: true });
    }
  } else {
    candidates = [{ allowPendingLabelRepair: false, number: requestedNumber }];
  }
  validateCandidateNumbers(candidates, requestedNumber === undefined);
  return candidates.sort(
    (left, right) =>
      finalizationPriority(left) - finalizationPriority(right) || pullRequestNumberForOrdering(left) - pullRequestNumberForOrdering(right),
  );
}

function validateCandidateIndex(candidates, maximum, states, label) {
  if (!Array.isArray(candidates) || candidates.length > maximum) {
    throw new Error(`${label} must contain at most ${maximum} ${maximum === 1 ? 'entry' : 'entries'}.`);
  }
  for (const candidate of candidates) {
    if (!Number.isSafeInteger(candidate?.number) || candidate.number <= 0 || !states.includes(candidate.state)) {
      throw new Error(`${label} index has an invalid number or state.`);
    }
  }
}

function validateCandidateNumbers(candidates, stateRequired) {
  const numbers = new Set();
  for (const candidate of candidates) {
    if (
      !Number.isSafeInteger(candidate?.number)
      || candidate.number <= 0
      || (stateRequired && !['MERGED', 'OPEN'].includes(candidate.state))
    ) {
      throw new Error('Pending synchronization pull request index has an invalid number or state.');
    }
    if (numbers.has(candidate.number)) {
      throw new Error(`Pending synchronization pull requests contain duplicate number '${candidate.number}'.`);
    }
    numbers.add(candidate.number);
  }
}

function loadPullRequest(number, repository) {
  return runJson(
    'gh',
    [
      'pr',
      'view',
      String(number),
      '--repo',
      repository,
      '--json',
      'number,body,baseRefName,headRefName,headRefOid,state,mergeCommit,comments,labels',
    ],
    `synchronization pull request #${number}`,
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

function validatePullRequest(pullRequest, expectedNumber) {
  if (
    !pullRequest
    || Array.isArray(pullRequest)
    || pullRequest.baseRefName !== TARGET_BRANCH
    || pullRequest.headRefName !== SYNC_BRANCH
    || !Number.isSafeInteger(pullRequest.number)
    || pullRequest.number <= 0
    || pullRequest.number !== expectedNumber
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

async function recoverCandidate({
  allowPendingLabelRepair,
  directory,
  expectedNumber,
  pendingLabel,
  pullRequest,
  repository,
  requestedBuildShas,
  requestedProposalBuildShas,
}) {
  validatePullRequest(pullRequest, expectedNumber);
  const pending = (pullRequest.labels ?? []).some(label => label?.name === pendingLabel);
  if (trustedCompletionForPullRequest(pullRequest)) {
    if (pending) {
      removePendingLabel(pullRequest.number, pendingLabel, repository);
    }
    return 'complete';
  }
  if (!pending && (!allowPendingLabelRepair || pullRequest.state !== 'OPEN')) {
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
  if (decision.action === 'schedule-recheck') {
    await ensureProposalBuild({
      labelRepair: pending ? undefined : { number: pullRequest.number, pendingLabel },
      proposalHeadSha: expected.headSha,
      repository,
      requestedProposalBuildShas,
    });
    return decision.action;
  }
  if (decision.action === 'blocked') {
    return decision.action;
  }
  if (decision.action !== 'complete') {
    throw new Error(`unexpected finalization action '${decision.action}'`);
  }

  await ensureExactBuild({ experimentalSha: decision.experimentalSha, repository, requestedBuildShas });
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

async function ensureProposalBuild({ labelRepair, proposalHeadSha, repository, requestedProposalBuildShas }) {
  run('git', ['fetch', 'origin', SYNC_BRANCH]);
  if (gitOutput(['rev-parse', `origin/${SYNC_BRANCH}`]) !== proposalHeadSha) {
    throw new Error('published synchronization branch no longer matches the recorded proposal head');
  }
  if (labelRepair) {
    addPendingLabel(labelRepair.number, labelRepair.pendingLabel, repository);
  }
  if (requestedProposalBuildShas.has(proposalHeadSha) || (await reusableProposalBuild(proposalHeadSha, repository))) {
    return;
  }
  try {
    run('gh', ['workflow', 'run', 'github-actions.yml', '--repo', repository, '--ref', SYNC_BRANCH]);
  } catch (error) {
    throw new Error(`proposal-head build dispatch failed: ${error.message}`);
  }
  run('git', ['fetch', 'origin', SYNC_BRANCH]);
  if (gitOutput(['rev-parse', `origin/${SYNC_BRANCH}`]) !== proposalHeadSha) {
    throw new Error('synchronization branch moved while dispatching its proposal-head build');
  }
  requestedProposalBuildShas.add(proposalHeadSha);
}

async function reusableProposalBuild(proposalHeadSha, repository) {
  const runs = await runJson(
    'gh',
    [
      'run',
      'list',
      '--repo',
      repository,
      '--workflow',
      'github-actions.yml',
      '--branch',
      SYNC_BRANCH,
      '--commit',
      proposalHeadSha,
      '--event',
      'workflow_dispatch',
      '--user',
      'github-actions[bot]',
      '--limit',
      '20',
      '--json',
      'databaseId,status,conclusion,event,headSha,headBranch',
    ],
    'trusted proposal-head workflow-dispatch builds',
  );
  return validatedRunList(runs).some(
    runRecord =>
      runRecord.headSha === proposalHeadSha
      && runRecord.headBranch === SYNC_BRANCH
      && runRecord.event === 'workflow_dispatch'
      && ['queued', 'in_progress', 'completed'].includes(runRecord.status),
  );
}

async function ensureExactBuild({ experimentalSha, repository, requestedBuildShas }) {
  if (requestedBuildShas.has(experimentalSha) || (await reusableBuildRuns(experimentalSha, repository))) {
    return;
  }
  run('gh', ['workflow', 'run', 'github-actions.yml', '--repo', repository, '--ref', TARGET_BRANCH]);
  run('git', ['fetch', 'origin', TARGET_BRANCH]);
  if (gitOutput(['rev-parse', `origin/${TARGET_BRANCH}`]) !== experimentalSha) {
    throw new Error('experimental moved while dispatching its exact standard build');
  }
  requestedBuildShas.add(experimentalSha);
}

async function reusableBuildRuns(experimentalSha, repository) {
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
  const pushRuns = await runJson('gh', [...commonArguments, '--event', 'push'], 'experimental push builds');
  const dispatchRuns = await runJson(
    'gh',
    [...commonArguments, '--event', 'workflow_dispatch', '--user', 'github-actions[bot]'],
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

function addPendingLabel(number, pendingLabel, repository) {
  run('gh', ['pr', 'edit', String(number), '--repo', repository, '--add-label', pendingLabel]);
}

function runJson(command, arguments_, label) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, arguments_, { stdio: ['ignore', 'pipe', 'pipe'] });
    const chunks = [];
    const errorChunks = [];
    let bytes = 0;
    let exceeded = false;
    let startError;
    child.stdout.on('data', chunk => {
      bytes += chunk.length;
      if (bytes > MAX_JSON_BYTES) {
        exceeded = true;
        child.kill('SIGKILL');
        return;
      }
      chunks.push(chunk);
    });
    child.stderr.on('data', chunk => {
      if (errorChunks.reduce((total, item) => total + item.length, 0) < 64 * 1024) {
        errorChunks.push(chunk);
      }
    });
    child.on('error', error => {
      startError = error;
    });
    child.on('close', status => {
      if (exceeded) {
        reject(new Error(`${label} exceeds the maximum of ${MAX_JSON_BYTES} bytes.`));
        return;
      }
      if (startError) {
        reject(new Error(`${command} ${arguments_.join(' ')} could not start: ${startError.message}`));
        return;
      }
      if (status !== 0) {
        const detail = Buffer.concat(errorChunks).toString('utf8').trim() || `exit ${status}`;
        reject(new Error(`${command} ${arguments_.join(' ')} failed: ${detail}`));
        return;
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch (error) {
        reject(new Error(`${label} is not valid JSON: ${error.message}`));
      }
    });
  });
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
  recover()
    .then(status => {
      process.exitCode = status;
    })
    .catch(error => {
      console.error(error.message);
      process.exitCode = 1;
    });
}

module.exports = { recover };
