package com.seed4j.cli.bootstrap.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsInstallation;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.infrastructure.primary.JavaRuntimeSelectionReader.JavaRuntimeSelection;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaRuntimeSelectionReaderTest {

  @Test
  void shouldReadStandardRuntimeSelectionFromApplicationService() {
    JavaRuntimeSelectionReader reader = new JavaRuntimeSelectionReader(applicationService(RuntimeSelection.standard()));

    JavaRuntimeSelection runtimeSelection = reader.activeRuntimeSelection();

    assertThat(runtimeSelection.extension()).isFalse();
    assertThat(runtimeSelection.distributionId()).isEmpty();
    assertThat(runtimeSelection.distributionVersion()).isEmpty();
  }

  @Test
  void shouldReadExtensionRuntimeSelectionFromApplicationService() {
    RuntimeDistributionId distributionId = new RuntimeDistributionId("company-extension");
    RuntimeDistributionVersion distributionVersion = new RuntimeDistributionVersion("1.0.0");
    RuntimeSelection extensionSelection = RuntimeSelection.extensionWithoutJar(
      Optional.of(distributionId),
      Optional.of(distributionVersion)
    );
    JavaRuntimeSelectionReader reader = new JavaRuntimeSelectionReader(applicationService(extensionSelection));

    JavaRuntimeSelection runtimeSelection = reader.activeRuntimeSelection();

    assertThat(runtimeSelection.extension()).isTrue();
    assertThat(runtimeSelection.distributionId()).contains("company-extension");
    assertThat(runtimeSelection.distributionVersion()).contains("1.0.0");
  }

  private static RuntimeExtensionApplicationService applicationService(RuntimeSelection runtimeSelection) {
    return new RuntimeExtensionApplicationService(
      new SilentRuntimeExtensionPackageValidator(),
      new SilentRuntimeModeConfigurationRepository(),
      new SilentRuntimeExtensionArtifactsRepository(),
      runtimeSelection
    );
  }

  private static final class SilentRuntimeExtensionPackageValidator implements RuntimeExtensionPackageValidator {

    @Override
    public void validate(RuntimeExtensionJarPath extensionJarPath) {}
  }

  private static final class SilentRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      return new RuntimeModeChangePlan() {
        @Override
        public Path configPath() {
          return Path.of("config.yml");
        }

        @Override
        public void apply() {}
      };
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }
  }

  private static final class SilentRuntimeExtensionArtifactsRepository implements RuntimeExtensionArtifactsRepository {

    @Override
    public boolean activeRuntimePresent() {
      return false;
    }

    @Override
    public RuntimeExtensionArtifactsInstallation install(RuntimeExtensionInstallRequest request) {
      return new RuntimeExtensionArtifactsInstallation(Path.of("extension.jar"), Path.of("metadata.yml"));
    }
  }
}
