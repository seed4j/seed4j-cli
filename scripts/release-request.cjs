const STABLE_VERSION = /^\d+\.\d+\.\d+$/;
const STABLE_TAG = /^v(\d+\.\d+\.\d+)$/;

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

module.exports = { validateDispatchRequest, validateManualRelease, validateRecoveryVersion };
