package com.seed4j.cli.bootstrap.composition;

import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringBootstrapApplicationService;
import com.seed4j.cli.bootstrap.application.PreSpringLauncher;
import com.seed4j.cli.bootstrap.application.PreSpringLauncherFactory;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationBuilder;
import com.seed4j.cli.bootstrap.domain.LocalSpringCliRunner.ApplicationContext;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncher;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapRunner;
import com.seed4j.cli.bootstrap.infrastructure.secondary.CurrentProcessPreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.JavaChildProcessCommandExecutor;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class PreSpringBootstrapConfiguration {

  private PreSpringBootstrapConfiguration() {}

  public static PreSpringBootstrapRunner preSpringBootstrapRunner() {
    return preSpringBootstrapRunner(new CurrentProcessPreSpringRuntimeEnvironmentReader());
  }

  static PreSpringBootstrapRunner preSpringBootstrapRunner(PreSpringRuntimeEnvironmentReader preSpringRuntimeEnvironmentReader) {
    Assert.notNull("preSpringRuntimeEnvironmentReader", preSpringRuntimeEnvironmentReader);
    PreSpringBootstrapApplicationService preSpringBootstrapApplicationService = new PreSpringBootstrapApplicationService(
      preSpringLauncherFactory(),
      preSpringRuntimeEnvironmentReader
    );
    return new PreSpringBootstrapRunner(preSpringBootstrapApplicationService);
  }

  private static PreSpringLauncherFactory preSpringLauncherFactory() {
    return PreSpringBootstrapConfiguration::preSpringLauncher;
  }

  private static PreSpringLauncher preSpringLauncher(
    Path userHomePath,
    Path executablePath,
    String currentSeed4JVersion,
    Path javaExecutablePath
  ) {
    Seed4JCliLauncherFactory launcherFactory = new Seed4JCliLauncherFactory();
    Seed4JCliLauncherFactory.LauncherDependencies launcherDependencies = new Seed4JCliLauncherFactory.LauncherDependencies(
      javaExecutablePath,
      new JavaChildProcessCommandExecutor(),
      PreSpringBootstrapConfiguration::applicationBuilder,
      PreSpringBootstrapConfiguration::resolveExitCode
    );
    Seed4JCliLauncher launcher = launcherFactory.create(
      userHomePath,
      executablePath,
      currentSeed4JVersion,
      new FileSystemRuntimeModeConfigurationRepository(userHomePath),
      launcherDependencies
    );
    return launcher::launch;
  }

  private static ApplicationBuilder applicationBuilder() {
    return new SpringApplicationBuilderAdapter(new SpringApplicationBuilder(Seed4JCliApp.class));
  }

  private static int resolveExitCode(ApplicationContext context) {
    SpringApplicationContextAdapter springApplicationContext = (SpringApplicationContextAdapter) context;
    return SpringApplication.exit(springApplicationContext.context());
  }

  private record SpringApplicationContextAdapter(ConfigurableApplicationContext context) implements ApplicationContext {}

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
