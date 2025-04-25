package tech.jhipster.lite.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@SpringBootApplication
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class JHLiteCliApplicationApp {

  private static final Logger log = LoggerFactory.getLogger(JHLiteCliApplicationApp.class);

  public static void main(String[] args) {
    Environment env = SpringApplication.run(JHLiteCliApplicationApp.class, args).getEnvironment();

    if (log.isInfoEnabled()) {
      log.info(ApplicationStartupTraces.of(env));
    }
  }
}
