package com.seed4j.cli;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationBuilder;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationContext;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackageClasses = { Seed4JApp.class, Seed4JCliApp.class })
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class Seed4JCliApp {

  private static final String CHILD_MODE_PROPERTY = "seed4j.cli.runtime.child";
  private static final String DEFAULT_CLI_VERSION = "0.0.0-SNAPSHOT";

  interface BootstrapEntryPoint {
    int launch(String[] args);
  }

  interface ExitHandler {
    void exit(int exitCode);
  }

  interface ProductionBootstrapEntryPointFactory {
    BootstrapEntryPoint create();
  }

  static void main(String[] args) {
    runProductionPath(args, () -> productionBootstrapEntryPoint(userHomePath(), childMode()), System::exit);
  }

  static void runProductionPath(String[] args, ProductionBootstrapEntryPointFactory bootstrapEntryPointFactory, ExitHandler exitHandler) {
    int exitCode = bootstrapEntryPointFactory.create().launch(args);
    exitHandler.exit(exitCode);
  }

  static BootstrapEntryPoint productionBootstrapEntryPoint(Path userHomePath, boolean childMode) {
    Seed4JCliLauncherFactory launcherFactory = new Seed4JCliLauncherFactory();
    Seed4JCliLauncher launcher = launcherFactory.create(
      userHomePath,
      executablePath(),
      currentCliVersion(),
      defaultJavaExecutable(),
      Seed4JCliApp::executeCommand,
      Seed4JCliApp::applicationBuilder,
      Seed4JCliApp::resolveExitCode
    );

    return args -> launcher.launch(args, childMode);
  }

  private static Path userHomePath() {
    return Path.of(System.getProperty("user.home"));
  }

  private static Path executablePath() {
    try {
      Path codeSourcePath = Path.of(Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return resolveExecutablePath(codeSourcePath, System.getProperty("sun.java.command", ""), System.getProperty("java.class.path", ""));
    } catch (URISyntaxException e) {
      throw new InvalidRuntimeConfigurationException("Could not resolve executable path.");
    }
  }

  static Path resolveExecutablePath(Path codeSourcePath, String javaCommand) {
    if (Files.isRegularFile(codeSourcePath) && codeSourcePath.getFileName().toString().endsWith(".jar")) {
      return codeSourcePath;
    }

    return Optional.of(javaCommand.trim())
      .filter(command -> !command.isEmpty())
      .map(command -> command.split("\\s+", 2)[0])
      .flatMap(Seed4JCliApp::regularJarPath)
      .orElse(codeSourcePath);
  }

  static Path resolveExecutablePath(Path codeSourcePath, String javaCommand, String javaClassPath) {
    Path executablePathFromCommand = resolveExecutablePath(codeSourcePath, javaCommand);
    if (!executablePathFromCommand.equals(codeSourcePath)) {
      return executablePathFromCommand;
    }

    String pathSeparator = System.getProperty("path.separator");
    return Optional.ofNullable(javaClassPath)
      .filter(classPath -> !classPath.isBlank())
      .stream()
      .flatMap(classPath -> Arrays.stream(classPath.split(java.util.regex.Pattern.quote(pathSeparator))))
      .map(String::trim)
      .map(Seed4JCliApp::regularJarPath)
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(codeSourcePath);
  }

  private static Optional<Path> regularJarPath(String candidatePath) {
    return Optional.ofNullable(candidatePath)
      .filter(path -> path.endsWith(".jar"))
      .map(Path::of)
      .filter(Files::isRegularFile);
  }

  private static String currentCliVersion() {
    return Optional.ofNullable(Seed4JCliApp.class.getPackage().getImplementationVersion())
      .filter(version -> !version.isBlank())
      .orElse(DEFAULT_CLI_VERSION);
  }

  private static boolean childMode() {
    return Boolean.parseBoolean(System.getProperty(CHILD_MODE_PROPERTY));
  }

  private static Path defaultJavaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  private static int executeCommand(List<String> command) {
    try {
      Process process = new ProcessBuilder(command).inheritIO().start();
      return process.waitFor();
    } catch (IOException e) {
      throw new InvalidRuntimeConfigurationException("Could not launch child process.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InvalidRuntimeConfigurationException("Child process execution was interrupted.");
    }
  }

  private static LocalSpringCliRunner.ApplicationBuilder applicationBuilder() {
    return new SpringApplicationBuilderAdapter(new SpringApplicationBuilder(Seed4JCliApp.class));
  }

  private static int resolveExitCode(LocalSpringCliRunner.ApplicationContext context) {
    SpringApplicationContextAdapter springApplicationContext = (SpringApplicationContextAdapter) context;
    return SpringApplication.exit(springApplicationContext.context());
  }

  private record SpringApplicationContextAdapter(
    ConfigurableApplicationContext context
  ) implements LocalSpringCliRunner.ApplicationContext {}

  private record SpringApplicationBuilderAdapter(SpringApplicationBuilder springApplicationBuilder) implements ApplicationBuilder {
    @Override
    public ApplicationBuilder bannerMode(Mode bannerMode) {
      springApplicationBuilder.bannerMode(bannerMode);
      return this;
    }

    @Override
    public ApplicationBuilder web(WebApplicationType webApplicationType) {
      springApplicationBuilder.web(webApplicationType);
      return this;
    }

    @Override
    public ApplicationBuilder lazyInitialization(boolean lazyInitialization) {
      springApplicationBuilder.lazyInitialization(lazyInitialization);
      return this;
    }

    @Override
    public ApplicationBuilder properties(String properties) {
      springApplicationBuilder.properties(properties);
      return this;
    }

    @Override
    public ApplicationContext run(String[] args) {
      ConfigurableApplicationContext applicationContext = springApplicationBuilder.run(args);
      return new SpringApplicationContextAdapter(applicationContext);
    }
  }
}
