package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class CliRuntimeLibraryIndexTest {

  @Test
  void shouldBuildFileNamesAndVersionsByCoordinateFromCliLibraries() {
    Set<RuntimeLibraryEntry> cliLibraries = Set.of(
      new RuntimeLibraryEntry(
        "logback-classic-1.5.32.jar",
        Optional.of(new RuntimeLibraryIdentity("ch.qos.logback:logback-classic", "1.5.32"))
      ),
      new RuntimeLibraryEntry("shared-lib-2.1.0.jar", Optional.of(new RuntimeLibraryIdentity("com.acme:shared-lib", "2.1.0"))),
      RuntimeLibraryEntry.fromFileName("bundle-all.jar")
    );

    CliRuntimeLibraryIndex libraryIndex = CliRuntimeLibraryIndex.from(cliLibraries);

    assertThat(libraryIndex.fileNames()).containsExactlyInAnyOrder("logback-classic-1.5.32.jar", "shared-lib-2.1.0.jar", "bundle-all.jar");
    assertThat(libraryIndex.versionsByCoordinate())
      .containsEntry("ch.qos.logback:logback-classic", Set.of("1.5.32"))
      .containsEntry("com.acme:shared-lib", Set.of("2.1.0"));
  }
}
