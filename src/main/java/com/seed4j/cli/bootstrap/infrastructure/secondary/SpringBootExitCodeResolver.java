package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.boot.SpringApplication;

class SpringBootExitCodeResolver {

  int resolve(SpringApplicationContextAdapter context) {
    Assert.notNull("context", context);
    return SpringApplication.exit(context.context());
  }
}
