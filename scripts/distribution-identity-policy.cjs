const PROPERTY_NAMES = Object.freeze({
  artifactId: 'seed4j.artifact-id',
  channel: 'seed4j.release-channel',
  groupId: 'seed4j.group-id',
  repositoryUrl: 'seed4j.repository-url',
  unavailableModules: 'seed4j.unavailable-modules',
  version: 'seed4j.version',
});

const FILTERED_METADATA = `release-channel=@seed4j.release-channel@
seed4j-dependency-coordinate=@seed4j.group-id@:@seed4j.artifact-id@:@seed4j.version@
unavailable-modules=@seed4j.unavailable-modules@
`;

function distributionIdentity(pom) {
  pom = withoutComments(pom);
  const properties = /<properties>([\s\S]*?)<\/properties>/.exec(pom)?.[1];
  if (!properties) {
    throw new Error('Maven must define one top-level distribution authority.');
  }
  const currentIdentity = identity(properties);
  if (currentIdentity.channel === 'experimental') {
    return Object.freeze({ ...currentIdentity, upstreamCommit: requireExperimentalIdentity(currentIdentity) });
  }
  return currentIdentity;
}

function identity(properties) {
  return Object.freeze({
    ...Object.fromEntries(Object.entries(PROPERTY_NAMES).map(([field, property]) => [field, propertyValue(properties, property)])),
    upstreamCommit: '',
  });
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
  pom = withoutComments(pom);
  if (metadata !== FILTERED_METADATA) {
    throw new Error('Packaged distribution metadata must contain only Maven authority tokens.');
  }
  if (/<seed4j\.upstream-commit(?:\s*\/>|>)/.test(pom)) {
    throw new Error('Maven distribution authority must not define legacy upstream commit.');
  }
  if (
    !/<include>META-INF\/seed4j-cli-distribution\.properties<\/include>/.test(pom)
    || !/<exclude>META-INF\/seed4j-cli-distribution\.properties<\/exclude>/.test(pom)
  ) {
    throw new Error('Distribution metadata must be filtered exactly once by Maven.');
  }
  const currentIdentity = distributionIdentity(pom);
  let validatedIdentity = currentIdentity;
  if (currentIdentity.channel === 'stable') {
    requireStableIdentity(currentIdentity);
    requireNoPersonalSnapshotDeclarations(pom);
  } else if (currentIdentity.channel === 'experimental') {
    validatedIdentity = Object.freeze({ ...currentIdentity, upstreamCommit: requireExperimentalIdentity(currentIdentity) });
    requireExperimentalRepository(pom);
  } else {
    throw new Error(`Unsupported distribution release channel '${currentIdentity.channel}'.`);
  }
  requireSeed4JDependencies(pom);
  return validatedIdentity;
}

function withoutComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, '');
}

function requireSeed4JDependencies(pom) {
  const expectedDependencies = [
    { role: 'engine', artifactId: '${seed4j.artifact-id}', scope: 'compile' },
    { role: 'core modules', artifactId: 'seed4j-core-modules', scope: 'compile' },
    { role: 'test support', artifactId: 'seed4j-test', scope: 'test' },
  ];
  const project = childElements(pom, 'project')[0] ?? '';
  const dependencies = childElements(project, 'dependencies')
    .flatMap(dependencies => childElements(dependencies, 'dependency'))
    .map(dependency =>
      Object.fromEntries(
        ['groupId', 'artifactId', 'version', 'scope', 'type', 'classifier'].map(field => [field, childElements(dependency, field)]),
      ),
    );
  for (const expected of expectedDependencies) {
    requireDependency(
      dependencies.filter(dependency => dependency.artifactId.includes(expected.artifactId)),
      expected,
    );
  }
  const seed4jGroups = ['${seed4j.group-id}', 'com.seed4j', 'io.github.renanfranca'];
  const seed4jDependencies = dependencies.filter(dependency => dependency.groupId.some(group => seed4jGroups.includes(group)));
  if (seed4jDependencies.length !== expectedDependencies.length) {
    throw new Error('Only the engine, core modules and test support may be declared as direct Seed4J dependencies.');
  }
}

function childElements(xml, name) {
  const elements = [];
  let depth = 0;
  let start = 0;
  let elementName = '';
  for (const tag of xml.matchAll(/<\/?([\w.:-]+)\b[^>]*>/g)) {
    const closing = tag[0].startsWith('</');
    const empty = tag[0].endsWith('/>');
    if (!closing && depth === 0) {
      start = tag.index + tag[0].length;
      elementName = tag[1];
    }
    if (!empty) {
      depth += closing ? -1 : 1;
    }
    if (depth === 0 && elementName === name) {
      elements.push(empty ? '' : xml.slice(start, tag.index).trim());
    }
  }
  return elements;
}

function requireDependency(dependencies, { role, artifactId, scope }) {
  if (dependencies.length !== 1) {
    throw new Error(`Seed4J ${role} dependency artifactId must be '${artifactId}' and declared exactly once in project dependencies.`);
  }
  const dependency = dependencies[0];
  for (const [field, expected, defaultValue] of [
    ['groupId', '${seed4j.group-id}'],
    ['artifactId', artifactId],
    ['version', '${seed4j.version}'],
    ['type', 'jar', 'jar'],
    ['classifier', '', ''],
    ['scope', scope, 'compile'],
  ]) {
    const values = dependency[field];
    const value = values.length === 0 ? defaultValue : values[0];
    if (values.length > 1 || value !== expected) {
      throw new Error(`Seed4J ${role} dependency ${field} must be '${expected}'${defaultValue === undefined ? ' exactly once' : ''}.`);
    }
  }
}

function requireStableIdentity(identity) {
  if (
    identity.channel !== 'stable'
    || identity.groupId !== 'com.seed4j'
    || identity.artifactId !== 'seed4j-engine'
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
    || identity.artifactId !== 'seed4j-engine'
    || identity.repositoryUrl !== 'https://central.sonatype.com/repository/maven-snapshots/'
    || identity.unavailableModules !== 'seed4j-extension'
  ) {
    throw new Error('Experimental distribution requires complete full upstream SHA facts.');
  }
  const fullVersion = /^\d+\.\d+\.\d+-main\.\d{8}\.\d{6}\.([0-9a-f]{40})-SNAPSHOT$/.exec(identity.version);
  if (!fullVersion) {
    throw new Error('Experimental snapshot version must contain a full upstream SHA.');
  }
  return fullVersion[1];
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
