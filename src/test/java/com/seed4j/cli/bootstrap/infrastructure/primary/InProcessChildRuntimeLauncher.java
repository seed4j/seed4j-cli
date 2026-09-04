package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.DISTRIBUTION_ID_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.DISTRIBUTION_VERSION_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.LOADER_PATH_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.RUNTIME_MODE_PROPERTY;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.javaExecutablePath;

import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLaunchRequest;
import com.seed4j.cli.bootstrap.domain.ChildRuntimeLauncher;
import com.seed4j.cli.bootstrap.domain.JavaExecutablePath;
import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.domain.RuntimeProcessMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliExecutablePath;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.ScopedRuntimeProperties;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemPackagedExecutableDetector;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionSelectionRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JarRuntimeExtensionPackageValidator;
import com.seed4j.cli.bootstrap.infrastructure.secondary.PreSpringRuntimeEnvironmentSeed4JCliRuntime;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionLoaderPathResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionOverlayCache;
import com.seed4j.cli.bootstrap.infrastructure.secondary.RuntimeExtensionStartClassResolver;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SystemErrBootstrapOutput;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

final class InProcessChildRuntimeLauncher implements ChildRuntimeLauncher {

  private final Seed4JCliHome cliHome;
  private final LocalCliRunner localCliRunner;

  private InProcessChildRuntimeLauncher(Seed4JCliHome cliHome, LocalCliRunner localCliRunner) {
    this.cliHome = cliHome;
    this.localCliRunner = localCliRunner;
  }

  static PreSpringBootstrapRunner runner(Path userHome) throws IOException {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    Path executableJar = Files.createTempFile("seed4j-cli-", ".jar");
    LocalCliRunner localCliRunner = new SpringBootLocalCliRunner(TestSeed4JCliApp.class, cliHome);
    PreSpringRuntimeEnvironment runtimeEnvironment = new PreSpringRuntimeEnvironment(
      cliHome,
      new Seed4JCliExecutablePath(executableJar),
      RuntimeProcessMode.PARENT,
      new JavaExecutablePath(javaExecutablePath())
    );
    PreSpringBootstrapApplicationService applicationService = new PreSpringBootstrapApplicationService(
      new PreSpringRuntimeEnvironmentSeed4JCliRuntime(runtimeEnvironment),
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionSelectionRepository(cliHome, new JarRuntimeExtensionPackageValidator()),
      new InProcessChildRuntimeLauncher(cliHome, localCliRunner),
      localCliRunner,
      new FileSystemPackagedExecutableDetector(),
      () -> {},
      new SystemErrBootstrapOutput()
    );
    return new PreSpringBootstrapRunner(applicationService);
  }

  @Override
  public int launch(ChildRuntimeLaunchRequest request) {
    Map<String, String> systemProperties = systemProperties(request);
    try (URLClassLoader childRuntimeClassLoader = childRuntimeClassLoader(request, Thread.currentThread().getContextClassLoader())) {
      return runInChildRuntime(request, systemProperties, childRuntimeClassLoader);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not close the in-process child runtime classloader", exception);
    }
  }

  private int runInChildRuntime(
    ChildRuntimeLaunchRequest request,
    Map<String, String> systemProperties,
    URLClassLoader childRuntimeClassLoader
  ) {
    Thread currentThread = Thread.currentThread();
    ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    ScopedRuntimeProperties runtimeProperties = ScopedRuntimeProperties.capture(systemProperties.keySet());
    try (runtimeProperties) {
      currentThread.setContextClassLoader(childRuntimeClassLoader);
      systemProperties.forEach(System::setProperty);
      return localCliRunner.run(request.arguments());
    } finally {
      currentThread.setContextClassLoader(originalContextClassLoader);
    }
  }

  private Map<String, String> systemProperties(ChildRuntimeLaunchRequest request) {
    Map<String, String> systemProperties = new LinkedHashMap<>(runtimeSystemProperties(request.runtimeSelection()));
    systemProperties.putAll(extensionSystemProperties(request));
    systemProperties.putAll(quietLoggingSystemProperties());
    return Map.copyOf(systemProperties);
  }

  private Map<String, String> runtimeSystemProperties(RuntimeSelection runtimeSelection) {
    Map<String, String> systemProperties = new LinkedHashMap<>();
    systemProperties.put("seed4j.cli.runtime.child", "true");
    systemProperties.put(RUNTIME_MODE_PROPERTY, runtimeSelection.mode().name().toLowerCase());
    runtimeSelection.distributionId().ifPresent(id -> systemProperties.put(DISTRIBUTION_ID_PROPERTY, id.id()));
    runtimeSelection.distributionVersion().ifPresent(version -> systemProperties.put(DISTRIBUTION_VERSION_PROPERTY, version.version()));
    return systemProperties;
  }

  private Map<String, String> extensionSystemProperties(ChildRuntimeLaunchRequest request) {
    return request
      .runtimeSelection()
      .extensionJarPath()
      .map(extensionJarPath -> extensionSystemProperties(request, extensionJarPath.path()))
      .orElseGet(Map::of);
  }

  private Map<String, String> extensionSystemProperties(ChildRuntimeLaunchRequest request, Path extensionJarPath) {
    Path overlayClassesPath = new RuntimeExtensionOverlayCache(cliHome).materialize(extensionJarPath);
    return Map.of(
      "seed4j.cli.runtime.extension.start-class",
      new RuntimeExtensionStartClassResolver().resolve(extensionJarPath),
      LOADER_PATH_PROPERTY,
      new RuntimeExtensionLoaderPathResolver().resolve(overlayClassesPath, extensionJarPath, request.executableJar().path())
    );
  }

  private Map<String, String> quietLoggingSystemProperties() {
    return Map.of(
      "logging.config",
      "classpath:seed4j-cli-logback-spring.xml",
      "logging.level.root",
      "ERROR",
      "spring.main.log-startup-info",
      "false"
    );
  }

  private URLClassLoader childRuntimeClassLoader(ChildRuntimeLaunchRequest request, ClassLoader parentClassLoader) {
    return request
      .runtimeSelection()
      .extensionJarPath()
      .map(extensionJarPath -> childRuntimeClassLoader(request, extensionJarPath.path(), parentClassLoader))
      .orElseGet(() -> new URLClassLoader(new URL[0], parentClassLoader));
  }

  private URLClassLoader childRuntimeClassLoader(ChildRuntimeLaunchRequest request, Path extensionJarPath, ClassLoader parentClassLoader) {
    try {
      Path overlayClassesPath = new RuntimeExtensionOverlayCache(cliHome).materialize(extensionJarPath);
      return new ChildFirstRuntimeExtensionResourceClassLoader(
        childRuntimeAndTestClasspathUrls(overlayClassesPath, extensionJarPath, request.executableJar().path()),
        parentClassLoader
      );
    } catch (MalformedURLException exception) {
      throw new IllegalStateException("Could not create child runtime classloader for extension jar: " + extensionJarPath, exception);
    }
  }

  private URL[] childRuntimeAndTestClasspathUrls(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath)
    throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    urls.add(overlayClassesPath.toUri().toURL());
    urls.add(extensionJarPath.toUri().toURL());
    urls.add(executableJarPath.toUri().toURL());
    for (String classPathEntry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) {
      if (!classPathEntry.isBlank()) {
        urls.add(Path.of(classPathEntry).toUri().toURL());
      }
    }
    return urls.toArray(URL[]::new);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(basePackages = "com.seed4j.cli")
  public static class TestSeed4JCliApp {}
}
