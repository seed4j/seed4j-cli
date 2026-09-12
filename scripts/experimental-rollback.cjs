const EXPERIMENTAL_VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)-experimental\.(0|[1-9]\d*)$/;
const ISSUE_URL = /^https:\/\/github\.com\/seed4j\/seed4j-cli\/issues\/[1-9]\d*$/;
const STABLE_VERSION = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const FLAGS = Object.freeze({
  '--bad': 'badVersion',
  '--current-experimental': 'currentExperimentalVersion',
  '--current-latest': 'currentLatestVersion',
  '--good': 'goodVersion',
  '--issue-url': 'issueUrl',
  '--package': 'packageName',
});

function validateRollback(request) {
  if (request.packageName !== 'seed4j-cli') {
    throw new Error(`Rollback package must be seed4j-cli, got '${request.packageName ?? ''}'.`);
  }
  requireExperimentalVersion('good', request.goodVersion);
  requireExperimentalVersion('bad', request.badVersion);
  if (request.goodVersion === request.badVersion) {
    throw new Error('Good and bad experimental versions must differ.');
  }
  if (compareExperimentalVersions(request.goodVersion, request.badVersion) >= 0) {
    throw new Error(`Good version ${request.goodVersion} must precede bad version ${request.badVersion}.`);
  }
  if (!STABLE_VERSION.test(request.currentLatestVersion ?? '')) {
    throw new Error(`Current latest must be a stable semantic version, got '${request.currentLatestVersion ?? ''}'.`);
  }
  if (request.currentExperimentalVersion !== request.badVersion) {
    throw new Error(`Bad version ${request.badVersion} must equal current experimental ${request.currentExperimentalVersion ?? ''}.`);
  }
  if (!ISSUE_URL.test(request.issueUrl ?? '')) {
    throw new Error(`Issue URL must identify a seed4j/seed4j-cli GitHub issue, got '${request.issueUrl ?? ''}'.`);
  }

  return Object.freeze({ ...request });
}

function requireExperimentalVersion(label, version) {
  if (!EXPERIMENTAL_VERSION.test(version ?? '')) {
    throw new Error(`${label} version must be an experimental semantic version, got '${version ?? ''}'.`);
  }
}

function compareExperimentalVersions(left, right) {
  const leftParts = EXPERIMENTAL_VERSION.exec(left).slice(1).map(BigInt);
  const rightParts = EXPERIMENTAL_VERSION.exec(right).slice(1).map(BigInt);
  for (let index = 0; index < leftParts.length; index++) {
    if (leftParts[index] < rightParts[index]) {
      return -1;
    }
    if (leftParts[index] > rightParts[index]) {
      return 1;
    }
  }
  return 0;
}

function commands(request) {
  return [
    `npm dist-tag add seed4j-cli@${request.goodVersion} experimental`,
    `npm deprecate seed4j-cli@${request.badVersion} "Bad experimental build; see ${request.issueUrl}"`,
  ];
}

function parseArguments(arguments_) {
  const request = {};
  for (let index = 0; index < arguments_.length; index += 2) {
    const flag = arguments_[index];
    const value = arguments_[index + 1];
    const field = FLAGS[flag];
    if (!field || value === undefined || value.startsWith('--')) {
      throw new Error(`Invalid rollback argument '${flag ?? ''}'.`);
    }
    if (request[field] !== undefined) {
      throw new Error(`Duplicate rollback argument '${flag}'.`);
    }
    request[field] = value;
  }
  return request;
}

function run() {
  const request = validateRollback(parseArguments(process.argv.slice(2)));
  for (const command of commands(request)) {
    console.log(command);
  }
}

if (require.main === module) {
  try {
    run();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = { commands, parseArguments, validateRollback };
