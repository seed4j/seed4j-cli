#!/usr/bin/env node

const { spawn, spawnSync } = require('node:child_process');
const { writeSync } = require('node:fs');
const { join } = require('node:path');

const minimumJavaVersion = 25;
const jarPath = join(__dirname, '..', 'dist', 'seed4j-cli.jar');
const javaVersion = spawnSync('java', ['--version'], {
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe'],
});

if (javaVersion.error?.code === 'ENOENT') {
  writeSync(
    process.stderr.fd,
    'Java 25 or higher is required to run seed4j-cli. Install Java 25 or higher and make sure `java` is available on PATH.\n',
  );
  process.exit(1);
}

const javaVersionOutput = `${javaVersion.stdout ?? ''}\n${javaVersion.stderr ?? ''}`;
const javaMajorVersionMatch = javaVersionOutput.match(/^(?:openjdk|java) (\d+)(?:[.\s]|$)/m);
const javaMajorVersion = javaMajorVersionMatch ? Number(javaMajorVersionMatch[1]) : undefined;

if (javaVersion.status !== 0 || javaMajorVersion === undefined) {
  writeSync(
    process.stderr.fd,
    `Unable to determine the Java version from \`java --version\`. Install Java ${minimumJavaVersion} or higher and make sure it is the first \`java\` on PATH.\n`,
  );
  process.exit(1);
}

if (javaMajorVersion < minimumJavaVersion) {
  writeSync(
    process.stderr.fd,
    `seed4j-cli requires Java ${minimumJavaVersion} or higher, but Java ${javaMajorVersion} was found. Install a compatible Java version and make sure it is the first \`java\` on PATH.\n`,
  );
  process.exit(1);
}

const java = spawn('java', ['-jar', jarPath, ...process.argv.slice(2)], {
  stdio: 'inherit',
});

java.on('error', error => {
  if (error.code === 'ENOENT') {
    writeSync(
      process.stderr.fd,
      `Java ${minimumJavaVersion} or higher is required to run seed4j-cli. Install Java ${minimumJavaVersion} or higher and make sure \`java\` is available on PATH.\n`,
    );
    process.exit(1);
  }

  writeSync(process.stderr.fd, `Unable to start seed4j-cli: ${error.message}\n`);
  process.exit(1);
});

java.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }

  process.exit(code ?? 1);
});
