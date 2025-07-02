package tech.jhipster.lite.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.jhipster.lite.TestProjects.newTestFolder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tech.jhipster.lite.cli.shared.exit.domain.SpringApplicationExit;
import tech.jhipster.lite.cli.shared.exit.domain.SystemExit;

@IntegrationTest
class JHLiteCliAppTest {

  private static final String LOADING_COMPLETE_MESSAGE = "JHipster Lite CLI is ready";
  private static final String COMMAND_SUCCESS_MESSAGE = "Command executed";
  private static final String COMMAND_FAILURE_MESSAGE = "Command failed";
  private static final String VERSION_INFO_PREFIX = "JHipster Lite CLI v";
  private static final String AVAILABLE_JHIPSTER_LITE_MODULES = "Available jhipster-lite modules";
  private static final String MISSING_REQUIRED_OPTIONS = "Missing required options";

  @Configuration
  static class TestConfiguration {

    @Bean
    @Primary
    SystemExit systemExit() {
      return mock(SystemExit.class);
    }

    @Bean
    @Primary
    SpringApplicationExit springApplicationExit() {
      return mock(SpringApplicationExit.class);
    }
  }

  @Nested
  @DisplayName("spinner progress messages")
  class SpinnerProgressMessages {

    @Nested
    @DisplayName("should show messages in correct order when")
    class ShouldShowMessagesInCorrectOrderWhen {

      @Test
      void runningVersionCommand() {
        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          JHLiteCliApp.main(new String[] { "--version" });

          String output = outputCaptor.getOutput();
          assertThat(output).contains(LOADING_COMPLETE_MESSAGE).contains(COMMAND_SUCCESS_MESSAGE);
          int loadingCompletePosition = output.indexOf(LOADING_COMPLETE_MESSAGE);
          int commandSuccessPosition = output.indexOf(COMMAND_SUCCESS_MESSAGE);
          int versionInfoPosition = output.indexOf(VERSION_INFO_PREFIX);
          assertThat(loadingCompletePosition).isPositive();
          assertThat(commandSuccessPosition).isPositive();
          assertThat(versionInfoPosition).isPositive();
          assertThat(loadingCompletePosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(LOADING_COMPLETE_MESSAGE, COMMAND_SUCCESS_MESSAGE))
            .isLessThan(commandSuccessPosition);
          assertThat(commandSuccessPosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(COMMAND_SUCCESS_MESSAGE, VERSION_INFO_PREFIX))
            .isLessThan(versionInfoPosition);
        }
      }

      @Test
      void runningListCommand() {
        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          JHLiteCliApp.main(new String[] { "list" });

          String output = outputCaptor.getOutput();
          assertThat(output).contains(LOADING_COMPLETE_MESSAGE).contains(COMMAND_SUCCESS_MESSAGE);
          int loadingCompletePosition = output.indexOf(LOADING_COMPLETE_MESSAGE);
          int commandSuccessPosition = output.indexOf(COMMAND_SUCCESS_MESSAGE);
          int availableJHipsterLiteModulesPosition = output.indexOf(AVAILABLE_JHIPSTER_LITE_MODULES);
          assertThat(loadingCompletePosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(LOADING_COMPLETE_MESSAGE, COMMAND_SUCCESS_MESSAGE))
            .isLessThan(commandSuccessPosition);
          assertThat(commandSuccessPosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(COMMAND_SUCCESS_MESSAGE, AVAILABLE_JHIPSTER_LITE_MODULES))
            .isLessThan(availableJHipsterLiteModulesPosition);
        }
      }

      @Test
      void runningApplyInitCommandWithoutParameters() {
        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          JHLiteCliApp.main(new String[] { "apply", "init", "--project-path", newTestFolder() });

          String output = outputCaptor.getOutput();
          assertThat(output).contains(LOADING_COMPLETE_MESSAGE).contains(COMMAND_FAILURE_MESSAGE);
          int loadingCompletePosition = output.indexOf(LOADING_COMPLETE_MESSAGE);
          int commandFailurePosition = output.indexOf(COMMAND_FAILURE_MESSAGE);
          int missingRequiredOptionsPosition = output.indexOf(MISSING_REQUIRED_OPTIONS);
          assertThat(loadingCompletePosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(LOADING_COMPLETE_MESSAGE, COMMAND_FAILURE_MESSAGE))
            .isLessThan(commandFailurePosition);
          assertThat(commandFailurePosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(COMMAND_FAILURE_MESSAGE, MISSING_REQUIRED_OPTIONS))
            .isLessThan(missingRequiredOptionsPosition);
        }
      }

      @Test
      void runningApplyInitCommandWithRequiredParameters() {
        try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
          JHLiteCliApp.main(
            new String[] {
              "apply",
              "init",
              "--project-path",
              newTestFolder(),
              "--base-name",
              "jhipsterSampleApplication",
              "--project-name",
              "JHipster Sample Application",
              "--node-package-manager",
              "npm",
            }
          );

          String output = outputCaptor.getOutput();
          assertThat(output).contains(LOADING_COMPLETE_MESSAGE).contains(COMMAND_SUCCESS_MESSAGE);
          int loadingCompletePosition = output.indexOf(LOADING_COMPLETE_MESSAGE);
          int commandSuccessPosition = output.indexOf(COMMAND_SUCCESS_MESSAGE);
          assertThat(loadingCompletePosition)
            .withFailMessage("'%s' message should appear before '%s'".formatted(LOADING_COMPLETE_MESSAGE, COMMAND_SUCCESS_MESSAGE))
            .isLessThan(commandSuccessPosition);
          assertThat(output.trim()).endsWith(COMMAND_SUCCESS_MESSAGE);
        }
      }
    }
  }
}
