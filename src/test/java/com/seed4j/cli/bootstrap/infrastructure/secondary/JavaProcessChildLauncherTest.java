package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionCacheIdentity;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

@UnitTest
class JavaProcessChildLauncherTest {

  @Test
  void shouldBuildTheJavaCommandForStandardMode() {
    RecordingProcessExecutor processExecutor = new RecordingProcessExecutor();
    JavaProcessChildLauncher launcher = launcher(Path.of("/tmp/seed4j-cli-home"), processExecutor);
    RuntimeSelection runtimeSelection = RuntimeSelection.standard();
    ChildRuntimeLaunchRequest request = new ChildRuntimeLaunchRequest(
      Path.of("/opt/seed4j/seed4j-cli.jar"),
      runtimeSelection,
      new Seed4JCliArguments(new String[] { "--version" }),
      false
    );

    int exitCode = launcher.launch(request);

    assertThat(exitCode).isEqualTo(19);
    assertThat(processExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.mode=standard",
      "-cp",
      "/opt/seed4j/seed4j-cli.jar",
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version"
    );
  }

  @Test
  void shouldBuildTheJavaCommandForExtensionMode() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createEmptyJar(Files.createTempFile("seed4j-cli-", ".jar"));
    Path extensionJar = createExtensionJarWithStartClass(userHome.resolve("extension.jar"));
    RecordingProcessExecutor processExecutor = new RecordingProcessExecutor();
    JavaProcessChildLauncher launcher = launcher(userHome, processExecutor);
    RuntimeSelection runtimeSelection = RuntimeSelection.extension(
      new RuntimeExtensionJarPath(extensionJar),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );
    ChildRuntimeLaunchRequest request = new ChildRuntimeLaunchRequest(
      executableJar,
      runtimeSelection,
      new Seed4JCliArguments(new String[] { "--version", "--debug" }),
      true
    );

    int exitCode = launcher.launch(request);

    RuntimeExtensionCacheIdentity cacheIdentity = new RuntimeExtensionCacheIdentityResolver().resolve(extensionJar);
    Path overlayClassesPath = userHome.resolve(".config/seed4j-cli/runtime/cache").resolve(cacheIdentity.value()).resolve("classes");
    assertThat(exitCode).isEqualTo(19);
    assertThat(processExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dloader.path=" + overlayClassesPath,
      "-Dlogging.config=classpath:seed4j-cli-logback-spring.xml",
      "-Dlogging.level.com.seed4j.cli.bootstrap.domain=DEBUG",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.distribution.id=company-extension",
      "-Dseed4j.cli.runtime.distribution.version=1.0.0",
      "-Dseed4j.cli.runtime.extension.start-class=com.seed4j.extension.ExtensionApplication",
      "-Dseed4j.cli.runtime.mode=extension",
      "-Dspring.main.log-startup-info=false",
      "-cp",
      executableJar.toString(),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version",
      "--debug"
    );
  }

  @Test
  void shouldSetRootLoggingToErrorForExtensionModeWithoutDebug() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createEmptyJar(Files.createTempFile("seed4j-cli-", ".jar"));
    Path extensionJar = createExtensionJarWithStartClass(userHome.resolve("extension.jar"));
    RecordingProcessExecutor processExecutor = new RecordingProcessExecutor();
    JavaProcessChildLauncher launcher = launcher(userHome, processExecutor);
    RuntimeSelection runtimeSelection = RuntimeSelection.extension(
      new RuntimeExtensionJarPath(extensionJar),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );
    ChildRuntimeLaunchRequest request = new ChildRuntimeLaunchRequest(
      executableJar,
      runtimeSelection,
      new Seed4JCliArguments(new String[] { "--version" }),
      false
    );

    int exitCode = launcher.launch(request);

    RuntimeExtensionCacheIdentity cacheIdentity = new RuntimeExtensionCacheIdentityResolver().resolve(extensionJar);
    Path overlayClassesPath = userHome.resolve(".config/seed4j-cli/runtime/cache").resolve(cacheIdentity.value()).resolve("classes");
    assertThat(exitCode).isEqualTo(19);
    assertThat(processExecutor.command()).containsExactly(
      "/opt/jdk/bin/java",
      "-Dloader.path=" + overlayClassesPath,
      "-Dlogging.config=classpath:seed4j-cli-logback-spring.xml",
      "-Dlogging.level.root=ERROR",
      "-Dseed4j.cli.runtime.child=true",
      "-Dseed4j.cli.runtime.distribution.id=company-extension",
      "-Dseed4j.cli.runtime.distribution.version=1.0.0",
      "-Dseed4j.cli.runtime.extension.start-class=com.seed4j.extension.ExtensionApplication",
      "-Dseed4j.cli.runtime.mode=extension",
      "-Dspring.main.log-startup-info=false",
      "-cp",
      executableJar.toString(),
      "org.springframework.boot.loader.launch.PropertiesLauncher",
      "--version"
    );
  }

  @Test
  void shouldFailBeforeExecutingProcessWhenExtensionStartClassIsMissing() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path executableJar = createEmptyJar(Files.createTempFile("seed4j-cli-", ".jar"));
    Path extensionJar = createExtensionJarWithoutStartClass(userHome.resolve("extension.jar"));
    RecordingProcessExecutor processExecutor = new RecordingProcessExecutor();
    JavaProcessChildLauncher launcher = launcher(userHome, processExecutor);
    RuntimeSelection runtimeSelection = RuntimeSelection.extension(
      new RuntimeExtensionJarPath(extensionJar),
      new RuntimeDistributionId("company-extension"),
      new RuntimeDistributionVersion("1.0.0")
    );
    ChildRuntimeLaunchRequest request = new ChildRuntimeLaunchRequest(
      executableJar,
      runtimeSelection,
      new Seed4JCliArguments(new String[] { "--version" }),
      false
    );

    assertThatThrownBy(() -> launcher.launch(request))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Start-Class");
    assertThat(processExecutor.command()).isNull();
  }

  private static JavaProcessChildLauncher launcher(Path userHome, RecordingProcessExecutor processExecutor) {
    return new JavaProcessChildLauncher(
      Path.of("/opt/jdk/bin/java"),
      processExecutor,
      new RuntimeExtensionStartClassResolver(),
      new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome)),
      new RuntimeExtensionLoaderPathResolver()
    );
  }

  private static Path createEmptyJar(Path jarPath) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createExtensionJarWithStartClass(Path jarPath) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Start-Class", "com.seed4j.extension.ExtensionApplication");
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/"));
      jarOutputStream.closeEntry();
      jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/com/seed4j/extension/ExtensionApplication.class"));
      jarOutputStream.write(new byte[] { 0 });
      jarOutputStream.closeEntry();
    }
    return jarPath;
  }

  private static Path createExtensionJarWithoutStartClass(Path jarPath) throws IOException {
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

  private static final class RecordingProcessExecutor implements ChildProcessCommandExecutor {

    private List<String> command;

    @Override
    public int execute(List<String> command) {
      this.command = command;
      return 19;
    }

    List<String> command() {
      return command;
    }
  }
}
