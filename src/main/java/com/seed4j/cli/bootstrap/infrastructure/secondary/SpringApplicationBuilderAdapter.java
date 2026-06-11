package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class SpringApplicationBuilderAdapter implements SpringApplicationBuilderOperations {

  private final SpringApplicationBuilder springApplicationBuilder;

  SpringApplicationBuilderAdapter(SpringApplicationBuilder springApplicationBuilder) {
    Assert.notNull("springApplicationBuilder", springApplicationBuilder);
    this.springApplicationBuilder = springApplicationBuilder;
  }

  @Override
  public SpringApplicationBuilderOperations bannerModeOff() {
    springApplicationBuilder.bannerMode(Mode.OFF);
    return this;
  }

  @Override
  public SpringApplicationBuilderOperations webNone() {
    springApplicationBuilder.web(WebApplicationType.NONE);
    return this;
  }

  @Override
  public SpringApplicationBuilderOperations lazyInitialization(boolean lazyInitialization) {
    springApplicationBuilder.lazyInitialization(lazyInitialization);
    return this;
  }

  @Override
  public SpringApplicationBuilderOperations properties(String properties) {
    springApplicationBuilder.properties(properties);
    return this;
  }

  @Override
  public SpringApplicationContextAdapter run(String[] args) {
    ConfigurableApplicationContext context = springApplicationBuilder.run(args);
    return new SpringApplicationContextAdapter(context);
  }
}
