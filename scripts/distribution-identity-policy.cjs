const PROPERTY_NAMES = Object.freeze({
  artifactId: 'seed4j.artifact-id',
  channel: 'seed4j.release-channel',
  groupId: 'seed4j.group-id',
  repositoryUrl: 'seed4j.repository-url',
  unavailableModules: 'seed4j.unavailable-modules',
  upstreamCommit: 'seed4j.upstream-commit',
  version: 'seed4j.version',
});

const FILTERED_METADATA = `release-channel=@seed4j.release-channel@
seed4j-dependency-coordinate=@seed4j.group-id@:@seed4j.artifact-id@:@seed4j.version@
seed4j-upstream-commit=@seed4j.upstream-commit@
unavailable-modules=@seed4j.unavailable-modules@
`;

function distributionIdentity(pom) {
  const properties = /<properties>([\s\S]*?)<\/properties>/.exec(pom)?.[1];
  if (!properties) {
    throw new Error('Maven must define one top-level distribution authority.');
  }
  return identity(properties);
}

function identity(properties) {
  return Object.freeze(
    Object.fromEntries(Object.entries(PROPERTY_NAMES).map(([field, property]) => [field, propertyValue(properties, property)])),
  );
}

function propertyValue(properties, name) {
  const matches = [
    ...properties.matchAll(new RegExp(`<${escapeRegularExpression(name)}(?:\\s*/>|>([^<]*)<\\/${escapeRegularExpression(name)}>)`, 'g')),
  ];
  if (matches.length !== 1) {
    throw new Error(`Maven distribution authority must define ${name} exactly once.`);
  }
  return (matches[0][1] ?? '').trim();
}

function validateDistributionBuild({ metadata, pom }) {
  if (metadata !== FILTERED_METADATA) {
    throw new Error('Packaged distribution metadata must contain only Maven authority tokens.');
  }
  for (const element of ['groupId', 'artifactId', 'version']) {
    const property = PROPERTY_NAMES[element === 'groupId' ? 'groupId' : element === 'artifactId' ? 'artifactId' : 'version'];
    const matches = pom.match(new RegExp(`<${element}>\\$\\{${escapeRegularExpression(property)}\\}<\\/${element}>`, 'g'));
    if ((matches ?? []).length !== 2) {
      throw new Error(`Both Seed4J dependencies must use Maven-owned ${property}.`);
    }
  }
  if (
    !/<include>META-INF\/seed4j-cli-distribution\.properties<\/include>/.test(pom)
    || !/<exclude>META-INF\/seed4j-cli-distribution\.properties<\/exclude>/.test(pom)
  ) {
    throw new Error('Distribution metadata must be filtered exactly once by Maven.');
  }
  const currentIdentity = distributionIdentity(pom);
  if (currentIdentity.channel === 'stable') {
    requireStableIdentity(currentIdentity);
    requireNoPersonalSnapshotDeclarations(pom);
  } else if (currentIdentity.channel === 'experimental') {
    requireExperimentalIdentity(currentIdentity);
    requireExperimentalRepository(pom);
  } else {
    throw new Error(`Unsupported distribution release channel '${currentIdentity.channel}'.`);
  }
  return currentIdentity;
}

function requireStableIdentity(identity) {
  if (
    identity.channel !== 'stable'
    || identity.groupId !== 'com.seed4j'
    || identity.artifactId !== 'seed4j'
    || !/^\d+\.\d+\.\d+$/.test(identity.version)
    || identity.repositoryUrl !== 'https://repo.maven.apache.org/maven2'
    || identity.upstreamCommit !== ''
    || identity.unavailableModules !== ''
  ) {
    throw new Error('Stable distribution authority is incomplete or not isolated.');
  }
}

function requireNoPersonalSnapshotDeclarations(pom) {
  if (/io\.github\.renanfranca|seed4j-main-snapshot|central\.sonatype\.com\/repository\/maven-snapshots|<id>experimental<\/id>/.test(pom)) {
    throw new Error('Stable main-bound Maven authority must not declare the personal snapshot channel.');
  }
}

function requireExperimentalIdentity(identity) {
  if (
    identity.channel !== 'experimental'
    || identity.groupId !== 'io.github.renanfranca'
    || identity.artifactId !== 'seed4j-main-snapshot'
    || identity.repositoryUrl !== 'https://central.sonatype.com/repository/maven-snapshots/'
    || identity.unavailableModules !== 'seed4j-extension'
    || !/^[0-9a-f]{40}$/.test(identity.upstreamCommit)
  ) {
    throw new Error('Experimental distribution requires complete full upstream SHA facts.');
  }
  const version = /^\d+\.\d+\.\d+-main\.\d{8}\.\d{6}\.([0-9a-f]{12})-SNAPSHOT$/.exec(identity.version);
  if (!version || !identity.upstreamCommit.startsWith(version[1])) {
    throw new Error('Experimental snapshot version must match the recorded full upstream SHA.');
  }
}

function requireExperimentalRepository(pom) {
  const repositories = [...pom.matchAll(/<repositories>([\s\S]*?)<\/repositories>/g)];
  const repository = repositories[0]?.[1];
  if (
    repositories.length !== 1
    || (repository.match(/<repository>/g) ?? []).length !== 1
    || !/<id>seed4j-main-snapshots<\/id>[\s\S]*?<url>\$\{seed4j\.repository-url\}<\/url>[\s\S]*?<releases>[\s\S]*?<enabled>false<\/enabled>[\s\S]*?<snapshots>[\s\S]*?<enabled>true<\/enabled>/.test(
      repository,
    )
  ) {
    throw new Error('Experimental Maven authority requires one snapshot-only personal repository.');
  }
}

function escapeRegularExpression(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

module.exports = { distributionIdentity, validateDistributionBuild };
