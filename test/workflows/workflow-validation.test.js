const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { chmodSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const checksum = '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8';
const downloadUrl = 'https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz';

test('the workflow lint command validates every workflow with the pinned download and cleans temporary tools', () => {
  const fixture = lintFixture('success');
  try {
    const result = runLintCommand(fixture);
    const events = readFileSync(fixture.log, 'utf8')
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => JSON.parse(line));
    const lint = events.find(event => event.tool === 'actionlint');
    const workflowFiles = readdirSync(join(repositoryRoot, '.github/workflows'))
      .filter(name => /\.ya?ml$/.test(name))
      .sort();

    assert.equal(result.status, 0, result.stderr);
    assert.ok(events.find(event => event.tool === 'curl').args.includes(downloadUrl));
    assert.match(events.find(event => event.tool === 'sha256sum').input, new RegExp(`^${checksum}  `));
    assert.ok(lint.args.includes('-shellcheck='));
    assert.ok(lint.args.includes('-pyflakes='));
    assert.deepEqual(
      lint.args.filter(argument => /\.ya?ml$/.test(argument)).sort(),
      workflowFiles.map(file => join(repositoryRoot, '.github/workflows', file)),
    );
    assert.deepEqual(readdirSync(fixture.temporaryDirectory), []);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});

function lintFixture(scenario) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-workflow-lint-'));
  const temporaryDirectory = join(directory, 'tools with spaces');
  const toolsDirectory = join(directory, 'boundary-tools');
  const packageDirectory = join(directory, 'package');
  mkdirSync(temporaryDirectory);
  mkdirSync(toolsDirectory);
  mkdirSync(packageDirectory);
  const log = join(directory, 'events.jsonl');
  writeFileSync(log, '');
  const executable = join(packageDirectory, 'actionlint');
  writeFileSync(
    executable,
    `#!/usr/bin/env node
const fs = require('node:fs');
fs.appendFileSync(process.env.TEST_LINT_LOG, JSON.stringify({ tool: 'actionlint', args: process.argv.slice(2) }) + '\\n');
process.exit(process.env.TEST_LINT_SCENARIO === 'lint-failure' ? 17 : 0);
`,
  );
  chmodSync(executable, 0o755);
  const archive = join(directory, 'download.tar.gz');
  const packaged = spawnSync('tar', ['-czf', archive, '-C', packageDirectory, 'actionlint'], { encoding: 'utf8' });
  assert.equal(packaged.status, 0, packaged.stderr);
  installLintBoundary(
    toolsDirectory,
    'curl',
    `
const args = process.argv.slice(2);
fs.appendFileSync(process.env.TEST_LINT_LOG, JSON.stringify({ tool: 'curl', args }) + '\\n');
if (process.env.TEST_LINT_SCENARIO === 'download-failure') process.exit(22);
const outputIndex = args.includes('--output') ? args.indexOf('--output') : args.indexOf('-o');
fs.copyFileSync(process.env.TEST_LINT_ARCHIVE, args[outputIndex + 1]);
`,
  );
  installLintBoundary(
    toolsDirectory,
    'sha256sum',
    `
const input = fs.readFileSync(0, 'utf8');
fs.appendFileSync(process.env.TEST_LINT_LOG, JSON.stringify({ tool: 'sha256sum', input }) + '\\n');
if (process.env.TEST_LINT_SCENARIO === 'checksum-failure') {
  const result = require('node:child_process').spawnSync('/usr/bin/sha256sum', process.argv.slice(2), { input, encoding: 'utf8' });
  process.stdout.write(result.stdout);
  process.stderr.write(result.stderr);
  process.exit(result.status);
}
if (!input.startsWith('${checksum}  ')) process.exit(1);
`,
  );
  return { directory, temporaryDirectory, toolsDirectory, archive, log, scenario };
}

function installLintBoundary(directory, name, script) {
  const executable = join(directory, name);
  writeFileSync(executable, `#!/usr/bin/env node\nconst fs = require('node:fs');\n${script}`);
  chmodSync(executable, 0o755);
}

function runLintCommand(fixture) {
  return spawnSync('bash', [resolve(repositoryRoot, 'tests-ci/lint-workflows.sh')], {
    cwd: fixture.directory,
    encoding: 'utf8',
    env: {
      ...process.env,
      PATH: `${fixture.toolsDirectory}:${process.env.PATH}`,
      TMPDIR: fixture.temporaryDirectory,
      TEST_LINT_SCENARIO: fixture.scenario,
      TEST_LINT_LOG: fixture.log,
      TEST_LINT_ARCHIVE: fixture.archive,
    },
  });
}

test('the tests check runs workflow validation after checkout and propagates its failure before the build', () => {
  const buildWorkflow = readFileSync(join(repositoryRoot, '.github/workflows/github-actions.yml'), 'utf8');
  const workflowStep = /- name: 'Test: validate workflows'\n\s+run: ([^\n]+)/.exec(buildWorkflow);
  const fixture = lintFixture('lint-failure');
  try {
    assert.ok(workflowStep, 'the tests check must execute workflow validation');
    const result = spawnSync('bash', ['-e', '-o', 'pipefail', '-c', workflowStep[1]], {
      cwd: repositoryRoot,
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${fixture.toolsDirectory}:${process.env.PATH}`,
        TMPDIR: fixture.temporaryDirectory,
        TEST_LINT_SCENARIO: fixture.scenario,
        TEST_LINT_LOG: fixture.log,
        TEST_LINT_ARCHIVE: fixture.archive,
      },
    });

    assert.equal(result.status, 17, result.stderr);
    assert.ok(buildWorkflow.indexOf('actions/checkout') < workflowStep.index);
    assert.ok(workflowStep.index < buildWorkflow.indexOf('./mvnw --batch-mode -ntp clean verify'));
    assert.deepEqual(readdirSync(fixture.temporaryDirectory), []);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});

test('a failed actionlint download fails validation and removes temporary files', () => {
  const fixture = lintFixture('download-failure');
  try {
    const result = runLintCommand(fixture);
    const events = readFileSync(fixture.log, 'utf8')
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => JSON.parse(line));

    assert.equal(result.status, 22, result.stderr);
    assert.equal(
      events.some(event => event.tool === 'sha256sum'),
      false,
    );
    assert.equal(
      events.some(event => event.tool === 'actionlint'),
      false,
    );
    assert.deepEqual(readdirSync(fixture.temporaryDirectory), []);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});

test('a downloaded archive with the wrong checksum fails before any linter executes', () => {
  const fixture = lintFixture('checksum-failure');
  try {
    const result = runLintCommand(fixture);
    const events = readFileSync(fixture.log, 'utf8')
      .trim()
      .split('\n')
      .filter(Boolean)
      .map(line => JSON.parse(line));

    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /computed checksum did NOT match/);
    assert.equal(
      events.some(event => event.tool === 'actionlint'),
      false,
    );
    assert.deepEqual(readdirSync(fixture.temporaryDirectory), []);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});
