package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationBuilder;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationContext;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class PreSpringLauncherAssembler {

  public PreSpringBootstrapEntryPoint assemble(Path userHomePath, Path executablePath, String currentSeed4JVersion, boolean childMode) {
    Seed4JCliLauncherFactory launcherFactory = new Seed4JCliLauncherFactory();
    Seed4JCliLauncherFactory.LauncherDependencies launcherDependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      defaultJavaExecutable(),
      PreSpringLauncherAssembler::executeCommand,
      PreSpringLauncherAssembler::applicationBuilder,
      PreSpringLauncherAssembler::resolveExitCode
    );
    Seed4JCliLauncher launcher = launcherFactory.create(
      userHomePath,
      executablePath,
      currentSeed4JVersion,
      new FileSystemRuntimeModeConfigurationRepository(userHomePath),
      launcherDependencies
    );

    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      launcher::launch,
      childMode
    );
    return preSpringBootstrapApplicationService::launch;
  }

  private static Path defaultJavaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java");
  }

  private static int executeCommand(List<String> command) {
    try {
      Process process = new ProcessBuilder(command).inheritIO().start();
      return process.waitFor();
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not launch child process.", ioException);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw InvalidRuntimeConfigurationException.technicalError("Child process execution was interrupted.", interruptedException);
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
