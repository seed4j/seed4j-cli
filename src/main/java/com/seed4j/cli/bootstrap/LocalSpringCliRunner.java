package com.seed4j.cli.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;

class LocalSpringCliRunner implements LocalCliRunner {

  private static final String CONFIG_FILE_NAME = ".config/seed4j-cli.yml";
  private static final String SPRING_CONFIG_TEMPLATE = "spring.config.location=classpath:/config/,file:%s";

  @FunctionalInterface
  interface ApplicationBuilderFactory {
    ApplicationBuilder create();
  }

  @FunctionalInterface
  interface ExitCodeResolver {
    int resolve(ApplicationContext context);
  }

  @FunctionalInterface
  interface UserHomeProvider {
    Path userHome();
  }

  interface ApplicationContext {}

  interface ApplicationBuilder {
    ApplicationBuilder bannerMode(Banner.Mode bannerMode);

    ApplicationBuilder web(WebApplicationType webApplicationType);

    ApplicationBuilder lazyInitialization(boolean lazyInitialization);

    ApplicationBuilder properties(String properties);

    ApplicationContext run(String[] args);
  }

  private final ApplicationBuilderFactory applicationBuilderFactory;
  private final ExitCodeResolver exitCodeResolver;
  private final UserHomeProvider userHomeProvider;

  LocalSpringCliRunner(
    ApplicationBuilderFactory applicationBuilderFactory,
    ExitCodeResolver exitCodeResolver,
    UserHomeProvider userHomeProvider
  ) {
    this.applicationBuilderFactory = applicationBuilderFactory;
    this.exitCodeResolver = exitCodeResolver;
    this.userHomeProvider = userHomeProvider;
  }

  @Override
  public int run(String[] args) {
    ApplicationBuilder builder = applicationBuilderFactory.create();
    Path configPath = userHomeProvider.userHome().resolve(CONFIG_FILE_NAME);
    if (Files.exists(configPath)) {
      builder.properties(SPRING_CONFIG_TEMPLATE.formatted(configPath));
    }
    builder.bannerMode(Banner.Mode.OFF);
    builder.web(WebApplicationType.NONE);
    ApplicationContext context = builder.run(args);
    return exitCodeResolver.resolve(context);
  }
}
