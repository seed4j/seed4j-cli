package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeLibraryIdentityTest {

  @Test
  void shouldParseReleaseVersionFromJarFileName() {
    String libraryFileName = "shared-lib-RELEASE.jar";

    Optional<RuntimeLibraryIdentity> identity = RuntimeLibraryIdentity.fromJarFileName(libraryFileName);

    assertThat(identity).contains(
      new RuntimeLibraryIdentity(new RuntimeLibraryCoordinate("shared-lib"), new RuntimeLibraryVersion("RELEASE"))
    );
  }

  @Test
  void shouldParseVPrefixedVersionFromJarFileName() {
    String libraryFileName = "shared-lib-v1.2.3.jar";

    Optional<RuntimeLibraryIdentity> identity = RuntimeLibraryIdentity.fromJarFileName(libraryFileName);

    assertThat(identity).contains(
      new RuntimeLibraryIdentity(new RuntimeLibraryCoordinate("shared-lib"), new RuntimeLibraryVersion("v1.2.3"))
    );
  }

  @Test
  void shouldReturnEmptyWhenLibraryFileNameIsVeryLargeAndInvalid() {
    String libraryFileName = "a".repeat(100_000) + ".jar";

    Optional<RuntimeLibraryIdentity> identity = RuntimeLibraryIdentity.fromJarFileName(libraryFileName);

    assertThat(identity).isEmpty();
  }
}
