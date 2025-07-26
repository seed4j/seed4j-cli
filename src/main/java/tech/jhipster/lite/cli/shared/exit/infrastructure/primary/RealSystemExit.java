package tech.jhipster.lite.cli.shared.exit.infrastructure.primary;

import org.springframework.stereotype.Component;
import tech.jhipster.lite.cli.shared.exit.domain.SystemExit;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@Component
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing actual System exit")
class RealSystemExit implements SystemExit {

  @Override
  public void exit(int exitCode) {
    System.exit(exitCode);
  }
}
