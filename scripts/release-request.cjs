const { appendFileSync } = require('node:fs');
const { spawnSync } = require('node:child_process');

const STABLE_VERSION = /^\d+\.\d+\.\d+$/;
const STABLE_TAG = /^v(\d+\.\d+\.\d+)$/;
const RELEASE_TAG = /^v\d+\.\d+\.\d+(?:-experimental\.\d+)?$/;

function validateDispatchRequest({ operation, version }) {
  if (operation === 'release') {
    if (version) {
      throw new Error('Manual release calculates its version automatically; leave version empty.');
    }

    return { operation };
  }
  if (operation === 'recover') {
    return { operation, version: validateRecoveryVersion(version) };
  }

  throw new Error(`Unsupported release operation '${operation ?? ''}'.`);
}

function validateRecoveryVersion(version) {
  if (!STABLE_VERSION.test(version ?? '')) {
    throw new Error(`Recovery requires a stable semantic version, got '${version ?? ''}'.`);
  }

  return version;
}

function validateManualRelease({ checkedOutSha, currentMainSha, releaseTags, successfulBuildCount }) {
  if (!checkedOutSha || checkedOutSha !== currentMainSha) {
    throw new Error(`Manual release requires current main ${currentMainSha}; checked out ${checkedOutSha}.`);
  }

  const existingRelease = releaseTags.map(tag => STABLE_TAG.exec(tag)).find(match => match !== null);
  if (existingRelease) {
    throw new Error(`Current main already has ${existingRelease[0]}; use operation=recover with version ${existingRelease[1]}.`);
  }

  if (Number(successfulBuildCount) !== 1) {
    throw new Error(`Manual release requires a successful push build for current main ${currentMainSha}.`);
  }
}

function validateExperimentalRelease({
  buildActor,
  buildConclusion,
  buildEvent,
  buildHeadBranch,
  builtSha,
  checkedOutSha,
  currentExperimentalSha,
  releaseTags,
}) {
  const trustedBuildEvent = buildEvent === 'push' || (buildEvent === 'workflow_dispatch' && buildActor === 'github-actions[bot]');
  if (!trustedBuildEvent || buildHeadBranch !== 'experimental') {
    throw new Error('Experimental release requires an experimental push build or trusted synchronization dispatch.');
  }
  if (buildConclusion !== 'success') {
    throw new Error('Experimental release requires a successful build.');
  }
  if (!checkedOutSha || checkedOutSha !== currentExperimentalSha || builtSha !== currentExperimentalSha) {
    throw new Error(
      `Experimental release requires current experimental HEAD ${currentExperimentalSha}; built ${builtSha} and checked out ${checkedOutSha}.`,
    );
  }

  const existingReleaseTag = releaseTags.find(tag => RELEASE_TAG.test(tag));
  if (existingReleaseTag) {
    throw new Error(`Current experimental HEAD already has ${existingReleaseTag}.`);
  }
}

function workflowRunReleaseRequest(environment) {
  if (environment.BUILD_CONCLUSION !== 'success') {
    return Object.freeze({ release: false, reason: 'build-not-successful' });
  }
  if (environment.BUILD_HEAD_BRANCH === 'main') {
    if (environment.BUILD_EVENT !== 'push') {
      return Object.freeze({ release: false, reason: 'stable-release-requires-push' });
    }
    if (
      !environment.BUILT_SHA
      || environment.BUILT_SHA !== environment.CHECKED_OUT_SHA
      || environment.BUILT_SHA !== environment.CURRENT_MAIN_SHA
    ) {
      return Object.freeze({ release: false, reason: 'stable-build-is-stale' });
    }
    return Object.freeze({ channel: 'stable', release: true });
  }
  if (environment.BUILD_HEAD_BRANCH !== 'experimental') {
    return Object.freeze({ release: false, reason: 'unsupported-build-branch' });
  }
  const trustedExperimentalEvent =
    environment.BUILD_EVENT === 'push'
    || (environment.BUILD_EVENT === 'workflow_dispatch' && environment.BUILD_ACTOR === 'github-actions[bot]');
  if (!trustedExperimentalEvent) {
    return Object.freeze({ release: false, reason: 'untrusted-experimental-build-event' });
  }
  validateExperimentalRelease({
    buildActor: environment.BUILD_ACTOR,
    buildConclusion: environment.BUILD_CONCLUSION,
    buildEvent: environment.BUILD_EVENT,
    buildHeadBranch: environment.BUILD_HEAD_BRANCH,
    builtSha: environment.BUILT_SHA,
    checkedOutSha: environment.CHECKED_OUT_SHA,
    currentExperimentalSha: environment.CURRENT_EXPERIMENTAL_SHA,
    releaseTags: (environment.RELEASE_TAGS ?? '').split(/\r?\n/).filter(Boolean),
  });
  return Object.freeze({ channel: 'experimental', release: true });
}

function admitWorkflowRun(environment) {
  const trustedRepository =
    environment.GITHUB_REPOSITORY === 'seed4j/seed4j-cli' && environment.BUILD_SOURCE_REPOSITORY === environment.GITHUB_REPOSITORY;
  const trustedPush = environment.BUILD_EVENT === 'push';
  const trustedSynchronization =
    environment.BUILD_EVENT === 'workflow_dispatch'
    && environment.BUILD_ACTOR === 'github-actions[bot]'
    && environment.BUILD_HEAD_BRANCH === 'experimental';
  const supportedBranch = environment.BUILD_HEAD_BRANCH === 'main' || environment.BUILD_HEAD_BRANCH === 'experimental';

  if (!trustedRepository || environment.BUILD_CONCLUSION !== 'success' || !supportedBranch || (!trustedPush && !trustedSynchronization)) {
    throw new Error('Release qualification requires a successful trusted release workflow run.');
  }
  if (environment.BUILD_HEAD_BRANCH === 'main' && !trustedPush) {
    throw new Error('Release qualification requires a successful trusted release workflow run.');
  }
  if (!/^[0-9a-f]{40}$/.test(environment.BUILT_SHA ?? '')) {
    throw new Error('Release qualification requires an immutable 40-character build SHA.');
  }

  return Object.freeze({
    branch: environment.BUILD_HEAD_BRANCH,
    channel: environment.BUILD_HEAD_BRANCH === 'main' ? 'stable' : 'experimental',
    sha: environment.BUILT_SHA,
  });
}

function qualifyWorkflowRun(environment) {
  const admission = admitWorkflowRun(environment);
  runGit(['fetch', 'origin', admission.branch, '--tags']);
  const currentSha = runGit(['rev-parse', `origin/${admission.branch}`]);
  const releaseTags = runGit(['tag', '--points-at', admission.sha, '--list', 'v*']);
  const request = workflowRunReleaseRequest({
    ...environment,
    CHECKED_OUT_SHA: admission.sha,
    CURRENT_EXPERIMENTAL_SHA: admission.branch === 'experimental' ? currentSha : '',
    CURRENT_MAIN_SHA: admission.branch === 'main' ? currentSha : '',
    RELEASE_TAGS: releaseTags,
  });
  if (!request.release || request.channel !== admission.channel) {
    throw new Error(`Release workflow run is not eligible: ${request.reason ?? 'channel mismatch'}.`);
  }

  return Object.freeze({ channel: admission.channel, eligible: true, sha: admission.sha });
}

function qualifyManualRelease(environment) {
  validateDispatchRequest({ operation: environment.RELEASE_OPERATION, version: environment.RELEASE_VERSION });
  runGit(['fetch', 'origin', 'main', '--tags']);
  const checkedOutSha = runGit(['rev-parse', 'HEAD']);
  const currentMainSha = runGit(['rev-parse', 'origin/main']);
  validateManualRelease({
    checkedOutSha,
    currentMainSha,
    releaseTags: runGit(['tag', '--points-at', checkedOutSha, '--list', 'v*']).split(/\r?\n/).filter(Boolean),
    successfulBuildCount: environment.SUCCESSFUL_BUILD_COUNT,
  });

  return Object.freeze({ channel: 'stable', eligible: true, sha: checkedOutSha });
}

function qualifyRecovery(environment) {
  const request = validateDispatchRequest({
    operation: environment.RELEASE_OPERATION,
    version: environment.RELEASE_VERSION,
  });
  runGit(['fetch', 'origin', 'main', '--tags']);
  const releaseTag = `v${request.version}`;
  const tagSha = runGit(['rev-list', '-n', '1', `refs/tags/${releaseTag}`]);
  runGit(['merge-base', '--is-ancestor', tagSha, 'origin/main']);

  return Object.freeze({ sha: tagSha });
}

function runGit(arguments_) {
  const result = spawnSync('git', arguments_, { encoding: 'utf8' });
  if (result.status !== 0) {
    throw new Error(`git ${arguments_.join(' ')} failed: ${(result.stderr ?? '').trim()}`);
  }

  return result.stdout.trim();
}

function writeWorkflowOutputs(values) {
  if (!process.env.GITHUB_OUTPUT) {
    throw new Error('GITHUB_OUTPUT is required for release workflow output.');
  }
  for (const [key, value] of Object.entries(values)) {
    appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
  }
}

function run() {
  const command = process.argv[2];

  if (command === 'dispatch') {
    validateDispatchRequest({
      operation: process.env.RELEASE_OPERATION,
      version: process.env.RELEASE_VERSION,
    });
    return;
  }
  if (command === 'manual') {
    validateManualRelease({
      checkedOutSha: process.env.CHECKED_OUT_SHA,
      currentMainSha: process.env.CURRENT_MAIN_SHA,
      releaseTags: (process.env.RELEASE_TAGS ?? '').split(/\r?\n/).filter(Boolean),
      successfulBuildCount: process.env.SUCCESSFUL_BUILD_COUNT,
    });
    return;
  }
  if (command === 'experimental') {
    validateExperimentalRelease({
      buildActor: process.env.BUILD_ACTOR,
      buildConclusion: process.env.BUILD_CONCLUSION,
      buildEvent: process.env.BUILD_EVENT,
      buildHeadBranch: process.env.BUILD_HEAD_BRANCH,
      builtSha: process.env.BUILT_SHA,
      checkedOutSha: process.env.CHECKED_OUT_SHA,
      currentExperimentalSha: process.env.CURRENT_EXPERIMENTAL_SHA,
      releaseTags: (process.env.RELEASE_TAGS ?? '').split(/\r?\n/).filter(Boolean),
    });
    return;
  }
  if (command === 'workflow-run') {
    const request = workflowRunReleaseRequest(process.env);
    writeWorkflowOutputs({
      channel: request.channel ?? '',
      reason: request.reason ?? '',
      release: request.release,
    });
    return;
  }
  if (command === 'qualify-workflow-run') {
    const qualification = qualifyWorkflowRun(process.env);
    writeWorkflowOutputs(qualification);
    return;
  }
  if (command === 'qualify-manual-release') {
    writeWorkflowOutputs(qualifyManualRelease(process.env));
    return;
  }
  if (command === 'qualify-recovery') {
    writeWorkflowOutputs(qualifyRecovery(process.env));
    return;
  }

  throw new Error(`Unsupported release request command '${command ?? ''}'.`);
}

if (require.main === module) {
  try {
    run();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = {
  validateDispatchRequest,
  validateExperimentalRelease,
  validateManualRelease,
  validateRecoveryVersion,
  admitWorkflowRun,
  qualifyManualRelease,
  qualifyRecovery,
  qualifyWorkflowRun,
  workflowRunReleaseRequest,
};
