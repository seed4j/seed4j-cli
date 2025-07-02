package tech.jhipster.lite.cli.shared.spinnerprogress.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tech.jhipster.lite.cli.UnitTest;
import tech.jhipster.lite.cli.shared.spinnerprogress.domain.SpinnerProgress;

@UnitTest
@ExtendWith(OutputCaptureExtension.class)
class SpinnerProgressTest {

  private static final String CLEAR_LINE = "\r\033[K";

  @Nested
  @DisplayName("spinner display")
  class SpinnerDisplay {

    @Test
    void shouldDisplaySpinnerWithMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();

      spinnerProgress.show("Loading test data");

      assertThat(output.toString()).contains("Loading test data");
      spinnerProgress.hide();
    }

    @Test
    void shouldDisplaySpinnerWithDefaultMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();

      spinnerProgress.show();

      assertThat(output.toString()).contains("Processing");
      spinnerProgress.hide();
    }
  }

  @Nested
  @DisplayName("spinner updates")
  class SpinnerUpdates {

    @Test
    void shouldUpdateSpinnerMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();
      spinnerProgress.show("Initial message");

      spinnerProgress.update("Updated message");

      assertThat(output.toString()).contains("Updated message");
      spinnerProgress.hide();
    }
  }

  @Nested
  @DisplayName("spinner completion")
  class SpinnerCompletion {

    @Test
    void shouldShowSuccessMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();
      spinnerProgress.show("Operation in progress");

      spinnerProgress.success("Completed successfully");

      assertThat(output.toString()).contains("✓").contains("Completed successfully");
    }

    @Test
    void shouldShowFailureMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();
      spinnerProgress.show("Operation in progress");

      spinnerProgress.failure("Operation failed");

      assertThat(output.toString()).contains("✗").contains("Operation failed");
    }

    @Test
    void shouldHideSpinnerWithoutCompletionMessage(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();
      spinnerProgress.show("Operation in progress");

      spinnerProgress.hide();

      assertThat(output).endsWith(CLEAR_LINE).doesNotContain("✓ ").doesNotContain("✗ ");
    }
  }

  @Nested
  @DisplayName("spinner state handling")
  class SpinnerStateHandling {

    @Test
    void shouldHandleMultipleShowCalls(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();
      spinnerProgress.show("First message");

      spinnerProgress.show("Second message");

      assertThat(output.toString()).contains("Second message");
      spinnerProgress.hide();
    }

    @Test
    void shouldHandleSuccessWhenNotRunning(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();

      spinnerProgress.success("Success without running");

      assertThat(output.toString()).doesNotContain("✓").doesNotContain("Success without running");
    }

    @Test
    void shouldHandleFailureWhenNotRunning(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();

      spinnerProgress.failure("Failure without running");

      assertThat(output.toString()).doesNotContain("✗").doesNotContain("Failure without running");
    }

    @Test
    void shouldHandleHideWhenNotRunning(CapturedOutput output) {
      SpinnerProgress spinnerProgress = new ConsoleSpinnerProgress();

      String outputBefore = output.toString();
      spinnerProgress.hide();
      String outputAfter = output.toString();

      assertThat(outputAfter).isEqualTo(outputBefore);
    }
  }
}
