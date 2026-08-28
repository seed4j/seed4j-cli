package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
@DisplayName("list")
class ListModulesCommandIntegrationTest {

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Test
  void shouldNotLeakTheExtensionOnlySlugWhenListingModulesInStandardRuntimeMode(CapturedOutput output) {
    String[] args = { "list" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).doesNotContain("runtime-extension-list-only");
  }

  @Test
  void shouldRenderTypedDependenciesWhenModuleHasDependencies(CapturedOutput output) {
    String[] args = { "list" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).containsPattern("(?m)^\\s{2}\\S+\\s{2,}(?:module|feature):\\S+.*\\s{2,}.+$");
  }

  @Test
  void shouldShowDependenciesColumnWithFallbackForListOutput(CapturedOutput output) {
    String[] args = { "list" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .containsPattern("(?m)^\\s{2}Module\\s{2,}Dependencies\\s{2,}Description\\s*$")
      .containsPattern("(?m)^\\s{2}init\\s{2,}-\\s{2,}Init project\\s*$");
  }

  @Test
  void shouldListModules(CapturedOutput output) {
    String[] args = { "list" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .contains("Available seed4j modules")
      .contains("init")
      .contains("Init project")
      .contains("prettier")
      .contains("Format project with prettier");
  }
}
