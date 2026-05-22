package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;
import org.slf4j.Logger;

record RuntimeLibraryIdentityResolution(
  Optional<RuntimeLibraryIdentity> effectiveIdentity,
  Optional<RuntimeLibraryIdentity> metadataIdentity,
  Optional<RuntimeLibraryIdentity> fileNameIdentity
) {
  static RuntimeLibraryIdentityResolution from(
    Optional<RuntimeLibraryIdentity> metadataIdentity,
    Optional<RuntimeLibraryIdentity> fileNameIdentity
  ) {
    Optional<RuntimeLibraryIdentity> effectiveIdentity = metadataIdentity.or(() -> fileNameIdentity);
    return new RuntimeLibraryIdentityResolution(effectiveIdentity, metadataIdentity, fileNameIdentity);
  }

  void logOverrideIfNeeded(Logger logger, String libraryFileName) {
    metadataIdentity.ifPresent(metadataLibraryIdentity ->
      fileNameIdentity
        .filter(inferredLibraryIdentity -> !metadataLibraryIdentity.equals(inferredLibraryIdentity))
        .ifPresent(inferredLibraryIdentity ->
          logger.debug(
            "Using pom.properties identity {}:{} for '{}' instead of file name inferred identity {}:{}",
            metadataLibraryIdentity.coordinate(),
            metadataLibraryIdentity.version(),
            libraryFileName,
            inferredLibraryIdentity.coordinate(),
            inferredLibraryIdentity.version()
          )
        )
    );
  }
}
