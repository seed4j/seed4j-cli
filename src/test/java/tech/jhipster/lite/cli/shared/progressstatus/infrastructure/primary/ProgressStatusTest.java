package tech.jhipster.lite.cli.shared.progressstatus.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tech.jhipster.lite.cli.UnitTest;
import tech.jhipster.lite.cli.shared.progressstatus.domain.ProgressStatus;

@UnitTest
@ExtendWith(OutputCaptureExtension.class)
class ProgressStatusTest {

  private static final String CLEAR_LINE = "\r\033[K";

  @Nested
  @DisplayName("spinner display")
  class SpinnerDisplay {

    @Test
    void shouldDisplaySpinnerWithMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();

      progressStatus.show("Loading test data");

      assertThat(output.toString()).contains("Loading test data");
      progressStatus.hide();
    }

    @Test
    void shouldDisplaySpinnerWithDefaultMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();

      progressStatus.show();

      assertThat(output.toString()).contains("Processing");
      progressStatus.hide();
    }
  }

  @Nested
  @DisplayName("spinner updates")
  class SpinnerUpdates {

    @Test
    void shouldUpdateSpinnerMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();
      progressStatus.show("Initial message");

      progressStatus.update("Updated message");

      assertThat(output.toString()).contains("Updated message");
      progressStatus.hide();
    }
  }

  @Nested
  @DisplayName("spinner completion")
  class SpinnerCompletion {

    @Test
    void shouldShowSuccessMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();
      progressStatus.show("Operation in progress");

      progressStatus.success("Completed successfully");

      assertThat(output.toString()).contains("✓").contains("Completed successfully");
    }

    @Test
    void shouldShowFailureMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();
      progressStatus.show("Operation in progress");

      progressStatus.failure("Operation failed");

      assertThat(output.toString()).contains("✗").contains("Operation failed");
    }

    @Test
    void shouldHideSpinnerWithoutCompletionMessage(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();
      progressStatus.show("Operation in progress");

      progressStatus.hide();

      assertThat(output).endsWith(CLEAR_LINE).doesNotContain("✓ ").doesNotContain("✗ ");
    }
  }

  @Nested
  @DisplayName("spinner state handling")
  class SpinnerStateHandling {

    @Test
    void shouldHandleMultipleShowCalls(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();
      progressStatus.show("First message");

      progressStatus.show("Second message");

      assertThat(output.toString()).contains("Second message");
      progressStatus.hide();
    }

    @Test
    void shouldHandleSuccessWhenNotRunning(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();

      progressStatus.success("Success without running");

      assertThat(output.toString()).doesNotContain("✓").doesNotContain("Success without running");
    }

    @Test
    void shouldHandleFailureWhenNotRunning(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();

      progressStatus.failure("Failure without running");

      assertThat(output.toString()).doesNotContain("✗").doesNotContain("Failure without running");
    }

    @Test
    void shouldHandleHideWhenNotRunning(CapturedOutput output) {
      ProgressStatus progressStatus = new SpinnerProgressStatus();

      String outputBefore = output.toString();
      progressStatus.hide();
      String outputAfter = output.toString();

      assertThat(outputAfter).isEqualTo(outputBefore);
    }
  }
}
