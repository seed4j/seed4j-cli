package tech.jhipster.lite.cli.shared.exit.infrastructure.primary;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tech.jhipster.lite.cli.shared.exit.domain.SpringApplicationExit;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@Component
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing actual SpringApplication exit")
class RealSpringApplicationExit implements SpringApplicationExit {

  @Override
  public int exit(ApplicationContext context) {
    return SpringApplication.exit(context);
  }
}
