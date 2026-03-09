package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

class CliFixture {

  static Path setupProjectTestFolder() throws IOException {
    Path projectPath = Files.createTempDirectory("seed4j-cli-");
    loadGitConfig(projectPath);

    return projectPath;
  }

  static void loadGitConfig(Path project) {
    GitTestUtil.execute(project, "init");
    GitTestUtil.execute(project, "config", "init.defaultBranch", "main");
    GitTestUtil.execute(project, "config", "user.email", "\"test@seed4j.com\"");
    GitTestUtil.execute(project, "config", "user.name", "\"Test\"");
  }

  static CommandLine commandLine(Seed4JModulesApplicationService modules, ProjectsApplicationService projects) {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory);

    Seed4JCommandsFactory seed4JCommandsFactory = new Seed4JCommandsFactory(List.of(listModulesCommand, applyModuleCommand), "1", "2");

    return new CommandLine(seed4JCommandsFactory.buildCommandSpec());
  }
}
