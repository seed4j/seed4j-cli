package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionConfiguration;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeExtensionArtifactsRepository;
import com.seed4j.cli.bootstrap.infrastructure.secondary.FileSystemRuntimeModeConfigurationRepository;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
    return commandLine(modules, projects, standardRuntimeSelection(), "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelection runtimeSelection
  ) {
    return commandLine(modules, projects, runtimeSelection, "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelection runtimeSelection,
    String projectCliVersion,
    String projectSeed4JVersion
  ) {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory);
    Path userHome = Path.of(System.getProperty("user.home"));
    Seed4JCliHome cliHome = new Seed4JCliHome(userHome);
    RuntimeExtensionConfiguration runtimeExtensionConfiguration = cliHome.runtimeExtensionConfiguration();
    RuntimeExtensionApplicationService runtimeExtensionApplicationService = new RuntimeExtensionApplicationService(
      runtimeExtensionConfiguration,
      new FileSystemRuntimeModeConfigurationRepository(cliHome),
      new FileSystemRuntimeExtensionArtifactsRepository()
    );
    ExtensionInstallCommand extensionInstallCommand = new ExtensionInstallCommand(runtimeExtensionApplicationService);
    ExtensionCommand extensionCommand = new ExtensionCommand(extensionInstallCommand);

    Seed4JCommandsFactory seed4JCommandsFactory = new Seed4JCommandsFactory(
      List.of(listModulesCommand, applyModuleCommand, extensionCommand),
      projectCliVersion,
      projectSeed4JVersion,
      runtimeSelection
    );

    return new CommandLine(seed4JCommandsFactory.buildCommandSpec());
  }

  private static RuntimeSelection standardRuntimeSelection() {
    return new RuntimeSelection(RuntimeMode.STANDARD, Optional.empty(), Optional.empty(), Optional.empty());
  }
}
