package tech.jhipster.lite.cli.shared.exit.domain;

import org.springframework.context.ApplicationContext;

public interface SpringApplicationExit {
  int exit(ApplicationContext context);
}
