const { copyFileSync, existsSync, mkdirSync, readdirSync } = require('node:fs');
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
console.log(`Prepared ${npmJar}`);

function fail(message) {
  console.error(message);
  process.exit(1);
}
