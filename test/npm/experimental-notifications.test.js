const assert = require('node:assert/strict');
const { spawn, spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { chmodSync, copyFileSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, symlinkSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, resolve } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');

test('a newer experimental npm release is reported after a successful command', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, 'Java result\n');
  assert.match(result.stderr, /1\.2\.0-experimental\.3/);
  assert.match(result.stderr, /1\.2\.0-experimental\.12/);
  assert.match(result.stderr, /npm install -g seed4j-cli@experimental/);
});

test('a linked local skill is reported without following the link', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const external = join(fixture.root, 'external-skill');
  mkdirSync(external);
  writeFileSync(join(external, 'SKILL.md'), 'External skill\n');
  const skillParent = join(fixture.project, '.agents/skills');
  mkdirSync(skillParent, { recursive: true });
  symlinkSync(external, join(skillParent, 'seed4j-cli'));

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.match(result.stderr, /seed4j skill install/);
  assert.equal(readFileSync(join(external, 'SKILL.md'), 'utf8'), 'External skill\n');
});

test('an identical installed skill does not produce a notice', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill({ 'SKILL.md': 'Bundled skill\n', 'references/usage.md': 'Usage\n' });
  const skill = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(join(skill, 'references'), { recursive: true });
  writeFileSync(join(skill, 'SKILL.md'), 'Bundled skill\n');
  writeFileSync(join(skill, 'references/usage.md'), 'Usage\n');

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
});

test('an experimental release is reported once per rolling day and a new release is reported independently', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });

  const first = fixture.run(['--version'], { TEST_NOW: '1000000000' });
  const repeated = fixture.run(['--version'], { TEST_NOW: '1000001000' });
  fixture.registry({ experimental: '1.2.0-experimental.13' });
  const refreshed = fixture.run(['--version'], { TEST_NOW: '1086401000' });

  assert.match(first.stderr, /experimental\.12/);
  assert.equal(repeated.stderr, '');
  assert.match(refreshed.stderr, /experimental\.13/);
  assert.equal(fixture.registryCalls(), 2);
});

test('equal, older, missing, and malformed experimental tags do not produce a notice', t => {
  for (const tags of [
    { experimental: '1.2.0-experimental.3' },
    { experimental: '1.2.0-experimental.2' },
    { experimental: '1.1.9-experimental.99' },
    {},
    { experimental: 'latest' },
  ]) {
    const fixture = createFixture(t);
    fixture.registry(tags);

    const result = fixture.run(['--version']);

    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stderr, '', JSON.stringify(tags));
  }
});

test('a failed registry lookup backs off for an hour without affecting command output', t => {
  const fixture = createFixture(t);
  fixture.registry({ error: 'offline' });

  const first = fixture.run(['--version'], { TEST_NOW: '1000000000' });
  const backoff = fixture.run(['--version'], { TEST_NOW: '1000001000' });
  fixture.registry({ experimental: '1.2.0-experimental.12' });
  const retried = fixture.run(['--version'], { TEST_NOW: '1003601000' });

  assert.equal(first.stdout, 'Java result\n');
  assert.equal(first.stderr, '');
  assert.equal(backoff.stderr, '');
  assert.match(retried.stderr, /experimental\.12/);
  assert.equal(fixture.registryCalls(), 2);
});

test('a slow registry lookup does not hold a completed command open', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12', delayMs: 2000 });

  const started = Date.now();
  const result = fixture.run(['--version']);
  const elapsed = Date.now() - started;

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, 'Java result\n');
  assert.equal(result.stderr, '');
  assert.ok(elapsed < 1000, `Command took ${elapsed} ms`);
});

test('the registry lookup expires after one second during a longer command', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12', delayMs: 2000 });

  const result = fixture.run(['--version'], { FAKE_JAVA_DELAY_MS: '1300' });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
  assert.equal(fixture.registryCalls(), 1);
  assert.ok(JSON.parse(readFileSync(join(fixture.home, '.cache/seed4j-cli/update-notifications.json'), 'utf8')).registry.failedAt);
});

test('completion, skill installation, and unsuccessful commands suppress notices', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });
  fixture.bundledSkill('Bundled skill\n');
  const skill = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(skill, { recursive: true });
  writeFileSync(join(skill, 'SKILL.md'), 'Modified skill\n');

  const completion = fixture.run(['completion', 'bash']);
  const install = fixture.run(['skill', 'install']);
  const failed = fixture.run(['--version'], { FAKE_JAVA_EXIT_CODE: '2' });

  assert.equal(completion.stderr, '');
  assert.equal(install.stderr, '');
  assert.equal(failed.status, 2);
  assert.equal(failed.stderr, '');
});

test('local and user-level skill differences have separate notices and refresh actions', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  const global = join(fixture.home, '.agents/skills/seed4j-cli');
  for (const destination of [local, global]) {
    mkdirSync(destination, { recursive: true });
    writeFileSync(join(destination, 'SKILL.md'), 'Modified skill\n');
  }

  const first = fixture.run(['--version']);
  const repeated = fixture.run(['--version']);

  assert.match(first.stderr, new RegExp(local));
  assert.match(first.stderr, new RegExp(global));
  assert.match(first.stderr, /run seed4j skill install to refresh/);
  assert.match(first.stderr, /run seed4j skill install --global to refresh/);
  assert.equal(repeated.stderr, '');
  assert.equal(readFileSync(join(local, 'SKILL.md'), 'utf8'), 'Modified skill\n');
  assert.equal(readFileSync(join(global, 'SKILL.md'), 'utf8'), 'Modified skill\n');
});

test('missing and extra skill files are detected while an absent skill is ignored', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill({ 'SKILL.md': 'Bundled skill\n', 'references/usage.md': 'Usage\n' });
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Bundled skill\n');
  writeFileSync(join(local, 'extra.md'), 'Extra\n');

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.deepEqual(readdirSync(local).sort(), ['SKILL.md', 'extra.md']);
  assert.equal(result.stderr.includes('--global'), false);
});

test('concurrent invocations claim one notice for the same experimental release', async t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });

  const results = await Promise.all([fixture.runAsync(['--version']), fixture.runAsync(['--version'])]);

  assert.deepEqual(
    results.map(result => result.status),
    [0, 0],
  );
  assert.equal(results.filter(result => result.stderr.includes('experimental.12')).length, 1);
});

test('the stable npm channel does not perform notification checks', t => {
  const fixture = createFixture(t);
  fixture.version('1.2.0');
  fixture.registry({ experimental: '1.3.0-experimental.1' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Modified skill\n');

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
});

test('expired registry data is not reported when refresh fails', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });

  const first = fixture.run(['--version'], { TEST_NOW: '1000000000' });
  fixture.registry({ error: 'offline' });
  const expired = fixture.run(['--version'], { TEST_NOW: '1086400001' });

  assert.match(first.stderr, /experimental\.12/);
  assert.equal(expired.status, 0, expired.stderr);
  assert.equal(expired.stderr, '');
});

test('a signaled Java process propagates its signal without notices', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.12' });

  const result = fixture.run(['--version'], { FAKE_JAVA_SIGNAL: 'SIGTERM' });

  assert.equal(result.status, null);
  assert.equal(result.signal, 'SIGTERM');
  assert.equal(result.stderr, '');
});

test('an unreadable skill destination does not produce a confident difference notice', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Modified skill\n');
  chmodSync(local, 0o000);
  let result;
  try {
    result = fixture.run(['--version']);
  } finally {
    chmodSync(local, 0o700);
  }

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
});

test('a symlink inside a skill is reported without reading its target', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  const external = join(fixture.root, 'external.md');
  writeFileSync(external, 'External content\n');
  symlinkSync(external, join(local, 'SKILL.md'));

  const result = fixture.run(['--version']);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.equal(readFileSync(external, 'utf8'), 'External content\n');
});

test('a skill file replaced with a symlink during inspection is never read through the link', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Modified skill\n');
  const external = join(fixture.root, 'external.md');
  writeFileSync(external, 'Bundled skill\n');

  const result = fixture.run(['--version'], { SWAP_SKILL_FILE: join(local, 'SKILL.md'), SWAP_SKILL_TARGET: external });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.equal(readFileSync(external, 'utf8'), 'Bundled skill\n');
});

test('a skill root replaced with a symlink during inspection is never traversed', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Modified skill\n');
  const external = join(fixture.root, 'external-skill');
  mkdirSync(external);
  writeFileSync(join(external, 'SKILL.md'), 'Bundled skill\n');

  const result = fixture.run(['--version'], { SWAP_SKILL_ROOT: local, SWAP_SKILL_TARGET: external });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.equal(readFileSync(join(external, 'SKILL.md'), 'utf8'), 'Bundled skill\n');
});

test('a cached experimental version dated in the future is ignored', t => {
  const fixture = createFixture(t);
  fixture.registry({ error: 'offline' });
  fixture.cache({ registry: { version: '1.2.0-experimental.12', checkedAt: 2000000000 } });

  const result = fixture.run(['--version'], { TEST_NOW: '1000000000' });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stderr, '');
  assert.equal(fixture.registryCalls(), 1);
});

test('a Windows-style invocation reports changed local and global skill trees', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  const global = join(fixture.home, '.agents/skills/seed4j-cli');
  for (const destination of [local, global]) {
    mkdirSync(destination, { recursive: true });
    writeFileSync(join(destination, 'SKILL.md'), 'Changed skill\n');
  }

  const result = fixture.run(['--version'], { TEST_PLATFORM: 'win32' });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /seed4j skill install to refresh/);
  assert.match(result.stderr, /seed4j skill install --global to refresh/);
});

test('a Windows-style invocation ignores an identical skill and detects missing or extra files', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill({ 'SKILL.md': 'Bundled skill\n', 'references/usage.md': 'Usage\n' });
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(join(local, 'references'), { recursive: true });
  writeFileSync(join(local, 'SKILL.md'), 'Bundled skill\n');
  writeFileSync(join(local, 'references/usage.md'), 'Usage\n');

  const identical = fixture.run(['--version'], { TEST_PLATFORM: 'win32' });
  require('node:fs').unlinkSync(join(local, 'references/usage.md'));
  writeFileSync(join(local, 'extra.md'), 'Extra\n');
  const different = fixture.run(['--version'], { TEST_PLATFORM: 'win32' });

  assert.equal(identical.status, 0, identical.stderr);
  assert.equal(identical.stderr, '');
  assert.match(different.stderr, /differs from the bundled skill/);
});

test('a Windows-style invocation reports a static symlink without reading its target', t => {
  const fixture = createFixture(t);
  fixture.registry({ experimental: '1.2.0-experimental.3' });
  fixture.bundledSkill('Bundled skill\n');
  const local = join(fixture.project, '.agents/skills/seed4j-cli');
  mkdirSync(local, { recursive: true });
  const external = join(fixture.root, 'external.md');
  writeFileSync(external, 'Bundled skill\n');
  symlinkSync(external, join(local, 'SKILL.md'));

  const result = fixture.run(['--version'], { TEST_PLATFORM: 'win32' });

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stderr, /differs from the bundled skill/);
  assert.equal(readFileSync(external, 'utf8'), 'Bundled skill\n');
});

function createFixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'seed4j-notifications-'));
  t.after(() => require('node:fs').rmSync(root, { recursive: true, force: true }));
  const packageRoot = join(root, 'package');
  const bin = join(packageRoot, 'bin');
  const commands = join(root, 'commands');
  const home = join(root, 'home');
  const project = join(root, 'project');
  for (const directory of [bin, commands, home, project]) mkdirSync(directory, { recursive: true });
  copyFileSync(join(repositoryRoot, 'bin/seed4j.js'), join(bin, 'seed4j.js'));
  copyFileSync(join(repositoryRoot, 'bin/update-notifications.js'), join(bin, 'update-notifications.js'));
  writeFileSync(join(packageRoot, 'package.json'), JSON.stringify({ name: 'seed4j-cli', version: '1.2.0-experimental.3' }));
  const java = join(commands, 'java');
  writeFileSync(
    java,
    `#!${process.execPath}\nif (process.argv[2] === '--version') { console.log('openjdk 25.0.2'); } else { setTimeout(() => { if (process.env.FAKE_JAVA_SIGNAL) process.kill(process.pid, process.env.FAKE_JAVA_SIGNAL); else { console.log('Java result'); process.exit(Number(process.env.FAKE_JAVA_EXIT_CODE || 0)); } }, Number(process.env.FAKE_JAVA_DELAY_MS || 100)); }\n`,
  );
  chmodSync(java, 0o755);
  const registryFile = join(root, 'registry.json');
  const registryLog = join(root, 'registry-calls');
  const preload = join(root, 'preload.cjs');
  writeFileSync(
    preload,
    `globalThis.fetch = async (_, { signal } = {}) => { require('node:fs').appendFileSync(${JSON.stringify(registryLog)}, 'call\\n'); const value = JSON.parse(require('node:fs').readFileSync(${JSON.stringify(registryFile)}, 'utf8')); if (value.delayMs) await new Promise((resolve, reject) => { const timer = setTimeout(resolve, value.delayMs); signal?.addEventListener('abort', () => { clearTimeout(timer); reject(new Error('aborted')); }, { once: true }); }); if (value.error) throw new Error(value.error); return { ok: value.status !== 500, json: async () => value }; }; if (process.env.TEST_NOW) Date.now = () => Number(process.env.TEST_NOW); if (process.env.SWAP_SKILL_FILE) { const fs = require('node:fs'); const original = fs.readdirSync; fs.readdirSync = (...args) => { const result = original(...args); if (process.env.SWAP_SKILL_FILE && fs.realpathSync(args[0]) === require('node:path').dirname(process.env.SWAP_SKILL_FILE)) { const path = process.env.SWAP_SKILL_FILE; fs.renameSync(path, process.env.SWAP_SKILL_TARGET + '.original'); fs.symlinkSync(process.env.SWAP_SKILL_TARGET, path); delete process.env.SWAP_SKILL_FILE; } return result; }; }\n`,
  );
  require('node:fs').appendFileSync(
    preload,
    `if (process.env.SWAP_SKILL_ROOT) { const fs = require('node:fs'); const original = fs.lstatSync; fs.lstatSync = (...args) => { const stat = original(...args); if (process.env.SWAP_SKILL_ROOT && args[0] === process.env.SWAP_SKILL_ROOT) { fs.renameSync(args[0], process.env.SWAP_SKILL_TARGET + '.original'); fs.symlinkSync(process.env.SWAP_SKILL_TARGET, args[0]); delete process.env.SWAP_SKILL_ROOT; } return stat; }; }\n`,
  );
  require('node:fs').appendFileSync(
    preload,
    `if (process.env.TEST_PLATFORM) { require('node:path'); Object.defineProperty(process, 'platform', { value: process.env.TEST_PLATFORM }); }\n`,
  );
  return {
    registry: value => writeFileSync(registryFile, JSON.stringify(value)),
    cache: value => {
      const cacheDirectory = join(home, '.cache/seed4j-cli');
      mkdirSync(cacheDirectory, { recursive: true });
      writeFileSync(join(cacheDirectory, 'update-notifications.json'), JSON.stringify(value));
    },
    version: value => writeFileSync(join(packageRoot, 'package.json'), JSON.stringify({ name: 'seed4j-cli', version: value })),
    registryCalls: () => readFileSync(registryLog, 'utf8').trim().split('\n').length,
    bundledSkill: content => {
      mkdirSync(join(packageRoot, 'dist'), { recursive: true });
      const files = typeof content === 'string' ? { 'SKILL.md': content } : content;
      const directories = [...new Set(Object.keys(files).flatMap(path => path.split('/').slice(0, -1)))].filter(Boolean);
      writeFileSync(
        join(packageRoot, 'dist/skill-manifest.json'),
        JSON.stringify({
          directories,
          files: Object.fromEntries(Object.entries(files).map(([path, value]) => [path, createHash('sha256').update(value).digest('hex')])),
        }),
      );
    },
    run: (args, extraEnvironment = {}) =>
      spawnSync(process.execPath, [join(bin, 'seed4j.js'), ...args], {
        cwd: project,
        encoding: 'utf8',
        env: { ...process.env, HOME: home, USERPROFILE: home, PATH: commands, NODE_OPTIONS: `--require=${preload}`, ...extraEnvironment },
      }),
    runAsync: args =>
      new Promise(resolveResult => {
        const child = spawn(process.execPath, [join(bin, 'seed4j.js'), ...args], {
          cwd: project,
          env: { ...process.env, HOME: home, USERPROFILE: home, PATH: commands, NODE_OPTIONS: `--require=${preload}` },
        });
        let stderr = '';
        child.stderr.on('data', chunk => {
          stderr += chunk;
        });
        child.on('close', status => resolveResult({ status, stderr }));
      }),
    root,
    packageRoot,
    home,
    project,
  };
}
