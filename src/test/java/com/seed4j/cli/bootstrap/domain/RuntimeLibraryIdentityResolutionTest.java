package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seed4j.cli.UnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@UnitTest
class RuntimeLibraryIdentityResolutionTest {

  @Test
  void shouldPreferMetadataIdentityWhenMetadataIsPresent() {
    RuntimeLibraryIdentity metadataIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0");
    RuntimeLibraryIdentity fileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0");
    RuntimeLibraryIdentityResolution resolution = RuntimeLibraryIdentityResolution.from(
      Optional.of(metadataIdentity),
      Optional.of(fileNameIdentity)
    );

    assertThat(resolution.effectiveIdentity()).contains(metadataIdentity);
  }

  @Test
  void shouldUseFileNameIdentityWhenMetadataIsMissing() {
    RuntimeLibraryIdentity fileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0");
    RuntimeLibraryIdentityResolution resolution = RuntimeLibraryIdentityResolution.from(Optional.empty(), Optional.of(fileNameIdentity));

    assertThat(resolution.effectiveIdentity()).contains(fileNameIdentity);
  }

  @Test
  void shouldLogOverrideOnlyWhenMetadataAndFileNameIdentitiesDiffer() {
    RuntimeLibraryIdentity metadataIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0");
    RuntimeLibraryIdentity differentFileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "2.0.0");
    RuntimeLibraryIdentity sameFileNameIdentity = new RuntimeLibraryIdentity("com.acme:shared-lib", "1.0.0");
    Logger logger = (Logger) LoggerFactory.getLogger(RuntimeLibraryIdentityResolution.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      RuntimeLibraryIdentityResolution.from(Optional.of(metadataIdentity), Optional.of(differentFileNameIdentity)).logOverrideIfNeeded(
        logger,
        "shared-lib-2.0.0.jar"
      );
      RuntimeLibraryIdentityResolution.from(Optional.of(metadataIdentity), Optional.of(sameFileNameIdentity)).logOverrideIfNeeded(
        logger,
        "shared-lib-1.0.0.jar"
      );
      RuntimeLibraryIdentityResolution.from(Optional.empty(), Optional.of(differentFileNameIdentity)).logOverrideIfNeeded(
        logger,
        "shared-lib-2.0.0.jar"
      );
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list)
      .extracting(ILoggingEvent::getFormattedMessage)
      .containsExactly(
        "Using pom.properties identity com.acme:shared-lib:1.0.0 for 'shared-lib-2.0.0.jar' instead of file name inferred identity com.acme:shared-lib:2.0.0"
      );
  }
}
