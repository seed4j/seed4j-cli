const { createHash } = require('node:crypto');
const {
  constants,
  existsSync,
  fstatSync,
  lstatSync,
  mkdirSync,
  openSync,
  closeSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync,
  writeSync,
} = require('node:fs');
const { homedir } = require('node:os');
const { join, resolve } = require('node:path');

const day = 24 * 60 * 60 * 1000;
const hour = 60 * 60 * 1000;
const packageRoot = resolve(__dirname, '..');
const packageVersion = require('../package.json').version;
const experimentalVersion = /^\d+\.\d+\.\d+-experimental\.\d+$/.test(packageVersion);

function startNotifications(args) {
  if (!experimentalVersion || args[0] === 'completion' || (args[0] === 'skill' && args[1] === 'install')) {
    return { emit() {} };
  }

  const home = homedir();
  const cacheFile = join(home, '.cache', 'seed4j-cli', 'update-notifications.json');
  const registry = { version: cachedRegistry(cacheFile) };
  if (registry.version === undefined && registryCheckDue(cacheFile)) {
    Promise.resolve()
      .then(() => fetch('https://registry.npmjs.org/seed4j-cli/dist-tags', { signal: AbortSignal.timeout(1000) }))
      .then(response => {
        if (!response.ok) throw new Error('Registry response failed');
        return response.json();
      })
      .then(tags => {
        if (!validVersion(tags?.experimental)) throw new Error('Invalid experimental tag');
        registry.version = tags.experimental;
        updateCache(cacheFile, state => {
          state.registry = { version: tags.experimental, checkedAt: Date.now() };
        });
      })
      .catch(() => {
        updateCache(cacheFile, state => {
          state.registry = { failedAt: Date.now() };
        });
      });
  }

  return {
    emit() {
      if (registry.version && newer(registry.version, packageVersion)) {
        notice(
          cacheFile,
          `npm:${packageVersion}:${registry.version}`,
          `Seed4J CLI ${packageVersion}: experimental version ${registry.version} is available after this work. Run npm install -g seed4j-cli@experimental to update later.`,
        );
      }
      skillNotices(cacheFile, home);
    },
  };
}

function validVersion(version) {
  return typeof version === 'string' && /^\d+\.\d+\.\d+-experimental\.\d+$/.test(version);
}

function newer(available, installed) {
  const numbers = version => version.match(/\d+/g).map(Number);
  const a = numbers(available);
  const b = numbers(installed);
  return a.some((number, index) => number > b[index] && a.slice(0, index).every((value, previous) => value === b[previous]));
}

function cachedRegistry(cacheFile) {
  const registry = readCache(cacheFile).registry;
  const age = Date.now() - registry?.checkedAt;
  return validVersion(registry?.version) && Number.isFinite(registry.checkedAt) && age >= 0 && age < day ? registry.version : undefined;
}

function registryCheckDue(cacheFile) {
  const registry = readCache(cacheFile).registry;
  const age = Date.now() - registry?.failedAt;
  return !Number.isFinite(registry?.failedAt) || age < 0 || age >= hour;
}

function readCache(cacheFile) {
  try {
    const parsed = JSON.parse(readFileSync(cacheFile, 'utf8'));
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function updateCache(cacheFile, update) {
  const lock = `${cacheFile}.lock`;
  let descriptor;
  try {
    mkdirSync(resolve(cacheFile, '..'), { recursive: true });
    descriptor = openSync(lock, 'wx');
    const state = readCache(cacheFile);
    update(state);
    const temporary = `${cacheFile}.${process.pid}.tmp`;
    writeFileSync(temporary, JSON.stringify(state));
    renameSync(temporary, cacheFile);
    return true;
  } catch {
    return false;
  } finally {
    if (descriptor !== undefined) {
      try {
        closeSync(descriptor);
        rmSync(lock, { force: true });
      } catch {
        // Notification cache cleanup is advisory.
      }
    }
  }
}

function notice(cacheFile, key, message) {
  let claimed = false;
  const saved = updateCache(cacheFile, state => {
    state.notices ??= {};
    if (!Number.isFinite(state.notices[key]) || state.notices[key] > Date.now() || Date.now() - state.notices[key] >= day) {
      state.notices[key] = Date.now();
      claimed = true;
    }
  });
  if (saved && claimed) writeSync(process.stderr.fd, `${message}\n`);
}

function skillNotices(cacheFile, home) {
  let manifest;
  try {
    manifest = JSON.parse(readFileSync(join(packageRoot, 'dist', 'skill-manifest.json'), 'utf8'));
  } catch {
    return;
  }
  for (const [destination, action] of [
    [resolve(process.cwd(), '.agents/skills/seed4j-cli'), 'seed4j skill install'],
    [resolve(home, '.agents/skills/seed4j-cli'), 'seed4j skill install --global'],
  ]) {
    try {
      if (!existsSync(destination) && !lstatAvailable(destination)) continue;
      if (lstatSync(destination).isSymbolicLink() || skillDiffers(destination, manifest)) {
        notice(
          cacheFile,
          `skill:${destination}`,
          `Seed4J CLI skill at ${destination} differs from the bundled skill. After this work, run ${action} to refresh it.`,
        );
      }
    } catch {
      // Skill inspection is advisory.
    }
  }
}

function skillDiffers(destination, manifest) {
  const installed = skillTree(destination);
  const installedPaths = Object.keys(installed.files);
  const bundledPaths = Object.keys(manifest.files);
  return (
    installedPaths.length !== bundledPaths.length
    || installedPaths.some(path => installed.files[path] !== manifest.files[path])
    || JSON.stringify(installed.directories) !== JSON.stringify(manifest.directories)
  );
}

function lstatAvailable(path) {
  try {
    lstatSync(path);
    return true;
  } catch {
    return false;
  }
}

function skillTree(destination) {
  const descriptorRoot = process.platform === 'linux' ? '/proc/self/fd' : process.platform === 'darwin' ? '/dev/fd' : undefined;
  if (!descriptorRoot || !constants.O_NOFOLLOW || !constants.O_DIRECTORY) return skillTreeByPath(destination);
  const files = {};
  const directories = [];
  function visit(directoryDescriptor, prefix) {
    const directory = join(descriptorRoot, String(directoryDescriptor));
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const relative = prefix ? `${prefix}/${entry.name}` : entry.name;
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        let childDescriptor;
        try {
          childDescriptor = openSync(path, constants.O_RDONLY | constants.O_DIRECTORY | constants.O_NOFOLLOW);
          directories.push(relative);
          visit(childDescriptor, relative);
        } catch (error) {
          if (lstatSync(path).isSymbolicLink()) files[relative] = null;
          else throw error;
        } finally {
          if (childDescriptor !== undefined) closeSync(childDescriptor);
        }
      } else if (entry.isFile()) {
        let fileDescriptor;
        try {
          fileDescriptor = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
          files[relative] = fstatSync(fileDescriptor).isFile()
            ? createHash('sha256').update(readFileSync(fileDescriptor)).digest('hex')
            : null;
        } catch (error) {
          if (lstatSync(path).isSymbolicLink()) files[relative] = null;
          else throw error;
        } finally {
          if (fileDescriptor !== undefined) closeSync(fileDescriptor);
        }
      } else files[relative] = null;
    }
  }
  let rootDescriptor;
  try {
    rootDescriptor = openSync(destination, constants.O_RDONLY | constants.O_DIRECTORY | constants.O_NOFOLLOW);
    visit(rootDescriptor, '');
  } catch (error) {
    if (lstatSync(destination).isSymbolicLink()) return { directories: [], files: { '.': null } };
    throw error;
  } finally {
    if (rootDescriptor !== undefined) closeSync(rootDescriptor);
  }
  if (lstatSync(destination).isSymbolicLink()) return { directories: [], files: { '.': null } };
  return { directories: directories.sort(), files };
}

function skillTreeByPath(destination) {
  const files = {};
  const directories = [];
  function visit(directory, prefix) {
    if (!lstatSync(directory).isDirectory()) throw new Error('Skill directory changed during inspection');
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const relative = prefix ? `${prefix}/${entry.name}` : entry.name;
      const path = join(directory, entry.name);
      const type = lstatSync(path);
      if (type.isDirectory()) {
        directories.push(relative);
        visit(path, relative);
      } else if (type.isFile()) {
        let descriptor;
        try {
          descriptor = openSync(path, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0));
          files[relative] = fstatSync(descriptor).isFile() ? createHash('sha256').update(readFileSync(descriptor)).digest('hex') : null;
        } finally {
          if (descriptor !== undefined) closeSync(descriptor);
        }
      } else files[relative] = null;
    }
  }
  visit(destination, '');
  if (lstatSync(destination).isSymbolicLink()) return { directories: [], files: { '.': null } };
  return { directories: directories.sort(), files };
}

module.exports = { startNotifications };
