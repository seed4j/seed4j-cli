package tech.jhipster.lite.cli.shared.progressstatus.infrastructure.primary;

import tech.jhipster.lite.cli.shared.progressstatus.domain.ProgressStatus;

public final class ProgressStatusProvider {

  private static final ProgressStatus INSTANCE = new SpinnerProgressStatus();

  private ProgressStatusProvider() {}

  public static ProgressStatus get() {
    return INSTANCE;
  }
}
