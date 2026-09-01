const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join, resolve, sep } = require('node:path');

const repositoryRoot = resolve(__dirname, '..');
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const temporaryRoot = mkdtempSync(join(tmpdir(), 'seed4j-cli-packed-skill-'));
let tarballPath;

try {
  const pack = run(npmCommand, ['pack', '--silent', '--ignore-scripts'], repositoryRoot);
  tarballPath = resolve(repositoryRoot, pack.stdout.trim().split(/\r?\n/).at(-1));
  const prefix = join(temporaryRoot, 'prefix');
  run(npmCommand, ['install', '--global', '--offline', '--ignore-scripts', '--prefix', prefix, tarballPath], repositoryRoot);

  const seed4j = process.platform === 'win32' ? join(prefix, 'seed4j.cmd') : join(prefix, 'bin/seed4j');
  const userHome = join(temporaryRoot, 'home');
  const javaOptions = `-Duser.home=${userHome}`;
  const environment = { ...process.env, HOME: userHome, JAVA_TOOL_OPTIONS: javaOptions, USERPROFILE: userHome };

  const localProject = join(temporaryRoot, 'local-project');
  const local = run(seed4j, ['skill', 'install'], localProject, environment, true);
  const localDestination = resolve(localProject, '.agents/skills/seed4j-cli');
  assert.equal(local.stdout, `Installed Seed4J CLI skill at ${localDestination}.\n`);
  assertCanonicalSkill(localDestination);

  const globalWorkingDirectory = join(temporaryRoot, 'global-working-directory');
  const global = run(seed4j, ['skill', 'install', '--global'], globalWorkingDirectory, environment, true);
  const globalDestination = resolve(userHome, '.agents/skills/seed4j-cli');
  assert.equal(global.stdout, `Installed Seed4J CLI skill at ${globalDestination}.\n`);
  assertCanonicalSkill(globalDestination);
  assert.deepEqual(readdirSync(globalWorkingDirectory), []);

  const overlay = join(temporaryRoot, 'extension-overlay');
  writeOverlaySkill(overlay);
  const overlayProject = join(temporaryRoot, 'overlay-project');
  const packageRoot = process.platform === 'win32' ? join(prefix, 'node_modules/seed4j-cli') : join(prefix, 'lib/node_modules/seed4j-cli');
  const packagedJar = join(packageRoot, 'dist/seed4j-cli.jar');
  const overlayRun = run(
    'java',
    [`-Dloader.path=${overlay}`, '-cp', packagedJar, 'org.springframework.boot.loader.launch.PropertiesLauncher', 'skill', 'install'],
    overlayProject,
    environment,
    true,
  );
  const overlayDestination = resolve(overlayProject, '.agents/skills/seed4j-cli');
  assert.equal(overlayRun.stdout, `Installed Seed4J CLI skill at ${overlayDestination}.\n`);
  assertCanonicalSkill(overlayDestination);
} finally {
  if (tarballPath) {
    rmSync(tarballPath, { force: true });
  }
  rmSync(temporaryRoot, { force: true, recursive: true });
}

function assertCanonicalSkill(destination) {
  assert.deepEqual(listFiles(destination), ['SKILL.md', 'references/applying-modules.md', 'references/module-set-planning.md']);
  assert.match(readFileSync(join(destination, 'SKILL.md'), 'utf8'), /^---\nname: seed4j-cli\ndescription: Use Seed4J CLI/);
  assert.match(readFileSync(join(destination, 'references/applying-modules.md'), 'utf8'), /seed4j apply <module> --plan/);
  assert.match(readFileSync(join(destination, 'references/module-set-planning.md'), 'utf8'), /seed4j apply-set <modules\.\.\.> --plan/);
}

function writeOverlaySkill(overlay) {
  const references = join(overlay, 'skills/seed4j-cli/references');
  mkdirSync(references, { recursive: true });
  writeFileSync(join(overlay, 'skills/seed4j-cli/SKILL.md'), 'shadowed skill\n');
  writeFileSync(join(references, 'applying-modules.md'), 'shadowed skill\n');
  writeFileSync(join(references, 'module-set-planning.md'), 'shadowed skill\n');
}

function listFiles(directory) {
  return readdirSync(directory, { withFileTypes: true })
    .flatMap(entry => {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        return listFiles(path).map(child => join(entry.name, child));
      }
      return entry.isFile() ? [entry.name] : [];
    })
    .map(path => path.split(sep).join('/'))
    .sort();
}

function run(command, args, cwd, env = process.env, createWorkingDirectory = false) {
  if (createWorkingDirectory) {
    require('node:fs').mkdirSync(cwd, { recursive: true });
  }
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', env });
  assert.equal(result.status, 0, result.error?.message ?? result.stderr);
  return result;
}
