package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

@UnitTest
class SpringBootExitCodeResolverTest {

  @Test
  void shouldResolveNonZeroExitCodeFromApplicationContext() {
    int expectedExitCode = 41;
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(ExitCodeGenerator.class, () -> () -> expectedExitCode);
      context.refresh();
      SpringBootExitCodeResolver exitCodeResolver = new SpringBootExitCodeResolver();

      int exitCode = exitCodeResolver.resolve(new SpringApplicationContextAdapter(context));

      assertThat(exitCode).isEqualTo(expectedExitCode);
    }
  }
}
