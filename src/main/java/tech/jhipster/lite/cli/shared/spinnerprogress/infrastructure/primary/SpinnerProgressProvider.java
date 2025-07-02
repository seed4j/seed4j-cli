package tech.jhipster.lite.cli.shared.spinnerprogress.infrastructure.primary;

import tech.jhipster.lite.cli.shared.spinnerprogress.domain.SpinnerProgress;

public final class SpinnerProgressProvider {

  private static final SpinnerProgress INSTANCE = new ConsoleSpinnerProgress();

  private SpinnerProgressProvider() {}

  public static SpinnerProgress get() {
    return INSTANCE;
  }
}
