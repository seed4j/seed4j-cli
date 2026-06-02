package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.error.YAMLException;

@UnitTest
class RuntimeExtensionInstallerTest {

  private static final String DISTRIBUTION_ID = "company-extension";
  private static final String DISTRIBUTION_VERSION = "1.0.0";

  @Test
  void shouldUsePreparedModeChangePlanToPersistExtensionModeAfterRuntimeArtifactInstallation() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();
    InvocationOrder invocationOrder = new InvocationOrder();
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      userHome.resolve(".config/seed4j-cli/config.yml"),
      new RuntimeModeConfigurationDocument(new LinkedHashMap<>()),
      invocationOrder
    );
    RecordingRuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository = new RecordingRuntimeExtensionArtifactsRepository(
      invocationOrder
    );
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(runtimeModeConfigurationRepository.prepareCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPreparedMode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeModeConfigurationRepository.applyCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.applyCalledAfterInstall()).isTrue();
    assertThat(runtimeExtensionArtifactsRepository.installCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.lastInstallRequest()).isEqualTo(request);
    assertThat(runtimeExtensionArtifactsRepository.lastRuntimeConfiguration()).isEqualTo(runtimeExtensionConfiguration);
    assertThat(installResult.configPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/config.yml"));
  }

  @Test
  void shouldCreateConfigFileWhenMissingAndInstallExtensionRuntimeUsingFileSystemSecondaryRepositories() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(configPath).exists();
    assertThat(Files.readString(configPath)).contains("mode: extension");
    assertThat(runtimeJarPath).exists();
    assertThat(metadataPath).exists();
    assertThat(installResult.extensionJarPath()).isEqualTo(runtimeJarPath);
    assertThat(installResult.metadataPath()).isEqualTo(metadataPath);
    assertThat(installResult.configPath()).isEqualTo(configPath);
    assertThat(installResult.runtimeReplaced()).isFalse();
  }

  @Test
  void shouldFailWhenConfigFileExistsButIsInvalidWithoutMutatingRuntimeArtifacts() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "seed4j: [broken");
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not read ~/.config/seed4j-cli/config.yml.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(YAMLException.class);

    assertThat(runtimeJarPath).doesNotExist();
    assertThat(metadataPath).doesNotExist();
    assertThat(Files.readString(configPath)).isEqualTo("seed4j: [broken");
  }

  @Test
  void shouldPreserveExistingConfigKeysWhenConfigIsValid() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(configPath.getParent());
    Files.writeString(
      configPath,
      """
      seed4j:
        runtime:
          mode: standard
        hidden-resources:
          slugs:
            - gradle-java
      custom:
        enabled: true
      """
    );
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    installer.install(request);

    String persistedConfiguration = Files.readString(configPath);
    assertThat(persistedConfiguration)
      .contains("mode: extension")
      .contains("hidden-resources")
      .contains("gradle-java")
      .contains("custom:")
      .contains("enabled: true");
  }

  @Test
  void shouldOverwriteRuntimeArtifactsWhenActiveExtensionAlreadyExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(runtimeJarPath.getParent());
    createFatJar(runtimeJarPath, "BOOT-INF/classes/com/company/Legacy.class", new byte[] { 1 });
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: legacy-extension
        version: 0.9.0
      """
    );
    Path extensionJarPath = createFatJar(
      userHome.resolve("company-extension.jar"),
      "BOOT-INF/classes/com/company/New.class",
      new byte[] { 2, 3 }
    );
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(installResult.runtimeReplaced()).isTrue();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID);
    assertThat(Files.readString(metadataPath)).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldMarkRuntimeAsReplacedWhenOnlyMetadataAlreadyExists() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Files.createDirectories(metadataPath.getParent());
    Files.writeString(
      metadataPath,
      """
      distribution:
        id: legacy-extension
        version: 0.9.0
      """
    );
    Path extensionJarPath = createFatJar(
      userHome.resolve("company-extension.jar"),
      "BOOT-INF/classes/com/company/New.class",
      new byte[] { 2, 3 }
    );
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(installResult.runtimeReplaced()).isTrue();
    assertThat(runtimeJarPath).exists();
    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(Files.readString(metadataPath)).contains("id: " + DISTRIBUTION_ID);
    assertThat(Files.readString(metadataPath)).contains("version: " + DISTRIBUTION_VERSION);
  }

  @Test
  void shouldFailWhenJarDoesNotContainBootInfClasses() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFlatJar(userHome.resolve("company-extension.jar"));
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime jar file")
      .hasMessageContaining("BOOT-INF/classes");

    assertThat(runtimeJarPath).doesNotExist();
    assertThat(metadataPath).doesNotExist();
    assertThat(configPath).doesNotExist();
  }

  @Test
  void shouldFailWhenRuntimeJarTargetIsNonEmptyDirectoryAndDeleteTemporaryJarFile() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(runtimeJarPath);
    Files.writeString(runtimeJarPath.resolve("occupied.txt"), "existing");
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not install runtime extension.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);

    assertThat(runtimeJarPath).isDirectory();
    assertThat(runtimeJarPath.resolve("occupied.txt")).exists();
    assertThat(metadataPath).doesNotExist();
    assertThat(configPath).doesNotExist();
    assertThat(filesWithPrefix(runtimeJarPath.getParent(), ".extension.jar.tmp-")).isEmpty();
  }

  @Test
  void shouldFailWhenMetadataTargetIsNonEmptyDirectoryAndDeleteTemporaryMetadataFile() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    Path runtimeJarPath = userHome.resolve(".config/seed4j-cli/runtime/active/extension.jar");
    Path metadataPath = userHome.resolve(".config/seed4j-cli/runtime/active/metadata.yml");
    Path configPath = userHome.resolve(".config/seed4j-cli/config.yml");
    Files.createDirectories(metadataPath);
    Files.writeString(metadataPath.resolve("occupied.txt"), "existing");
    RuntimeExtensionInstaller installer = installer(userHome);
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    assertThatThrownBy(() -> installer.install(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Could not install runtime extension.")
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);

    assertThat(Files.readAllBytes(runtimeJarPath)).isEqualTo(Files.readAllBytes(extensionJarPath));
    assertThat(metadataPath).isDirectory();
    assertThat(metadataPath.resolve("occupied.txt")).exists();
    assertThat(configPath).doesNotExist();
    assertThat(filesWithPrefix(metadataPath.getParent(), ".metadata.yml.tmp-")).isEmpty();
  }

  @Test
  void shouldOrchestrateRuntimeArtifactInstallationAndModePersistenceThroughInjectedRepositories() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-runtime-installer-");
    Path extensionJarPath = createFatJar(userHome.resolve("company-extension.jar"));
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    RecordingRuntimeModeConfigurationRepository runtimeModeConfigurationRepository = new RecordingRuntimeModeConfigurationRepository(
      userHome.resolve(".config/seed4j-cli/config.yml"),
      currentConfiguration
    );
    RecordingRuntimeExtensionArtifactsRepository runtimeExtensionArtifactsRepository = new RecordingRuntimeExtensionArtifactsRepository();
    RuntimeExtensionInstaller installer = new RuntimeExtensionInstaller(
      runtimeExtensionConfiguration,
      runtimeModeConfigurationRepository,
      runtimeExtensionArtifactsRepository
    );
    RuntimeExtensionInstallRequest request = installRequest(extensionJarPath);

    RuntimeExtensionInstallResult installResult = installer.install(request);

    assertThat(runtimeModeConfigurationRepository.prepareCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.lastPreparedMode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeModeConfigurationRepository.applyCalls()).isEqualTo(1);
    assertThat(runtimeModeConfigurationRepository.applyCalledAfterInstall()).isTrue();
    assertThat(runtimeModeConfigurationRepository.lastPersistedConfiguration()).isEqualTo(currentConfiguration);
    assertThat(runtimeModeConfigurationRepository.lastPersistedMode()).isEqualTo(RuntimeMode.EXTENSION);
    assertThat(runtimeExtensionArtifactsRepository.installCalls()).isEqualTo(1);
    assertThat(runtimeExtensionArtifactsRepository.lastInstallRequest()).isEqualTo(request);
    assertThat(runtimeExtensionArtifactsRepository.lastRuntimeConfiguration()).isEqualTo(runtimeExtensionConfiguration);
    assertThat(installResult.configPath()).isEqualTo(userHome.resolve(".config/seed4j-cli/config.yml"));
    assertThat(installResult.extensionJarPath()).isEqualTo(runtimeExtensionConfiguration.jarPath());
    assertThat(installResult.metadataPath()).isEqualTo(runtimeExtensionConfiguration.metadataPath());
  }

  private static RuntimeExtensionInstallRequest installRequest(Path extensionJarPath) {
    return new RuntimeExtensionInstallRequest(
      RuntimeExtensionJarPath.from(extensionJarPath.toString()),
      new RuntimeDistributionId(DISTRIBUTION_ID),
      new RuntimeDistributionVersion(DISTRIBUTION_VERSION)
    );
  }

  private static Path createFatJar(Path jarPath) throws IOException {
    return createFatJar(jarPath, "BOOT-INF/classes/", new byte[] {});
  }

  private static Path createFatJar(Path jarPath, String additionalEntryName, byte[] additionalEntryContent) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      if (!"BOOT-INF/classes/".equals(additionalEntryName)) {
        jarOutputStream.putNextEntry(new JarEntry(additionalEntryName));
        jarOutputStream.write(additionalEntryContent);
        jarOutputStream.closeEntry();
      }
    }

    return jarPath;
  }

  private static Path createFlatJar(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("com/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("com/company/Extension.class"));
      jarOutputStream.write(new byte[] { 1 });
      jarOutputStream.closeEntry();
    }

    return jarPath;
  }

  private static List<Path> filesWithPrefix(Path directoryPath, String prefix) throws IOException {
    try (Stream<Path> paths = Files.list(directoryPath)) {
      return paths.filter(path -> path.getFileName().toString().startsWith(prefix)).toList();
    }
  }

  private static RuntimeExtensionInstaller installer(Path userHome) {
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = new Seed4JCliHome(userHome).runtimeExtensionConfiguration();

    return new RuntimeExtensionInstaller(
      runtimeExtensionConfiguration,
      new FileSystemRuntimeModeConfigurationRepository(new Seed4JCliHome(userHome)),
      new FileSystemRuntimeExtensionArtifactsRepository()
    );
  }

  private static final class RecordingRuntimeModeConfigurationRepository implements RuntimeModeConfigurationRepository {

    private final Path configPath;
    private final RuntimeModeConfigurationDocument currentConfiguration;
    private final InvocationOrder invocationOrder;
    private int prepareCalls;
    private int applyCalls;
    private RuntimeModeConfigurationDocument lastPersistedConfiguration;
    private RuntimeMode lastPersistedMode;
    private RuntimeMode lastPreparedMode;
    private int applyInvocationOrder;

    private RecordingRuntimeModeConfigurationRepository(Path configPath, RuntimeModeConfigurationDocument currentConfiguration) {
      this(configPath, currentConfiguration, new InvocationOrder());
    }

    private RecordingRuntimeModeConfigurationRepository(
      Path configPath,
      RuntimeModeConfigurationDocument currentConfiguration,
      InvocationOrder invocationOrder
    ) {
      this.configPath = configPath;
      this.currentConfiguration = currentConfiguration;
      this.invocationOrder = invocationOrder;
    }

    @Override
    public RuntimeMode readMode() {
      return RuntimeMode.STANDARD;
    }

    @Override
    public RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode) {
      prepareCalls = prepareCalls + 1;
      lastPreparedMode = targetMode;

      return new RuntimeModeChangePlan() {
        @Override
        public Path configPath() {
          return configPath;
        }

        @Override
        public void apply() {
          applyCalls = applyCalls + 1;
          applyInvocationOrder = invocationOrder.next();
          lastPersistedMode = targetMode;
          lastPersistedConfiguration = currentConfiguration;
        }
      };
    }

    private int prepareCalls() {
      return prepareCalls;
    }

    private int applyCalls() {
      return applyCalls;
    }

    private RuntimeModeConfigurationDocument lastPersistedConfiguration() {
      return lastPersistedConfiguration;
    }

    private RuntimeMode lastPersistedMode() {
      return lastPersistedMode;
    }

    private RuntimeMode lastPreparedMode() {
      return lastPreparedMode;
    }

    private boolean applyCalledAfterInstall() {
      return applyInvocationOrder > 0 && applyInvocationOrder > invocationOrder.installInvocationOrder();
    }
  }

  private static final class RecordingRuntimeExtensionArtifactsRepository implements RuntimeExtensionArtifactsRepository {

    private final InvocationOrder invocationOrder;
    private int installCalls;
    private RuntimeExtensionInstallRequest lastInstallRequest;
    private RuntimeExtensionConfiguration lastRuntimeConfiguration;

    private RecordingRuntimeExtensionArtifactsRepository() {
      this(new InvocationOrder());
    }

    private RecordingRuntimeExtensionArtifactsRepository(InvocationOrder invocationOrder) {
      this.invocationOrder = invocationOrder;
    }

    @Override
    public boolean activeRuntimePresent(RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
      return false;
    }

    @Override
    public void install(RuntimeExtensionInstallRequest request, RuntimeExtensionConfiguration runtimeExtensionConfiguration) {
      installCalls = installCalls + 1;
      lastInstallRequest = request;
      lastRuntimeConfiguration = runtimeExtensionConfiguration;
      invocationOrder.markInstall();
    }

    private int installCalls() {
      return installCalls;
    }

    private RuntimeExtensionInstallRequest lastInstallRequest() {
      return lastInstallRequest;
    }

    private RuntimeExtensionConfiguration lastRuntimeConfiguration() {
      return lastRuntimeConfiguration;
    }
  }

  private static final class InvocationOrder {

    private int sequence;
    private int installInvocationOrder;

    private int next() {
      sequence = sequence + 1;
      return sequence;
    }

    private void markInstall() {
      installInvocationOrder = next();
    }

    private int installInvocationOrder() {
      return installInvocationOrder;
    }
  }
}
