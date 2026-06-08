package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeSelectionTest {

  @Test
  void shouldCreateStandardRuntimeSelection() {
    RuntimeSelection runtimeSelection = RuntimeSelection.standard();

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.STANDARD);
    assertThat(runtimeSelection.extensionMode()).isFalse();
    assertThat(runtimeSelection.extensionJarPath()).isEmpty();
    assertThat(runtimeSelection.distributionId()).isEmpty();
    assertThat(runtimeSelection.distributionVersion()).isEmpty();
  }

  @Test
  void shouldCreateExtensionRuntimeSelectionWithJarAndDistribution() {
    RuntimeExtensionJarPath extensionJarPath = new RuntimeExtensionJarPath(Path.of("company-extension.jar"));
    RuntimeDistributionId distributionId = new RuntimeDistributionId("company-extension");
    RuntimeDistributionVersion distributionVersion = new RuntimeDistributionVersion("1.0.0");

    RuntimeSelection runtimeSelection = RuntimeSelection.extension(extensionJarPath, distributionId, distributionVersion);

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionMode()).isTrue();
    assertThat(runtimeSelection.extensionJarPath()).contains(extensionJarPath);
    assertThat(runtimeSelection.distributionId()).contains(distributionId);
    assertThat(runtimeSelection.distributionVersion()).contains(distributionVersion);
  }

  @Test
  void shouldCreateExtensionRuntimeSelectionWithoutJarForChildProcess() {
    RuntimeDistributionId distributionId = new RuntimeDistributionId("company-extension");
    RuntimeDistributionVersion distributionVersion = new RuntimeDistributionVersion("1.0.0");

    RuntimeSelection runtimeSelection = RuntimeSelection.extensionWithoutJar(Optional.of(distributionId), Optional.of(distributionVersion));

    assertThat(runtimeSelection.mode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeSelection.extensionMode()).isTrue();
    assertThat(runtimeSelection.extensionJarPath()).isEmpty();
    assertThat(runtimeSelection.distributionId()).contains(distributionId);
    assertThat(runtimeSelection.distributionVersion()).contains(distributionVersion);
  }
}
