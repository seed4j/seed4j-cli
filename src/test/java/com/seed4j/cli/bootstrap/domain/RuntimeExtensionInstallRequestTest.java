package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionInstallRequestTest {

  @Test
  void shouldExposeDomainValueObjectsAndResolveJarPathAsPath() {
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(
      new RuntimeExtensionJarPath("runtime/company-extension.jar"),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );

    assertThat(request.extensionJarPath().path()).isEqualTo("runtime/company-extension.jar");
    assertThat(request.extensionJarPath().filePath()).isEqualTo(Path.of("runtime/company-extension.jar"));
    assertThat(request.distributionId().id()).isEqualTo("company-extension");
    assertThat(request.distributionVersion().version()).isEqualTo("1.0.0");
  }

  @Test
  void shouldFailWhenJarPathIsBlank() {
    assertThatThrownBy(() -> new RuntimeExtensionJarPath("  "))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"path\"");
  }

  @Test
  void shouldFailWhenDistributionIdIsBlank() {
    assertThatThrownBy(() -> new RuntimeDistributionId("  "))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"id\"");
  }

  @Test
  void shouldFailWhenDistributionVersionIsBlank() {
    assertThatThrownBy(() -> new RuntimeDistributionVersion("  "))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"version\"");
  }

  @Test
  void shouldFailWhenJarPathCannotBeConvertedToPath() {
    assertThatThrownBy(() -> new RuntimeExtensionJarPath("\u0000invalid.jar")).isExactlyInstanceOf(InvalidPathException.class);
  }

  @Test
  void shouldFailWhenRequestContainsNullValueObjects() {
    RuntimeExtensionJarPath extensionJarPath = new RuntimeExtensionJarPath("runtime/company-extension.jar");
    RuntimeDistributionId distributionId = new RuntimeDistributionId("company-extension");
    RuntimeDistributionVersion distributionVersion = new RuntimeDistributionVersion("1.0.0");

    assertThatThrownBy(() -> new RuntimeExtensionInstallRequest(null, distributionId, distributionVersion))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"extensionJarPath\"");
    assertThatThrownBy(() -> new RuntimeExtensionInstallRequest(extensionJarPath, null, distributionVersion))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"distributionId\"");
    assertThatThrownBy(() -> new RuntimeExtensionInstallRequest(extensionJarPath, distributionId, null))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"distributionVersion\"");
  }
}
