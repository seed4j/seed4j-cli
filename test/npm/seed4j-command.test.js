const assert = require('node:assert/strict');
const { spawn, spawnSync } = require('node:child_process');
const { closeSync, existsSync, mkdtempSync, openSync, readFileSync, writeFileSync } = require('node:fs');
const { chmod, mkdir, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const seed4jCommand = join(repositoryRoot, 'bin/seed4j.js');

test('runs the packaged JAR with forwarded arguments', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, { javaLog });

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
  await installFakeJava(testDirectory, { javaLog, jarExitCode: 42 });

  const result = runSeed4J(testDirectory, ['--version']);

  assert.equal(result.status, 42);

  await rm(testDirectory, { force: true, recursive: true });
});

test('preserves stdin, stdout, and stderr for the Java process', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, {
    echoStdio: true,
    javaLog,
    stderr: 'Java stderr\n',
  });
  const stdinPath = join(testDirectory, 'stdin.txt');
  writeFileSync(stdinPath, 'Seed4J stdin\n');
  const stdin = openSync(stdinPath, 'r');

  const result = runSeed4J(testDirectory, [], stdin);

  assert.equal(result.status, 0);
  assert.equal(result.stdout, 'Seed4J stdin\n');
  assert.equal(result.stderr, 'Java stderr\n');

  closeSync(stdin);
  await rm(testDirectory, { force: true, recursive: true });
});

test('preserves the signal that terminates the Java process', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  const javaPid = join(testDirectory, 'java.pid');
  await installFakeJava(testDirectory, { javaLog, javaPid });
  const seed4j = spawnSeed4J(testDirectory);
  await waitForFile(javaPid);

  process.kill(Number(readFileSync(javaPid, 'utf8')), 'SIGTERM');
  const result = await seed4j.result;

  assert.equal(result.status, null);
  assert.equal(result.signal, 'SIGTERM');

  await rm(testDirectory, { force: true, recursive: true });
});

test('prints a clear error when Java is missing', () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));

  const result = runSeed4J(testDirectory, ['--help']);

  assert.equal(result.status, 1);
  assert.equal(
    result.stderr,
    'Java 25 or higher is required to run seed4j-cli. Install Java 25 or higher and make sure `java` is available on PATH.\n',
  );

  return rm(testDirectory, { force: true, recursive: true });
});

test('rejects Java versions below 25 before starting the JAR', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, {
    javaLog,
    versionOutput: 'openjdk 21.0.8 2025-07-15 LTS\n',
  });

  const result = runSeed4J(testDirectory, ['--help']);

  assert.equal(result.status, 1);
  assert.equal(
    result.stderr,
    'seed4j-cli requires Java 25 or higher, but Java 21 was found. Install a compatible Java version and make sure it is the first `java` on PATH.\n',
  );
  assert.equal(existsSync(javaLog), false);

  await rm(testDirectory, { force: true, recursive: true });
});

test('fails clearly when the Java version output is not recognized', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, {
    javaLog,
    versionOutput: 'unexpected Java version output\n',
  });

  const result = runSeed4J(testDirectory, ['--help']);

  assert.equal(result.status, 1);
  assert.equal(
    result.stderr,
    'Unable to determine the Java version from `java --version`. Install Java 25 or higher and make sure it is the first `java` on PATH.\n',
  );
  assert.equal(existsSync(javaLog), false);

  await rm(testDirectory, { force: true, recursive: true });
});

test('fails clearly when the Java version command is unsuccessful', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, {
    javaLog,
    versionExitCode: 2,
    versionOutput: 'openjdk 25.0.2 2026-01-20 LTS\n',
  });

  const result = runSeed4J(testDirectory, ['--help']);

  assert.equal(result.status, 1);
  assert.equal(
    result.stderr,
    'Unable to determine the Java version from `java --version`. Install Java 25 or higher and make sure it is the first `java` on PATH.\n',
  );
  assert.equal(existsSync(javaLog), false);

  await rm(testDirectory, { force: true, recursive: true });
});

test('runs the packaged JAR with Java 26 reported on stderr', async () => {
  const testDirectory = mkdtempSync(join(tmpdir(), 'seed4j-cli-npm-'));
  const javaLog = join(testDirectory, 'java-arguments.json');
  await installFakeJava(testDirectory, {
    javaLog,
    versionOutput: 'java 26 2026-03-17\n',
    versionStream: 'stderr',
  });

  const result = runSeed4J(testDirectory, ['--version']);

  assert.equal(result.status, 0);
  assert.deepEqual(JSON.parse(readFileSync(javaLog, 'utf8')), ['-jar', join(repositoryRoot, 'dist/seed4j-cli.jar'), '--version']);

  await rm(testDirectory, { force: true, recursive: true });
});

async function installFakeJava(testDirectory, options) {
  await mkdir(testDirectory, { recursive: true });
  const javaPath = join(testDirectory, 'java');
  writeFileSync(
    javaPath,
    `#!${process.execPath}
const { readFileSync, writeFileSync, writeSync } = require("node:fs");
const options = ${JSON.stringify(options)};
const arguments = process.argv.slice(2);

if (arguments[0] === "--version") {
  const versionStream = options.versionStream === "stderr" ? process.stderr : process.stdout;
  writeSync(versionStream.fd, options.versionOutput ?? "openjdk 25.0.2 2026-01-20 LTS\\n");
  process.exit(options.versionExitCode ?? 0);
}

writeFileSync(options.javaLog, JSON.stringify(arguments));
if (options.javaPid) {
  writeFileSync(options.javaPid, String(process.pid));
  setInterval(() => {}, 1000);
  return;
}
if (options.echoStdio) {
  writeSync(process.stderr.fd, options.stderr);
  writeSync(process.stdout.fd, readFileSync(process.stdin.fd));
  process.exit(options.jarExitCode ?? 0);
}
process.exit(options.jarExitCode ?? 0);
`,
  );
  await chmod(javaPath, 0o755);
}

function runSeed4J(testDirectory, args, stdin = 'pipe') {
  return spawnSync(process.execPath, [seed4jCommand, ...args], {
    cwd: repositoryRoot,
    encoding: 'utf8',
    env: {
      PATH: testDirectory,
    },
    stdio: [stdin, 'pipe', 'pipe'],
  });
}

function spawnSeed4J(testDirectory) {
  const seed4j = spawn(process.execPath, [seed4jCommand], {
    cwd: repositoryRoot,
    env: {
      PATH: testDirectory,
    },
  });

  return {
    result: new Promise(resolveResult => {
      seed4j.on('close', (status, signal) => {
        resolveResult({ signal, status });
      });
    }),
  };
}

async function waitForFile(path) {
  for (let attempts = 0; attempts < 400; attempts++) {
    if (existsSync(path)) {
      return;
    }
    await new Promise(resolveWait => setTimeout(resolveWait, 5));
  }

  assert.fail(`Timed out waiting for ${path}`);
}
