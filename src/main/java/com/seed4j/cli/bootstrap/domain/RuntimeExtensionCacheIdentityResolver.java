package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

@FunctionalInterface
public interface RuntimeExtensionCacheIdentityResolver {
  RuntimeExtensionCacheIdentity resolve(Path extensionJarPath);
}
