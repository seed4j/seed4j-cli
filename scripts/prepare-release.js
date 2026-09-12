const { spawnSync } = require('node:child_process');
const { copyFileSync, existsSync, mkdirSync, readFileSync } = require('node:fs');
const { join, resolve } = require('node:path');
const { validateReleaseVersion } = require('./release-version.cjs');

const releaseVersion = process.argv[2];
const releaseChannel = process.env.SEED4J_RELEASE_CHANNEL ?? 'stable';
const repositoryRoot = resolve(__dirname, '..');

try {
  validateReleaseVersion(releaseVersion, releaseChannel);
} catch (error) {
  fail(error.message);
}

run('./mvnw', ['--batch-mode', '-ntp', 'versions:set', `-DnewVersion=${releaseVersion}`, '-DgenerateBackupPoms=false']);
run('npm', ['version', releaseVersion, '--no-git-tag-version', '--allow-same-version']);
validateMavenMetadata();
validateNpmMetadata();
run('npm', ['run', 'test:npm-package']);
run('./mvnw', ['--batch-mode', '-ntp', 'clean', 'package']);
validateCliVersion();
run('npm', ['run', 'package:prepare']);
run('npm', ['pack', '--dry-run']);
run('npm', ['run', 'test:npm-packed-skill']);
if (releaseChannel === 'stable') {
  prepareGitHubReleaseAsset();
}

function run(command, arguments_) {
  const result = spawnSync(command, arguments_, {
    cwd: repositoryRoot,
    stdio: 'inherit',
  });

  if (result.error) {
    fail(`Unable to run ${command}: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`${command} exited with status ${result.status}.`);
  }
}

function validateMavenMetadata() {
  const result = spawnSync('./mvnw', ['--batch-mode', '-ntp', 'help:evaluate', '-Dexpression=project.version', '-q', '-DforceStdout'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  });

  if (result.error || result.status !== 0) {
    fail('Unable to read the Maven release version.');
  }
  if (result.stdout.trim() !== releaseVersion) {
    fail(`Maven project version ${result.stdout.trim()} must match release ${releaseVersion}.`);
  }
}

function validateNpmMetadata() {
  const packageMetadata = readJson('package.json');
  const packageLock = readJson('package-lock.json');
  const versions = [packageMetadata.version, packageLock.version, packageLock.packages?.['']?.version];

  if (versions.some(version => version !== releaseVersion)) {
    fail(`Maven release ${releaseVersion} must match package.json and package-lock.json metadata.`);
  }
}

function readJson(fileName) {
  return JSON.parse(readFileSync(join(repositoryRoot, fileName), 'utf8'));
}

function validateCliVersion() {
  const result = spawnSync('java', ['-jar', builtJar(), '--version'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  });

  if (result.error || result.status !== 0) {
    fail('Unable to read the packaged CLI version.');
  }
  if (result.stdout.split(/\r?\n/, 1)[0] !== `Seed4J CLI v${releaseVersion}`) {
    fail(`Packaged CLI does not report release ${releaseVersion}.`);
  }
}

function prepareGitHubReleaseAsset() {
  const releaseJar = builtJar();
  if (!existsSync(releaseJar)) {
    fail(`Expected release JAR ${releaseJar}.`);
  }

  const releaseDirectory = join(repositoryRoot, 'target', 'release');
  mkdirSync(releaseDirectory, { recursive: true });
  copyFileSync(releaseJar, join(releaseDirectory, `seed4j-cli-${releaseVersion}.jar`));
}

function builtJar() {
  return join(repositoryRoot, 'target', `seed4j-cli-${releaseVersion}.jar`);
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
