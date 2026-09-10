const releasePolicy = require('./release-policy.cjs');

async function analyzeCommits(_pluginConfiguration, context) {
  const { analyzeCommits: analyzeConventionalCommits } = await import('@semantic-release/commit-analyzer');
  const release = await analyzeConventionalCommits(releasePolicy, context);

  if (release !== null) {
    return release;
  }

  return manualReleaseRequested(context) && context.commits.length > 0 ? 'patch' : null;
}

function manualReleaseRequested(context) {
  return context.env?.SEED4J_MANUAL_RELEASE === 'true';
}

module.exports = { analyzeCommits };
