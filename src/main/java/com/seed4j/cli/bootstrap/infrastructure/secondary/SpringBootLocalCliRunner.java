package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class SpringBootLocalCliRunner implements LocalCliRunner {

  private static final String CONFIG_FILE_NAME = ".config/seed4j-cli/config.yml";
  private static final String SPRING_CONFIG_TEMPLATE = "spring.config.location=classpath:/config/,file:%s";
  private static final String RUNTIME_EXTENSION_START_CLASS_PROPERTY = "seed4j.cli.runtime.extension.start-class";
  private static final String SPRING_MAIN_SOURCES_TEMPLATE = "spring.main.sources=%s";

  private final SpringApplicationBuilderOperationsFactory springApplicationBuilderOperationsFactory;
  private final SpringBootExitCodeResolver springBootExitCodeResolver;
  private final Path userHomePath;
  private final RuntimeExtensionStartClassReader runtimeExtensionStartClassReader;

  public SpringBootLocalCliRunner(Class<?> applicationClass, Path userHomePath) {
    this(
      () -> new SpringApplicationBuilderAdapter(new SpringApplicationBuilder(applicationClass)),
      new SpringBootExitCodeResolver(),
      userHomePath,
      () -> System.getProperty(RUNTIME_EXTENSION_START_CLASS_PROPERTY)
    );
  }

  SpringBootLocalCliRunner(
    SpringApplicationBuilderOperationsFactory springApplicationBuilderOperationsFactory,
    SpringBootExitCodeResolver springBootExitCodeResolver,
    Path userHomePath,
    RuntimeExtensionStartClassReader runtimeExtensionStartClassReader
  ) {
    Assert.notNull("springApplicationBuilderOperationsFactory", springApplicationBuilderOperationsFactory);
    Assert.notNull("springBootExitCodeResolver", springBootExitCodeResolver);
    Assert.notNull("userHomePath", userHomePath);
    Assert.notNull("runtimeExtensionStartClassReader", runtimeExtensionStartClassReader);
    this.springApplicationBuilderOperationsFactory = springApplicationBuilderOperationsFactory;
    this.springBootExitCodeResolver = springBootExitCodeResolver;
    this.userHomePath = userHomePath;
    this.runtimeExtensionStartClassReader = runtimeExtensionStartClassReader;
  }

  @Override
  public int run(String[] args) {
    SpringApplicationBuilderOperations springApplicationBuilderOperations = springApplicationBuilderOperationsFactory.create();
    Path configPath = userHomePath.resolve(CONFIG_FILE_NAME);

    if (Files.exists(configPath)) {
      springApplicationBuilderOperations.properties(SPRING_CONFIG_TEMPLATE.formatted(configPath));
    }

    extensionStartClass().ifPresent(startClass ->
      springApplicationBuilderOperations.properties(SPRING_MAIN_SOURCES_TEMPLATE.formatted(startClass))
    );

    SpringApplicationContextAdapter context = springApplicationBuilderOperations
      .bannerModeOff()
      .webNone()
      .lazyInitialization(true)
      .run(args);
    return springBootExitCodeResolver.resolve(context);
  }

  private Optional<String> extensionStartClass() {
    return Optional.ofNullable(runtimeExtensionStartClassReader.current())
      .map(String::trim)
      .filter(startClass -> !startClass.isEmpty());
  }

  @FunctionalInterface
  interface RuntimeExtensionStartClassReader {
    String current();
  }
}
