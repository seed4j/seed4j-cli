package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static com.seed4j.cli.command.infrastructure.primary.CliFixture.setupProjectTestFolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.SystemOutputCaptor;
import com.seed4j.cli.bootstrap.domain.Seed4JCliArguments;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.primary.ModuleCatalogOutput;
import com.seed4j.cli.bootstrap.infrastructure.secondary.SpringBootLocalCliRunner;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = { "spring.config.location=classpath:/cli/config/seed4j-cli-hidden-resources.yml" })
class ExternalConfigTest {

  private static final Path HIDDEN_RESOURCES_CONFIG = Path.of("src/test/resources/cli/config/seed4j-cli-hidden-resources.yml");

  @Autowired
  private ProjectsApplicationService projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Autowired
  private Environment environment;

  @Test
  void shouldLoadExternalConfiguration() {
    assertThat(environment.getProperty("seed4j.hidden-resources.slugs[0]")).isEqualTo("gradle-java");
  }

  @Test
  void shouldHideModuleWhenLoadExternalConfigurationFile() {
    String[] args = { "list" };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getOutput()).doesNotContain("gradle-java");
    }
  }

  @Test
  void shouldHideApplyCommandWhenLoadExternalConfigurationFile() throws IOException {
    Path projectPath = setupProjectTestFolder();
    String[] args = { "apply", "gitpod", "--project-path", projectPath.toString() };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isEqualTo(2);
      assertThat(outputCaptor.getOutput()).contains("Unmatched arguments from index");
    }
  }

  @Test
  void shouldHideApplyCommandsInBashCompletionWhenLoadExternalConfigurationFile() {
    String[] args = { "completion", "bash" };

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = commandLine(modules, projects).execute(args);

      assertThat(exitCode).isZero();
      assertThat(outputCaptor.getOutput()).doesNotContain("gradle-java").doesNotContain("gitpod");
    }
  }

  @Test
  void shouldFindAndLoadExternalConfigurationFileFromCliHomeWhenItExists(@TempDir Path userHome) throws IOException {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    Files.createDirectories(cliHome.configPath().getParent());
    Files.copy(HIDDEN_RESOURCES_CONFIG, cliHome.configPath());
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(Seed4JCliApp.class, cliHome);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.run(new Seed4JCliArguments(new String[] { "list" }));

      assertThat(exitCode).isZero();
      assertThat(ModuleCatalogOutput.slugsIn(outputCaptor.getStandardOutput()))
        .contains("maven-java")
        .doesNotContain("gradle-java", "gitpod");
    }
  }

  @Test
  void shouldRunWithDefaultConfigurationWhenExternalConfigurationFileDoesNotExist(@TempDir Path userHome) {
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    SpringBootLocalCliRunner runner = new SpringBootLocalCliRunner(Seed4JCliApp.class, cliHome);

    try (SystemOutputCaptor outputCaptor = new SystemOutputCaptor()) {
      int exitCode = runner.run(new Seed4JCliArguments(new String[] { "list" }));

      assertThat(exitCode).isZero();
      assertThat(ModuleCatalogOutput.slugsIn(outputCaptor.getStandardOutput())).contains("gradle-java", "gitpod");
    }
  }
}
