package com.seed4j.cli;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.bootstrap.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.JavaProcessChildLauncher;
import com.seed4j.cli.bootstrap.LocalSpringCliRunner;
import com.seed4j.cli.bootstrap.Seed4JCliLauncher;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.Banner;
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
    Seed4JCliLauncher launcher = new Seed4JCliLauncher(
      userHomePath,
      executablePath(),
      currentCliVersion(),
      new JavaProcessChildLauncher(defaultJavaExecutable(), Seed4JCliApp::executeCommand),
      new LocalSpringCliRunner(Seed4JCliApp::applicationBuilder, Seed4JCliApp::resolveExitCode, () -> userHomePath)
    );

    return args -> launcher.launch(args, childMode);
  }

  private static Path userHomePath() {
    return Path.of(System.getProperty("user.home"));
  }

  private static Path executablePath() {
    try {
      return Path.of(Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      throw new InvalidRuntimeConfigurationException("Could not resolve executable path.");
    }
  }

  private static String currentCliVersion() {
    String implementationVersion = Seed4JCliApp.class.getPackage().getImplementationVersion();
    if (implementationVersion == null || implementationVersion.isBlank()) {
      return DEFAULT_CLI_VERSION;
    }

    return implementationVersion;
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

  private static final class SpringApplicationBuilderAdapter implements LocalSpringCliRunner.ApplicationBuilder {

    private final SpringApplicationBuilder springApplicationBuilder;

    private SpringApplicationBuilderAdapter(SpringApplicationBuilder springApplicationBuilder) {
      this.springApplicationBuilder = springApplicationBuilder;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder bannerMode(Banner.Mode bannerMode) {
      springApplicationBuilder.bannerMode(bannerMode);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder web(WebApplicationType webApplicationType) {
      springApplicationBuilder.web(webApplicationType);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder lazyInitialization(boolean lazyInitialization) {
      springApplicationBuilder.lazyInitialization(lazyInitialization);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationBuilder properties(String properties) {
      springApplicationBuilder.properties(properties);
      return this;
    }

    @Override
    public LocalSpringCliRunner.ApplicationContext run(String[] args) {
      ConfigurableApplicationContext applicationContext = springApplicationBuilder.run(args);
      return new SpringApplicationContextAdapter(applicationContext);
    }
  }
}
