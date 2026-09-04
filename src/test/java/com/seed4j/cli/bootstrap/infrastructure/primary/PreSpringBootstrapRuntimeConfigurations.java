package com.seed4j.cli.bootstrap.infrastructure.primary;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

final class PreSpringBootstrapRuntimeConfigurations {

  private PreSpringBootstrapRuntimeConfigurations() {}

  static Stream<Arguments> invalid() {
    return Stream.of(
      Arguments.of(
        "extension mode selected without runtime artifacts",
        """
        seed4j:
          runtime:
            mode: extension
        """
      ),
      Arguments.of(
        "runtime mode has an invalid value",
        """
        seed4j:
          runtime:
            mode: corporate
        """
      ),
      Arguments.of(
        "external config root is not a map",
        """
        - seed4j
        - runtime
        """
      ),
      Arguments.of(
        "seed4j root is not a map",
        """
        seed4j: 123
        """
      ),
      Arguments.of(
        "runtime mode is not a string",
        """
        seed4j:
          runtime:
            mode:
              - standard
        """
      )
    );
  }

  static Stream<Arguments> standardMode() {
    return Stream.of(
      Arguments.of(
        "runtime mode explicitly set to standard",
        """
        seed4j:
          runtime:
            mode: standard
        """
      ),
      Arguments.of(
        "config file exists without runtime.mode",
        """
        seed4j:
          hidden-resources:
            slugs:
              - gradle-java
        """
      ),
      Arguments.of(
        "runtime section exists without mode",
        """
        seed4j:
          runtime:
            extension:
              fail-on-invalid: true
        """
      ),
      Arguments.of(
        "config file exists without seed4j section",
        """
        feature-flags:
          experimental: true
        """
      )
    );
  }
}
