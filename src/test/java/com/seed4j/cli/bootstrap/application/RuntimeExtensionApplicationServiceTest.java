package com.seed4j.cli.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeChangePlan;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionApplicationServiceTest {

  @Test
  void shouldInstallRuntimeExtensionUsingRepositoriesProvidedToApplicationServiceConstructor() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-extension-app-service-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path expectedConfigPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      expectedConfigPath
    );
    RecordingRuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository = new RecordingRuntimeExtensionArtifactsRepository(
      false
    );
    RuntimeExtensionApplicationService service = new RuntimeExtensionApplicationService(
      userHome,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(
      new RuntimeExtensionJarPath(extensionJarPath.toString()),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );

    RuntimeExtensionInstallResult installResult = service.install(request);

    assertThat(runtimeModeConfigurationRepository.prepareCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPreparedMode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeModeConfigurationRepository.applyCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.activeRuntimePresentCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.installCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.lastInstallRequest()).isEqualTo(request);
    assertThat(runtimeExtensionArtifactsRepository.lastRuntimeConfiguration()).isEqualTo(
      RuntimeExtensionConfiguration.withDefaultPaths(userHome)
    );
    assertThat(installResult.extensionJarPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar"));
    assertThat(installResult.metadataPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml"));
    assertThat(installResult.configPath()).isEqualTo(expectedConfigPath);
    assertThat(installResult.runtimeReplaced()).isFalse();
  }

  @Test
  void shouldPropagateRuntimeConfigurationErrorsRaisedByTheRuntimeExtensionInstallationFlow() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-extension-app-service-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    InvalidRuntimeConfigurationException runtimeConfigurationException = new InvalidRuntimeConfigurationException("invalid runtime config");
    ThrowingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new ThrowingRuntimeModeConfigurationRepository(
      runtimeConfigurationException
    );
    RecordingRuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository = new RecordingRuntimeExtensionArtifactsRepository(
      false
    );
    RuntimeExtensionApplicationService service = new RuntimeExtensionApplicationService(
      userHome,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(
      new RuntimeExtensionJarPath(extensionJarPath.toString()),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );

    assertThatThrownBy(() -> service.install(request)).isSameAs(runtimeConfigurationException);
    assertThat(runtimeModeConfigurationRepository.prepareCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.activeRuntimePresentCalls()).isZero();
    assertThat(runtimeExtensionArtifactsRepository.installCalls()).isZero();
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
    }

    return jarPath;
  }

  private static final class RecordingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final Path configPath;
    private RuntimeMode lastPreparedMode;
    private int prepareCalls;
    private int applyCalls;

    private RecordingRuntimeModeConfigurationRepository(Path configPath) {
      this.configPath = configPath;
    }

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      prepareCalls++;
      lastPreparedMode = targetMode;
      return new RuntimeModeChangePlan() {
        @Override
        public Path configPath() {
          return configPath;
        }

        @Override
        public void apply() {
          applyCalls++;
        }
      };
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }

    private int prepareCalls() {
      return prepareCalls;
    }

    private RuntimeMode lastPreparedMode() {
      return lastPreparedMode;
    }

    private int applyCalls() {
      return applyCalls;
    }
  }

  private static final class RecordingRuntimeExtensionArtifactsRepository implements RuntimeExtensionArtifactsRepository {

    private final boolean activeRuntimePresent;
    private RuntimeExtensionInstallRequest lastInstallRequest;
    private RuntimeExtensionConfiguration lastRuntimeConfiguration;
    private int activeRuntimePresentCalls;
    private int installCalls;

    private RecordingRuntimeExtensionArtifactsRepository(boolean activeRuntimePresent) {
      this.activeRuntimePresent = activeRuntimePresent;
    }

    @Override
    public boolean activeRuntimePresent(RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
      activeRuntimePresentCalls++;
      return activeRuntimePresent;
    }

    @Override
    public void install(RuntimeExtensionInstallRequest request, RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
      installCalls++;
      lastInstallRequest = request;
      lastRuntimeConfiguration = runtimeExtensionConfiguration;
    }

    private RuntimeExtensionInstallRequest lastInstallRequest() {
      return lastInstallRequest;
    }

    private RuntimeExtensionConfiguration lastRuntimeConfiguration() {
      return lastRuntimeConfiguration;
    }

    private int activeRuntimePresentCalls() {
      return activeRuntimePresentCalls;
    }

    private int installCalls() {
      return installCalls;
    }
  }

  private static final class ThrowingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final InvalidRuntimeConfigurationException runtimeConfigurationException;
    private int prepareCalls;

    private ThrowingRuntimeModeConfigurationRepository(InvalidRuntimeConfigurationException runtimeConfigurationException) {
      this.runtimeConfigurationException = runtimeConfigurationException;
    }

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      prepareCalls++;
      throw runtimeConfigurationException;
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }

    private int prepareCalls() {
      return prepareCalls;
    }
  }
}
