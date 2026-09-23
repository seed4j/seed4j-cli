const { copyFileSync, existsSync, mkdirSync, readdirSync } = require('node:fs');
const { createHash } = require('node:crypto');
const { spawnSync } = require('node:child_process');
const { writeFileSync } = require('node:fs');
const { join, resolve } = require('node:path');

const repositoryRoot = resolve(__dirname, '..');
const targetDirectory = join(repositoryRoot, 'target');
const distDirectory = join(repositoryRoot, 'dist');
const npmJar = join(distDirectory, 'seed4j-cli.jar');

if (!existsSync(targetDirectory)) {
  fail('Maven target directory not found. Run `./mvnw --batch-mode -ntp clean package` before `npm run package:prepare`.');
}

const candidates = readdirSync(targetDirectory)
  .filter(fileName => /^seed4j-cli-.*\.jar$/.test(fileName))
  .filter(fileName => !fileName.endsWith('-sources.jar'))
  .filter(fileName => !fileName.endsWith('-javadoc.jar'))
  .filter(fileName => !fileName.endsWith('-original.jar'))
  .sort();

if (candidates.length === 0) {
  fail('No Maven-built seed4j-cli JAR found in target/. Run `./mvnw --batch-mode -ntp clean package` first.');
}

mkdirSync(distDirectory, { recursive: true });
copyFileSync(join(targetDirectory, candidates.at(-1)), npmJar);
const skillPrefix = 'BOOT-INF/classes/skills/seed4j-cli/';
const skillEntries = unzip(['-Z1', npmJar])
  .toString('utf8')
  .split(/\r?\n/)
  .filter(entry => entry.startsWith(skillPrefix) && entry !== skillPrefix);
const entries = skillEntries.filter(entry => !entry.endsWith('/')).sort();
if (entries.length === 0) fail('The Maven-built JAR does not contain the bundled Seed4J CLI skill.');
const directories = skillEntries
  .filter(entry => entry.endsWith('/'))
  .map(entry => entry.slice(skillPrefix.length, -1))
  .sort();
const files = Object.fromEntries(
  entries.map(entry => [
    entry.slice(skillPrefix.length),
    createHash('sha256')
      .update(unzip(['-p', npmJar, entry]))
      .digest('hex'),
  ]),
);
writeFileSync(join(distDirectory, 'skill-manifest.json'), `${JSON.stringify({ directories, files }, null, 2)}\n`);
console.log(`Prepared ${npmJar}`);

function unzip(args) {
  const result = spawnSync('unzip', args, { encoding: null });
  if (result.status !== 0) fail(result.error?.message ?? result.stderr?.toString('utf8') ?? 'Unable to read bundled skill');
  return result.stdout;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
