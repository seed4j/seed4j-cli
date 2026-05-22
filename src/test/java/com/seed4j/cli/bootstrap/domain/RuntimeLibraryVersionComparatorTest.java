package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import org.junit.jupiter.api.Test;

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

  @Test
  void shouldReturnUncomparableWhenQualifiedVersionsUseDifferentQualifierFamilies() {
    String extensionVersion = "7.2.12.Final";
    String cliVersion = "7.2.13.CR";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  @Test
  void shouldReturnUncomparableWhenQualifiedVersionIsComparedToReleaseToken() {
    String extensionVersion = "7.2.12.Final";
    String cliVersion = "RELEASE";

    RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison comparison = comparator.compare(extensionVersion, cliVersion);

    assertThat(comparison).isEqualTo(RuntimeLibraryVersionComparator.RuntimeLibraryVersionComparison.UNCOMPARABLE);
  }

  @Test
  void shouldReturnUncomparableWhenNumericSegmentOverflowsIntegerRange() {
    String extensionVersion = "999999999999999999999999999.1";
    String cliVersion = "1.0.0";

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
}
