package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionMissingLibrariesSelectorTest {

  @Test
  void shouldReturnOnlyExtensionLibrariesThatAreMissingFromCliPreservingExtensionOrder() {
    List<String> extensionLibraries = List.of("shared-lib-1.0.0.jar", "missing-lib-2.0.0.jar", "another-missing-lib-3.1.0.jar");
    Set<String> cliLibraries = Set.of("shared-lib-1.0.0.jar", "seed4j-core-9.9.9.jar");

    List<String> missingLibraries = new RuntimeExtensionMissingLibrariesSelector().select(extensionLibraries, cliLibraries);

    assertThat(missingLibraries).containsExactly("missing-lib-2.0.0.jar", "another-missing-lib-3.1.0.jar");
  }

  // [TEST] shouldReturnEmptyWhenExtensionLibrariesAreAlreadyProvidedByCli
}
