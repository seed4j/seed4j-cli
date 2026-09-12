const { appendFileSync } = require('node:fs');

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
  workflowRunReleaseRequest,
};
