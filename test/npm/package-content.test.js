const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const { readdirSync } = require('node:fs');
const { relative, resolve, sep } = require('node:path');
const test = require('node:test');

const repositoryRoot = resolve(__dirname, '../..');
const documentationRoot = resolve(repositoryRoot, 'documentation');

test('includes every documentation file in the npm package', () => {
  const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const result = spawnSync(npmCommand, ['pack', '--dry-run', '--json', '--ignore-scripts'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  });

  assert.equal(result.status, 0, result.error?.message ?? result.stderr);

  const [manifest] = JSON.parse(result.stdout);
  const packagedPaths = new Set(manifest.files.map(file => normalizePath(file.path)));
  const documentationPaths = listRegularFiles(documentationRoot)
    .map(path => normalizePath(relative(repositoryRoot, path)))
    .sort();
  const missingPaths = documentationPaths.filter(path => !packagedPaths.has(path));

  assert.deepEqual(missingPaths, [], `npm package is missing documentation files:\n${missingPaths.map(path => `- ${path}`).join('\n')}`);
});

function listRegularFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(directory, entry.name);

    if (entry.isDirectory()) {
      return listRegularFiles(path);
    }

    return entry.isFile() ? [path] : [];
  });
}

function normalizePath(path) {
  return path.split(sep).join('/');
}
