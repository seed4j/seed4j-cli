const { completeRegistryCheck } = require('./update-notifications.js');

const [cacheFile, token] = process.argv.slice(2);
const deadline = 5000;
const safetyTimer = setTimeout(() => process.exit(0), 10000);
const controller = new AbortController();
let requestTimer;

async function check() {
  try {
    const tags = await Promise.race([
      fetch('https://registry.npmjs.org/-/package/seed4j-cli/dist-tags', { signal: controller.signal }).then(async response => {
        if (!response.ok) throw { category: 'http' };
        try {
          return await response.json();
        } catch (error) {
          throw { category: 'invalid-response' };
        }
      }),
      new Promise((_, reject) => {
        requestTimer = setTimeout(() => {
          controller.abort();
          reject({ category: 'timeout' });
        }, deadline);
      }),
    ]);
    if (typeof tags?.experimental !== 'string' || !/^\d+\.\d+\.\d+-experimental\.\d+$/.test(tags.experimental)) {
      throw { category: 'invalid-response' };
    }
    completeRegistryCheck(cacheFile, token, { version: tags.experimental });
  } catch (error) {
    completeRegistryCheck(cacheFile, token, { category: error?.category ?? 'network' });
  } finally {
    clearTimeout(requestTimer);
    clearTimeout(safetyTimer);
  }
}

check();
