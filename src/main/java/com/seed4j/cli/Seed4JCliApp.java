package com.seed4j.cli;

import com.seed4j.cli.bootstrap.composition.PreSpringBootstrapConfiguration;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs and System.exit behavior")
@SpringBootApplication
public class Seed4JCliApp {

  static void main(String[] args) {
    System.exit(PreSpringBootstrapConfiguration.preSpringBootstrapRunner().exitCodeFor(args));
  }
}
