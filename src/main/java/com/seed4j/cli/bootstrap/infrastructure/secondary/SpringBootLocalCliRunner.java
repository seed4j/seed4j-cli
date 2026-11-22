package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.LocalCliRunner;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class SpringBootLocalCliRunner implements LocalCliRunner {

  private static final String SPRING_CONFIG_TEMPLATE = "spring.config.location=classpath:/config/,file:%s";
  private final SpringApplicationBuilderOperationsFactory springApplicationBuilderOperationsFactory;
  private final SpringBootExitCodeResolver springBootExitCodeResolver;
  private final Seed4JCliHome cliHome;

  public SpringBootLocalCliRunner(Class<?> applicationClass, Seed4JCliHome cliHome) {
    this(
      () -> new SpringApplicationBuilderAdapter(new SpringApplicationBuilder(applicationClass)),
      new SpringBootExitCodeResolver(),
      cliHome
    );
  }

  SpringBootLocalCliRunner(
    SpringApplicationBuilderOperationsFactory springApplicationBuilderOperationsFactory,
    SpringBootExitCodeResolver springBootExitCodeResolver,
    Seed4JCliHome cliHome
  ) {
    Assert.notNull("springApplicationBuilderOperationsFactory", springApplicationBuilderOperationsFactory);
    Assert.notNull("springBootExitCodeResolver", springBootExitCodeResolver);
    Assert.notNull("cliHome", cliHome);
    this.springApplicationBuilderOperationsFactory = springApplicationBuilderOperationsFactory;
    this.springBootExitCodeResolver = springBootExitCodeResolver;
    this.cliHome = cliHome;
  }

  @Override
  public int run(Seed4JCliArguments arguments) {
    SpringApplicationBuilderOperations springApplicationBuilderOperations = springApplicationBuilderOperationsFactory.create();
    Path configPath = cliHome.configPath();

    if (Files.exists(configPath)) {
      springApplicationBuilderOperations.properties(SPRING_CONFIG_TEMPLATE.formatted(configPath));
    }

    SpringApplicationContextAdapter context = springApplicationBuilderOperations
      .bannerModeOff()
      .webNone()
      .lazyInitialization(true)
      .run(arguments.values());
    return springBootExitCodeResolver.resolve(context);
  }
}
