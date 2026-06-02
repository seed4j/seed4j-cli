package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstaller;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionModeEnabler;
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Service;

@UnitTest
class RuntimeExtensionSpringConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
    RuntimeExtensionSpringConfiguration.class,
    RuntimeExtensionApplicationService.class
  );

  @Test
  void shouldCreateRuntimeExtensionApplicationServiceUsingFilesystemSecondaryAdapters() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-extension-config-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(
      RuntimeExtensionJarPath.from(extensionJarPath.toString()),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );

    contextRunner
      .withPropertyValues("user.home=" + userHome)
      .run(context -> {
        RuntimeExtensionApplicationService runtimeExtensionApplicationService = context.getBean(RuntimeExtensionApplicationService.class);

        RuntimeExtensionInstallResult installResult = runtimeExtensionApplicationService.install(request);

        assertThat(context).hasSingleBean(RuntimeExtensionConfiguration.class);
        assertThat(context).hasSingleBean(RuntimeModeConfigurationRepository.class);
        assertThat(context).hasSingleBean(RuntimeExtensionInstaller.class);
        assertThat(context).hasSingleBean(RuntimeExtensionModeEnabler.class);
        assertThat(installResult.configPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/config.yml"));
        assertThat(installResult.extensionJarPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar"));
        assertThat(installResult.metadataPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml"));
        assertThat(installResult.runtimeReplaced()).isFalse();
      });
  }

  @Test
  void shouldCreateARuntimeModeConfigurationRepositoryScopedToTheInformedUserHome() throws IOException {
    Path firstUserHome = Files.createTempDirectory("seed4j-cli-runtime-extension-config-first-home-");
    Path secondUserHome = Files.createTempDirectory("seed4j-cli-runtime-extension-config-second-home-");

    RuntimeModeChangePlan firstModeChangePlan = modeChangePlanFor(firstUserHome);
    RuntimeModeChangePlan secondModeChangePlan = modeChangePlanFor(secondUserHome);

    assertThat(firstModeChangePlan.configPath()).isEqualTo(firstUserHome.resolve(".config/seed4j-cli/config.yml"));
    assertThat(secondModeChangePlan.configPath()).isEqualTo(secondUserHome.resolve(".config/seed4j-cli/config.yml"));
  }

  @Test
  void shouldExposeApplicationServiceAsSpringService() {
    assertThat(RuntimeExtensionApplicationService.class).hasAnnotation(Service.class);
  }

  private RuntimeModeChangePlan modeChangePlanFor(Path userHome) {
    RuntimeModeChangePlan[] modeChangePlan = new RuntimeModeChangePlan[1];

    contextRunner
      .withPropertyValues("user.home=" + userHome)
      .run(context -> {
        RuntimeModeConfigurationRepository runtimeModeConfigurationRepository = context.getBean(RuntimeModeConfigurationRepository.class);

        modeChangePlan[0] = runtimeModeConfigurationRepository.prepareModeChange(RuntimeMode.EXTENSION);
      });

    return modeChangePlan[0];
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
}
