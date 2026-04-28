package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    return commandLine(modules, projects, new CurrentProcessRuntimeSelectionProvider(Map::of), Map::of, "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelection runtimeSelection
  ) {
    return commandLine(modules, projects, () -> runtimeSelection, Map::of, "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelection runtimeSelection,
    Map<String, String> runtimeSystemProperties,
    String cliVersion,
    String seed4JVersion
  ) {
    return commandLine(modules, projects, () -> runtimeSelection, () -> runtimeSystemProperties, cliVersion, seed4JVersion);
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelectionProvider runtimeSelectionProvider
  ) {
    return commandLine(modules, projects, runtimeSelectionProvider, Map::of, "1", "2");
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeSelectionProvider runtimeSelectionProvider,
    RuntimeSystemProperties runtimeSystemProperties,
    String cliVersion,
    String seed4JVersion
  ) {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory);

    Seed4JCommandsFactory seed4JCommandsFactory = new Seed4JCommandsFactory(
      List.of(listModulesCommand, applyModuleCommand),
      cliVersion,
      seed4JVersion,
      runtimeSelectionProvider,
      runtimeSystemProperties
    );

    return new CommandLine(seed4JCommandsFactory.buildCommandSpec());
  }
}
