const { forNpm } = require('./release-provenance-context.cjs');

async function verifyConditions(pluginConfiguration, context) {
  return delegate('verifyConditions', pluginConfiguration, context);
}

async function delegate(hook, pluginConfiguration, context) {
  const npmPlugin = await import('@semantic-release/npm');

  return npmPlugin[hook](pluginConfiguration, forNpm(context));
}

async function prepare(pluginConfiguration, context) {
  return delegate('prepare', pluginConfiguration, context);
}

async function publish(pluginConfiguration, context) {
  return delegate('publish', pluginConfiguration, context);
}

async function addChannel(pluginConfiguration, context) {
  return delegate('addChannel', pluginConfiguration, context);
}

module.exports = { addChannel, prepare, publish, verifyConditions };
