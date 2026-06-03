package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;

public record RuntimeLibraryIdentityResolution(
  Optional<RuntimeLibraryIdentity> effectiveIdentity,
  Optional<RuntimeLibraryIdentity> metadataIdentity,
  Optional<RuntimeLibraryIdentity> fileNameIdentity
) {
  public static RuntimeLibraryIdentityResolution from(
    Optional<RuntimeLibraryIdentity> metadataIdentity,
    Optional<RuntimeLibraryIdentity> fileNameIdentity
  ) {
    Optional<RuntimeLibraryIdentity> effectiveIdentity = metadataIdentity.or(() -> fileNameIdentity);
    return new RuntimeLibraryIdentityResolution(effectiveIdentity, metadataIdentity, fileNameIdentity);
  }
}
