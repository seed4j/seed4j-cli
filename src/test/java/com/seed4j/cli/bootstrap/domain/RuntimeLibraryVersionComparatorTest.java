package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.seed4j.cli.UnitTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class RuntimeLibraryVersionComparatorTest {

  private final RuntimeLibraryVersionComparator comparator = new RuntimeLibraryVersionComparator();

  @Test
  void shouldReturnSameVersionWhenVersionsMatchAfterNormalizingVPrefix() {
    String extensionVersion = "v1.2.0";
    String cliVersion = "1.2.0";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.SAME_VERSION);
  }

  @Test
  void shouldReturnExtensionOlderWhenBothVersionsAreNumericAndCliIsNewer() {
    String extensionVersion = "1.2.3";
    String cliVersion = "1.2.4";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.EXTENSION_OLDER);
  }

  @Test
  void shouldReturnExtensionNewerWhenBothVersionsAreNumericAndExtensionIsNewer() {
    String extensionVersion = "2.0.0";
    String cliVersion = "1.9.9";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.EXTENSION_NEWER);
  }

  @Test
  void shouldReturnSameVersionWhenNumericVersionsDifferOnlyByTrailingZeroSegments() {
    String extensionVersion = "1.2";
    String cliVersion = "1.2.0.0";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.SAME_VERSION);
  }

  @Test
  void shouldCompareQualifiedNumericVersionsWhenQualifierFamilyMatchesIgnoringCase() {
    String extensionVersion = "7.2.0.Final";
    String cliVersion = "7.2.12.final";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.EXTENSION_OLDER);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("uncomparableVersions")
  void shouldReturnUncomparableForIncompatibleVersions(String scenario, String extensionVersion, String cliVersion) {
    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  @Test
  void shouldReturnUncomparableWhenNumericVersionIsVeryLargeAndMalformed() {
    String extensionVersion = "1.".repeat(2500);
    String cliVersion = "1.0.0";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  @Test
  void shouldReturnUncomparableWhenQualifiedVersionIsVeryLargeAndMalformed() {
    String extensionVersion = "1.".repeat(2500) + "1-";
    String cliVersion = "1.0.0-final";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  private static Stream<Arguments> uncomparableVersions() {
    return Stream.of(
      arguments("different qualifier families", "7.2.12.Final", "7.2.13.CR"),
      arguments("qualified version vs RELEASE token", "7.2.12.Final", "RELEASE"),
      arguments("numeric segment overflow", "999999999999999999999999999.1", "1.0.0")
    );
  }
}
