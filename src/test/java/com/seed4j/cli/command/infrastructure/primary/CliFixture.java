package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.BashCompletionInstallApplicationService;
import com.seed4j.cli.command.application.RuntimeDisplayApplicationService;
import com.seed4j.cli.command.application.RuntimeExtensionInstallApplicationService;
import com.seed4j.cli.command.application.RuntimeExtensionModeApplicationService;
import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.command.domain.RuntimeExtensionInstalledJarPath;
import com.seed4j.cli.command.domain.RuntimeExtensionMetadataPath;
import com.seed4j.cli.command.domain.RuntimeExtensionModeSwitchResult;
import com.seed4j.cli.command.domain.RuntimeExtensionReplacementStatus;
import com.seed4j.cli.command.domain.RuntimeModeConfigurationPath;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

class CliFixture {

  private static final Path HISTORY_FILE = Path.of(".seed4j", "modules", "history.json");
  private static final String EMPTY_HISTORY = """
    {
      "actions": []
    }
    """;

  static Path setupProjectTestFolder() throws IOException {
    Path projectPath = Files.createTempDirectory("seed4j-cli-");
    setupSeed4JHistory(projectPath);
    loadGitConfig(projectPath);

    return projectPath;
  }

  private static void setupSeed4JHistory(Path projectPath) throws IOException {
    Path historyFile = projectPath.resolve(HISTORY_FILE);
    Files.createDirectories(historyFile.getParent());
    Files.writeString(historyFile, EMPTY_HISTORY);
  }

  static void loadGitConfig(Path project) {
    GitTestUtil.execute(project, "init");
    GitTestUtil.execute(project, "config", "init.defaultBranch", "main");
    GitTestUtil.execute(project, "config", "user.email", "\"test@seed4j.com\"");
    GitTestUtil.execute(project, "config", "user.name", "\"Test\"");
  }

  static CommandLine commandLine(Seed4JModulesApplicationService modules, ProjectsApplicationService projects) {
    return commandLine(modules, projects, RuntimeDisplay.standard(), "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeDisplay runtimeDisplay
  ) {
    return commandLine(modules, projects, runtimeDisplay, "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeDisplay runtimeDisplay,
    String projectCliVersion,
    String projectSeed4JVersion
  ) {
    return commandLine(
      modules,
      projects,
      runtimeDisplay,
      projectCliVersion,
      projectSeed4JVersion,
      new BashCompletionInstallApplicationService(script ->
        new com.seed4j.cli.command.domain.BashCompletionInstallationResult(
          new com.seed4j.cli.command.domain.BashCompletionInstallationPath(
            Path.of(System.getProperty("user.home")).resolve(".local/share/bash-completion/completions/seed4j")
          )
        )
      )
    );
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    BashCompletionInstallApplicationService bashCompletionInstallApplicationService
  ) {
    return commandLine(modules, projects, RuntimeDisplay.standard(), "1", "2", bashCompletionInstallApplicationService);
  }

  private static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeDisplay runtimeDisplay,
    String projectCliVersion,
    String projectSeed4JVersion,
    BashCompletionInstallApplicationService bashCompletionInstallApplicationService
  ) {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory);
    RuntimeExtensionInstallApplicationService runtimeExtensionInstallApplicationService = new RuntimeExtensionInstallApplicationService(
      request ->
        new RuntimeExtensionInstallResult(
          new RuntimeExtensionInstalledJarPath(Path.of("extension.jar")),
          new RuntimeExtensionMetadataPath(Path.of("metadata.yml")),
          new RuntimeModeConfigurationPath(Path.of("config.yml")),
          RuntimeExtensionReplacementStatus.NEW_INSTALLATION
        )
    );
    ExtensionInstallCommand extensionInstallCommand = new ExtensionInstallCommand(runtimeExtensionInstallApplicationService);
    RuntimeExtensionModeApplicationService runtimeExtensionModeApplicationService = new RuntimeExtensionModeApplicationService(
      new RuntimeExtensionModeSwitcherStub()
    );
    ExtensionCommand extensionCommand = new ExtensionCommand(
      extensionInstallCommand,
      new ExtensionEnableCommand(runtimeExtensionModeApplicationService),
      new ExtensionDisableCommand(runtimeExtensionModeApplicationService)
    );
    CompletionCommand completionCommand = new CompletionCommand(new BashCompletionCommand(bashCompletionInstallApplicationService));
    RuntimeDisplayApplicationService runtimeDisplayApplicationService = new RuntimeDisplayApplicationService(() -> runtimeDisplay);

    Seed4JCommandsFactory seed4JCommandsFactory = new Seed4JCommandsFactory(
      List.of(listModulesCommand, applyModuleCommand, extensionCommand, completionCommand),
      projectCliVersion,
      projectSeed4JVersion,
      runtimeDisplayApplicationService
    );

    return new CommandLine(seed4JCommandsFactory.buildCommandSpec());
  }

  private static final class RuntimeExtensionModeSwitcherStub implements com.seed4j.cli.command.domain.RuntimeExtensionModeSwitcher {

    @Override
    public RuntimeExtensionModeSwitchResult enable() {
      return new RuntimeExtensionModeSwitchResult(Path.of("config.yml"));
    }

    @Override
    public RuntimeExtensionModeSwitchResult disable() {
      return new RuntimeExtensionModeSwitchResult(Path.of("config.yml"));
    }
  }
}
