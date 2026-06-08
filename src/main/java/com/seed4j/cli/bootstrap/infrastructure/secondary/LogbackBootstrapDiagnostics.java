package com.seed4j.cli.bootstrap.infrastructure.secondary;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.seed4j.cli.bootstrap.domain.BootstrapDiagnostics;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import org.slf4j.LoggerFactory;

@ExcludeFromGeneratedCodeCoverage(
  reason = "Non-Logback guard branch depends on the runtime SLF4J binding and this method is best-effort diagnostics"
)
public class LogbackBootstrapDiagnostics implements BootstrapDiagnostics {

  private static final String BOOTSTRAP_LOGGER_NAME = "com.seed4j.cli.bootstrap.domain";

  @Override
  public void enableDebugLogging() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
      return;
    }

    Logger logger = loggerContext.getLogger(BOOTSTRAP_LOGGER_NAME);
    logger.setLevel(Level.DEBUG);
  }
}
