const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdtempSync, readFileSync, writeFileSync } = require('node:fs');
const { chmod, mkdir, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const seed4jCommand = join(repositoryRoot, 'bin/seed4j.js');

test('runs the packaged JAR with forwarded arguments', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, javaLog, 0);

  const result = runSeed4J(testDirectory, ['apply', 'init', '--project-name', 'Seed4J Sample']);

  assert.equal(result.status, 0);
  assert.deepEqual(JSON.parse(readFileSync(javaLog, 'utf8')), [
    '-jar',
    join(repositoryRoot, 'dist/seed4j-cli.jar'),
    'apply',
    'init',
    '--project-name',
    'Seed4J Sample',
  ]);

  await rm(testDirectory, { force: true, recursive: true });
});

test('preserves the Java process exit code', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, javaLog, 42);

  const result = runSeed4J(testDirectory, ['--version']);

  assert.equal(result.status, 42);

  await rm(testDirectory, { force: true, recursive: true });
});

test('prints a clear error when Java is missing', () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));

  const result = runSeed4J(testDirectory, ['--help']);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /Java 25 is required to run seed4j-cli/);

  return rm(testDirectory, { force: true, recursive: true });
});

async function installFakeJava(testDirectory, javaLog, exitCode) {
  await mkdir(testDirectory, { recursive: true });
  const javaPath = join(testDirectory, 'java');
  writeFileSync(
    javaPath,
    `#!${process.execPath}
const { writeFileSync } = require("node:fs");
writeFileSync(${JSON.stringify(javaLog)}, JSON.stringify(process.argv.slice(2)));
process.exit(${exitCode});
`,
  );
  await chmod(javaPath, 0o755);
}

function runSeed4J(testDirectory, args) {
  return spawnSync(process.execPath, [seed4jCommand, ...args], {
    cwd: repositoryRoot,
    encoding: 'utf8',
    env: {
      PATH: testDirectory,
    },
  });
}
