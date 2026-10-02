const { createHash, randomUUID } = require('node:crypto');
const { spawn } = require('node:child_process');
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
const minute = 60 * 1000;
const freshness = 6 * hour;
const leaseDuration = 30 * 1000;
const packageRoot = resolve(__dirname, '..');
const packageVersion = require('../package.json').version;
const experimentalVersion = validVersion(packageVersion);

function startNotifications(args) {
  const command = args[0] === '--debug' || /^--debug=(?:true|false)?$/i.test(args[0]) ? args.slice(1) : args;
  if (!experimentalVersion || command[0] === 'completion' || (command[0] === 'skill' && command[1] === 'install')) {
    return { emit() {} };
  }

  const home = homedir();
  const cacheFile = join(home, '.cache', 'seed4j-cli', 'update-notifications.json');
  const token = reserveRegistryCheck(cacheFile);
  if (token) startRegistryWorker(cacheFile, token);

  return {
    emit() {
      const version = cachedRegistry(cacheFile);
      if (version && newer(version, packageVersion)) {
        notice(
          cacheFile,
          `npm:${packageVersion}:${version}`,
          `Seed4J CLI ${packageVersion}: experimental version ${version} is available after this work. Run npm install -g seed4j-cli@experimental to update later.`,
        );
      }
      skillNotices(cacheFile, home);
    },
  };
}

function reserveRegistryCheck(cacheFile) {
  const token = randomUUID();
  let reserved = false;
  const saved = updateCache(cacheFile, state => {
    const registry = objectRecord(state.registry) ? state.registry : {};
    const now = Date.now();
    if (cachedVersion(registry, now) || !registryCheckDue(registry, now)) return false;
    state.registry = {
      ...registry,
      attemptedAt: now,
      lease: { token, expiresAt: now + leaseDuration },
    };
    reserved = true;
  });
  return saved && reserved ? token : undefined;
}

function startRegistryWorker(cacheFile, token) {
  try {
    const worker = spawn(process.execPath, [join(__dirname, 'update-notification-worker.js'), cacheFile, token], {
      detached: true,
      stdio: 'ignore',
      windowsHide: true,
    });
    worker.on('error', () => completeRegistryCheck(cacheFile, token, { category: 'launch' }));
    worker.unref();
  } catch {
    completeRegistryCheck(cacheFile, token, { category: 'launch' });
  }
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
  return cachedVersion(readCache(cacheFile).registry, Date.now());
}

function cachedVersion(registry, now) {
  const age = now - registry?.checkedAt;
  return validVersion(registry?.version) && Number.isFinite(registry.checkedAt) && age >= 0 && age < freshness
    ? registry.version
    : undefined;
}

function registryCheckDue(registry, now) {
  if (objectRecord(registry?.lease) && Number.isFinite(registry.lease.expiresAt) && registry.lease.expiresAt > now) return false;
  if (Number.isFinite(registry?.failedAt) && registry.failedAt > now) return true;
  const nextAttemptAt = Number.isFinite(registry?.nextAttemptAt)
    ? registry.nextAttemptAt
    : Number.isFinite(registry?.failedAt)
      ? registry.failedAt + minute
      : undefined;
  return nextAttemptAt === undefined || now >= nextAttemptAt;
}

function completeRegistryCheck(cacheFile, token, result) {
  updateCache(cacheFile, state => {
    const registry = state.registry;
    if (!objectRecord(registry) || registry.lease?.token !== token) return false;
    const now = Date.now();
    const { lease, ...previous } = registry;
    if (result.version) {
      state.registry = { ...previous, version: result.version, checkedAt: now, consecutiveFailures: 0 };
      delete state.registry.failedAt;
      delete state.registry.failureCategory;
      delete state.registry.nextAttemptAt;
    } else {
      const consecutiveFailures =
        (Number.isInteger(previous.consecutiveFailures) && previous.consecutiveFailures > 0
          ? previous.consecutiveFailures
          : Number.isFinite(previous.failedAt)
            ? 1
            : 0) + 1;
      const delays = [1, 5, 15, 60];
      state.registry = {
        ...previous,
        failedAt: now,
        failureCategory: result.category,
        consecutiveFailures,
        nextAttemptAt: now + delays[Math.min(consecutiveFailures - 1, delays.length - 1)] * minute,
      };
    }
  });
}

function readCache(cacheFile) {
  try {
    const parsed = JSON.parse(readFileSync(cacheFile, 'utf8'));
    return objectRecord(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function objectRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function updateCache(cacheFile, update) {
  const lock = `${cacheFile}.lock`;
  let descriptor;
  let temporary;
  try {
    mkdirSync(resolve(cacheFile, '..'), { recursive: true });
    descriptor = openSync(lock, 'wx');
    const state = readCache(cacheFile);
    if (update(state) === false) return true;
    temporary = `${cacheFile}.${process.pid}.tmp`;
    writeFileSync(temporary, JSON.stringify(state));
    renameSync(temporary, cacheFile);
    return true;
  } catch {
    return false;
  } finally {
    if (temporary !== undefined) {
      try {
        rmSync(temporary, { force: true });
      } catch {
        // Notification cache cleanup is advisory.
      }
    }
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
    if (!objectRecord(state.notices)) state.notices = {};
    if (!Number.isFinite(state.notices[key]) || state.notices[key] > Date.now() || Date.now() - state.notices[key] >= day) {
      state.notices[key] = Date.now();
      claimed = true;
    } else {
      return false;
    }
  });
  if (saved && claimed) {
    try {
      writeSync(process.stderr.fd, `${message}\n`);
    } catch {
      // Notice output is advisory.
    }
  }
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

module.exports = { startNotifications, completeRegistryCheck, validVersion };
