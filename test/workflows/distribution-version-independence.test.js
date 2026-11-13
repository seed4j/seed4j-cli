const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { dirname, join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const suite = 'test/workflows/distribution-identity-policy.test.js';
const files = [
  suite,
  'scripts/distribution-identity-policy.cjs',
  'pom.xml',
  'src/main/resources/META-INF/seed4j-cli-distribution.properties',
  'renovate.json',
  '.github/workflows/github-actions.yml',
  'scripts/prepare-release.js',
];
const pom = readFileSync(join(repositoryRoot, 'pom.xml'), 'utf8');
const version = /<seed4j\.version>(\d+)\.(\d+)\.(\d+)<\/seed4j\.version>/.exec(pom);
assert.ok(version, 'the POM must declare a stable seed4j.version');
const [major, minor, patch] = version.slice(1).map(Number);

for (const [change, nextVersion] of [
  ['patch', `${major}.${minor}.${patch + 1}`],
  ['minor', `${major}.${minor + 1}.0`],
  ['major', `${major + 1}.0.0`],
]) {
  test(`the complete identity suite accepts a ${change} update to ${nextVersion}`, context => {
    const originals = files.map(file => [file, readFileSync(join(repositoryRoot, file))]);
    const directory = mkdtempSync(join(tmpdir(), 'seed4j-distribution-version-'));
    context.after(() => {
      rmSync(directory, { recursive: true, force: true });
      assert.equal(existsSync(directory), false, 'the temporary tree must be removed even on failure');
      for (const [file, content] of originals) {
        assert.deepEqual(readFileSync(join(repositoryRoot, file)), content, `${file} must remain unchanged`);
      }
      context.diagnostic('temporary tree removed; all original files unchanged');
    });
    for (const file of files) {
      const destination = join(directory, file);
      mkdirSync(dirname(destination), { recursive: true });
      copyFileSync(join(repositoryRoot, file), destination);
    }
    writeFileSync(join(directory, 'pom.xml'), pom.replace(version[0], `<seed4j.version>${nextVersion}</seed4j.version>`));
    const environment = { ...process.env };
    delete environment.NODE_TEST_CONTEXT;

    const result = spawnSync(process.execPath, ['--test', '--test-reporter=tap', suite], {
      cwd: directory,
      encoding: 'utf8',
      env: environment,
      timeout: 30000,
    });
    const output = result.stdout + result.stderr;
    const executedTests = /^# tests ([1-9]\d*)$/m.exec(result.stdout)?.[1];

    assert.ifError(result.error);
    assert.equal(result.signal, null, output);
    assert.equal(result.status, 0, output);
    assert.ok(executedTests, output);
    assert.match(result.stdout, new RegExp(`^# pass ${executedTests}$`, 'm'));
    assert.match(result.stdout, /^# fail 0$/m);
    assert.match(result.stdout, /^# cancelled 0$/m);
    assert.match(result.stdout, /^# skipped 0$/m);
    assert.match(result.stdout, /^# todo 0$/m);
    context.diagnostic(`${nextVersion}: ${executedTests} tests passed; zero failures, cancellations, skips or todos`);
  });
}
