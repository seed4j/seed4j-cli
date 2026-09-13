const SHA_PATTERN = /^[0-9a-f]{40}$/;

function forNpm(context) {
  const environment = context.env;
  const qualifiedReference = `refs/heads/${environment.RELEASE_BRANCH}`;

  if (environment.GITHUB_REF !== qualifiedReference || environment.GITHUB_SHA !== environment.QUALIFIED_SHA) {
    throw new Error('Semantic release is not bound to the qualified release target.');
  }
  if (environment.PROVENANCE_GITHUB_REF !== 'refs/heads/main' || !SHA_PATTERN.test(environment.PROVENANCE_GITHUB_SHA ?? '')) {
    throw new Error('GitHub Actions provenance identity is invalid.');
  }

  return {
    ...context,
    env: {
      ...environment,
      GITHUB_REF: environment.PROVENANCE_GITHUB_REF,
      GITHUB_SHA: environment.PROVENANCE_GITHUB_SHA,
    },
  };
}

module.exports = { forNpm };
