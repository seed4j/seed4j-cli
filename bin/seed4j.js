#!/usr/bin/env node

const { spawn } = require('node:child_process');
const { writeSync } = require('node:fs');
const { join } = require('node:path');

const jarPath = join(__dirname, '..', 'dist', 'seed4j-cli.jar');
const java = spawn('java', ['-jar', jarPath, ...process.argv.slice(2)], {
  stdio: 'inherit',
});

java.on('error', error => {
  if (error.code === 'ENOENT') {
    writeSync(process.stderr.fd, 'Java 25 is required to run seed4j-cli. Install Java 25 and make sure `java` is available on PATH.\n');
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
