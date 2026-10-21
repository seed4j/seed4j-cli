const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { dirname, join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const policyName = 'workflows pin every third-party action to an immutable commit';
const workflowFiles = ['github-actions.yml', 'release-drafter.yml', 'release.yml', 'synchronize-experimental.yml'];
const historicalWorkflows = {
  'github-actions.yml': `steps:
  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
  - uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v6
  - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6
`,
  'release-drafter.yml': `steps:
  - name: Draft release
    uses: release-drafter/release-drafter@34d80673e067bdc0c24568d3af899c216adcfaa9 # v6
  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
`,
  'release.yml': `steps:
  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
  - uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020
  - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v5
  - name: Publish release
    uses: release-drafter/release-drafter@34d80673e067bdc0c24568d3af899c216adcfaa9
`,
  'synchronize-experimental.yml': `steps:
  - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
  - uses: actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1
`,
};

function transformWorkflows(workflows, transform) {
  return Object.fromEntries(Object.entries(workflows).map(([file, content]) => [file, transform(content, file)]));
}

function runPolicy(context, workflows) {
  const directory = mkdtempSync(join(tmpdir(), 'seed4j-action-pinning-'));
  context.after(() => rmSync(directory, { recursive: true, force: true }));
  const suite = join(directory, 'test/workflows/channel-policy.test.js');
  mkdirSync(dirname(suite), { recursive: true });
  copyFileSync(join(__dirname, 'channel-policy.test.js'), suite);
  mkdirSync(join(directory, '.github/workflows'), { recursive: true });
  for (const [file, content] of Object.entries(workflows)) {
    writeFileSync(join(directory, '.github/workflows', file), content);
  }

  const environment = { ...process.env };
  // A separate runner must not inherit the parent runner's child-process marker.
  delete environment.NODE_TEST_CONTEXT;
  const result = spawnSync(process.execPath, ['--test', '--test-reporter=tap', `--test-name-pattern=^${policyName}$`, suite], {
    cwd: directory,
    encoding: 'utf8',
    env: environment,
    timeout: 10000,
  });
  assert.ifError(result.error);
  assert.equal(result.signal, null);
  return { status: result.status, output: result.stdout + result.stderr };
}

test('pinning policy accepts current workflows', context => {
  const workflows = Object.fromEntries(
    workflowFiles.map(file => [file, readFileSync(join(repositoryRoot, '.github/workflows', file), 'utf8')]),
  );
  const result = runPolicy(context, workflows);

  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /# pass 1\b/);
});

// Hashes and affected files from https://github.com/seed4j/seed4j-cli/pull/{number}.
for (const update of [
  {
    pr: 434,
    before: 'release-drafter/release-drafter@34d80673e067bdc0c24568d3af899c216adcfaa9',
    after: 'release-drafter/release-drafter@978bb0b30c5ee8cdd332ced253758157150d43f0',
    files: ['release-drafter.yml', 'release.yml'],
  },
  {
    pr: 442,
    before: 'actions/setup-node@820762786026740c76f36085b0efc47a31fe5020',
    after: 'actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1',
    files: ['github-actions.yml', 'release.yml'],
  },
  {
    pr: 445,
    before: 'actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1',
    after: 'actions/create-github-app-token@64ab729a6294000f5aa4a4bc0b73d1f32bc45510',
    files: ['synchronize-experimental.yml'],
  },
  {
    pr: 450,
    before: 'actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6',
    after: 'actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad',
    files: ['github-actions.yml', 'release.yml'],
  },
]) {
  test(`pinning policy accepts the isolated hash update from PR #${update.pr}`, context => {
    const workflows = transformWorkflows(historicalWorkflows, (content, file) => {
      if (!update.files.includes(file)) return content;
      assert.ok(content.includes(update.before), `${file}: missing PR #${update.pr} fixture reference`);
      return content.replaceAll(update.before, update.after);
    });
    const result = runPolicy(context, workflows);

    assert.equal(result.status, 0, result.output);
    assert.match(result.output, /# pass 1\b/);
  });
}

for (const syntax of ['uses:', '- uses:']) {
  for (const comment of ['', ' # v7']) {
    test(`pinning policy accepts different complete hashes with ${syntax}${comment}`, context => {
      const result = runPolicy(
        context,
        transformWorkflows(historicalWorkflows, (content, file) =>
          file === 'release-drafter.yml'
            ? `${content}\n      ${syntax} actions/checkout@0123456789abcdef0123456789abcdef01234567${comment}\n`
            : content,
        ),
      );

      assert.equal(result.status, 0, result.output);
      assert.match(result.output, /# pass 1\b/);
    });

    for (const reference of [
      'actions/checkout@v7',
      'actions/checkout@main',
      'actions/checkout@3d3c42e',
      'actions/checkout',
      'actions/checkout@',
      'actions/checkout@0123456789abcdef0123456789abcdef0123456',
      'actions/checkout@0123456789abcdef0123456789abcdef012345678',
      'actions/checkout@0123456789ABCDEF0123456789ABCDEF01234567',
      'actions/checkout@0123456789abcdef0123456789abcdef0123456g',
    ]) {
      test(`pinning policy rejects ${reference} with ${syntax}${comment} alongside a valid occurrence`, context => {
        const result = runPolicy(
          context,
          transformWorkflows(historicalWorkflows, (content, file) =>
            file === 'release-drafter.yml' ? `${content}\n      ${syntax} ${reference}${comment}\n` : content,
          ),
        );

        assert.equal(result.status, 1, result.output);
        assert.match(result.output, /# fail 1\b/);
        assert.ok(result.output.includes('.github/workflows/release-drafter.yml'), result.output);
        assert.ok(result.output.includes(reference), result.output);
      });
    }
  }
}

test('pinning policy rejects an unknown action with a complete hash', context => {
  const result = runPolicy(
    context,
    transformWorkflows(historicalWorkflows, (content, file) =>
      file === 'release-drafter.yml' ? `${content}\n      - uses: unknown/action@0123456789abcdef0123456789abcdef01234567 # v1\n` : content,
    ),
  );

  assert.equal(result.status, 1, result.output);
  assert.match(result.output, /# fail 1\b/);
  assert.ok(result.output.includes('.github/workflows/release-drafter.yml'), result.output);
  assert.ok(result.output.includes('unknown/action@0123456789abcdef0123456789abcdef01234567'), result.output);
});

test('pinning policy accepts synthetic hashes independently of historical versions', context => {
  const workflows = transformWorkflows(historicalWorkflows, content => content.replaceAll(/@[0-9a-f]{40}/g, `@${'a'.repeat(40)}`));
  workflows['release.yml'] += `  - uses: actions/checkout@${'b'.repeat(40)} # synthetic revision\n`;
  for (const file of workflowFiles) {
    assert.notEqual(workflows[file], historicalWorkflows[file], `${file}: synthetic hashes were not applied`);
  }
  const result = runPolicy(context, workflows);

  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /# pass 1\b/);
});

for (const action of [
  'actions/checkout',
  'actions/create-github-app-token',
  'actions/setup-java',
  'actions/setup-node',
  'release-drafter/release-drafter',
]) {
  test(`pinning policy rejects removal of expected action ${action}`, context => {
    const workflows = transformWorkflows(historicalWorkflows, content =>
      content
        .split('\n')
        .filter(line => !line.includes(`uses: ${action}@`))
        .join('\n'),
    );
    assert.notDeepEqual(workflows, historicalWorkflows, `${action}: no occurrence removed`);
    for (const content of Object.values(workflows)) {
      assert.ok(!content.includes(`${action}@`), `${action}: an occurrence remains`);
    }
    const result = runPolicy(context, workflows);

    assert.equal(result.status, 1, result.output);
    assert.match(result.output, /# fail 1\b/);
    assert.ok(result.output.includes(action), result.output);
  });
}
