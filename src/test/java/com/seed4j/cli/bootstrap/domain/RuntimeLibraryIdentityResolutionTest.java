package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeLibraryIdentityResolutionTest {

  @Test
  void shouldPreferMetadataIdentityWhenMetadataIsPresent() {
    RuntimeLibraryIdentity metadataIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0");
    RuntimeLibraryIdentity fileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0");
    RuntimeLibraryIdentityResolution resolution = RuntimeLibraryIdentityResolution.from(
      Optional.of(metadataIdentity),
      Optional.of(fileNameIdentity)
    );

    assertThat(resolution.effectiveIdentity()).contains(metadataIdentity);
  }

  @Test
  void shouldUseFileNameIdentityWhenMetadataIsMissing() {
    RuntimeLibraryIdentity fileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0");
    RuntimeLibraryIdentityResolution resolution = RuntimeLibraryIdentityResolution.from(Optional.empty(), Optional.of(fileNameIdentity));

    assertThat(resolution.effectiveIdentity()).contains(fileNameIdentity);
  }
}
