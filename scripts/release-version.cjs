const EXPERIMENTAL_VERSION = /^\d+\.\d+\.\d+-experimental\.\d+$/;
const STABLE_VERSION = /^\d+\.\d+\.\d+$/;

function validateReleaseVersion(version, channel) {
  if (channel === 'stable') {
    if (!STABLE_VERSION.test(version ?? '')) {
      throw new Error(`Expected a stable semantic version, got '${version ?? ''}'.`);
    }
    return version;
  }
  if (channel === 'experimental') {
    if (!EXPERIMENTAL_VERSION.test(version ?? '')) {
      throw new Error(`Expected an experimental semantic version, got '${version ?? ''}'.`);
    }
    return version;
  }

  throw new Error(`Unknown release channel '${channel ?? ''}'.`);
}

module.exports = { validateReleaseVersion };
