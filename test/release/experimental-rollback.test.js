const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const rollbackScript = resolve(repositoryRoot, 'scripts/experimental-rollback.cjs');

test('prints only the two approved maintainer commands for a validated experimental rollback', () => {
  const result = runRollback(
    '--package',
    'seed4j-cli',
    '--good',
    '1.2.3-experimental.3',
    '--bad',
    '1.2.3-experimental.4',
    '--current-latest',
    '1.2.2',
    '--current-experimental',
    '1.2.3-experimental.4',
    '--issue-url',
    'https://github.com/seed4j/seed4j-cli/issues/123',
  );

  assert.equal(result.status, 0);
  assert.equal(result.stderr, '');
  assert.equal(
    result.stdout,
    'npm dist-tag add seed4j-cli@1.2.3-experimental.3 experimental\n'
      + 'npm deprecate seed4j-cli@1.2.3-experimental.4 "Bad experimental build; see https://github.com/seed4j/seed4j-cli/issues/123"\n',
  );
});

test('rejects an incomplete or unsafe rollback request without printing commands', () => {
  const validArguments = [
    '--package',
    'seed4j-cli',
    '--good',
    '1.2.3-experimental.3',
    '--bad',
    '1.2.3-experimental.4',
    '--current-latest',
    '1.2.2',
    '--current-experimental',
    '1.2.3-experimental.4',
    '--issue-url',
    'https://github.com/seed4j/seed4j-cli/issues/123',
  ];
  const invalidRequests = [
    replaceValue(validArguments, '--package', '@scope/seed4j-cli'),
    replaceValue(validArguments, '--good', '1.2.3'),
    replaceValue(validArguments, '--good', '1.2.3-experimental.5'),
    replaceValue(validArguments, '--bad', '1.2.3-next.4'),
    replaceValue(validArguments, '--current-latest', '1.2.3-experimental.2'),
    replaceValue(validArguments, '--current-experimental', '1.2.3-experimental.5'),
    replaceValue(validArguments, '--issue-url', 'https://example.com/issues/123'),
    validArguments.slice(0, -2),
  ];

  for (const arguments_ of invalidRequests) {
    const result = runRollback(...arguments_);

    assert.equal(result.status, 1, result.stderr);
    assert.equal(result.stdout, '');
  }
});

function runRollback(...arguments_) {
  return spawnSync(process.execPath, [rollbackScript, ...arguments_], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  });
}

function replaceValue(arguments_, flag, value) {
  const changed = [...arguments_];
  changed[changed.indexOf(flag) + 1] = value;
  return changed;
}
