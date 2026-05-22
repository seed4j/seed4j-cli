package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;

public class LocalSpringCliRunner implements LocalCliRunner {

  private static final String CONFIG_FILE_NAME = ".config/seed4j-cli/config.yml";
  private static final String SPRING_CONFIG_TEMPLATE = "spring.config.location=classpath:/config/,file:%s";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";
  private static final String SPRING_MAIN_SOURCES_TEMPLATE = "spring.main.sources=%s";

  @FunctionalInterface
  public interface ApplicationBuilderFactory {
    ApplicationBuilder create();
  }

  @FunctionalInterface
  public interface ExitCodeResolver {
    int resolve(ApplicationContext context);
  }

  @FunctionalInterface
  public interface UserHomeProvider {
    Path userHome();
  }

  public interface ApplicationContext {}

  public interface ApplicationBuilder {
    ApplicationBuilder bannerMode(Banner.Mode bannerMode);

    ApplicationBuilder web(WebApplicationType webApplicationType);

    ApplicationBuilder lazyInitialization(boolean lazyInitialization);

    ApplicationBuilder properties(String properties);

    ApplicationContext run(String[] args);
  }

  private final ApplicationBuilderFactory applicationBuilderFactory;
  private final ExitCodeResolver exitCodeResolver;
  private final UserHomeProvider userHomeProvider;

  public LocalSpringCliRunner(
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
    extensionStartClass().ifPresent(startClass -> builder.properties(SPRING_MAIN_SOURCES_TEMPLATE.formatted(startClass)));
    builder.bannerMode(Banner.Mode.OFF);
    builder.web(WebApplicationType.NONE);
    builder.lazyInitialization(true);
    ApplicationContext context = builder.run(args);
    return exitCodeResolver.resolve(context);
  }

  private static Optional<String> extensionStartClass() {
    return Optional.ofNullable(System.getProperty(RUNTIME_EXTENSION_START_CLASS_PROPERTY))
      .map(String::trim)
      .filter(startClass -> !startClass.isEmpty());
  }
}
