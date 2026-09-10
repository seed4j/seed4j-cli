const assert = require('node:assert/strict');
const test = require('node:test');

const { validateDispatchRequest, validateManualRelease, validateRecoveryVersion } = require('../../scripts/release-request.cjs');

test('accepts release dispatch without a manually selected version', () => {
  const request = validateDispatchRequest({ operation: 'release', version: '' });

  assert.deepEqual(request, { operation: 'release' });
  assert.throws(() => validateDispatchRequest({ operation: 'release', version: '9.9.9' }), /calculates its version automatically/);
});

test('requires an exact stable version for recovery dispatch', () => {
  const request = validateDispatchRequest({ operation: 'recover', version: '0.1.0' });

  assert.deepEqual(request, { operation: 'recover', version: '0.1.0' });
  assert.throws(() => validateDispatchRequest({ operation: 'recover', version: '' }), /stable semantic version/);
  assert.throws(() => validateRecoveryVersion('v0.1.0'), /stable semantic version/);
  assert.throws(() => validateRecoveryVersion('0.1.0-rc.1'), /stable semantic version/);
  assert.throws(() => validateDispatchRequest({ operation: 'publish', version: '' }), /Unsupported release operation/);
});

test('accepts a manual release only for untagged current main with a green push build', () => {
  assert.doesNotThrow(() =>
    validateManualRelease({
      checkedOutSha: 'current-main',
      currentMainSha: 'current-main',
      releaseTags: [],
      successfulBuildCount: 1,
    }),
  );
});

test('rejects stale, unverified, or already released manual revisions', () => {
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'older-main',
        currentMainSha: 'current-main',
        releaseTags: [],
        successfulBuildCount: 1,
      }),
    /current main/,
  );
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'current-main',
        currentMainSha: 'current-main',
        releaseTags: [],
        successfulBuildCount: 0,
      }),
    /successful push build/,
  );
  assert.throws(
    () =>
      validateManualRelease({
        checkedOutSha: 'current-main',
        currentMainSha: 'current-main',
        releaseTags: ['preview', 'v0.1.0'],
        successfulBuildCount: 1,
      }),
    /operation=recover.*0\.1\.0/,
  );
});
