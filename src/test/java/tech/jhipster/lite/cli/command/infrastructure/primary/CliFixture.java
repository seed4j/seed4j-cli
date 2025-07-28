package tech.jhipster.lite.cli.command.infrastructure.primary;

import static tech.jhipster.lite.TestProjects.newTestFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.infrastructure.secondary.git.GitTestUtil;
import tech.jhipster.lite.project.application.ProjectsApplicationService;

class CliFixture {

  static Path setupProjectTestFolder() throws IOException {
    String projectFolder = newTestFolder();
    Path projectPath = Path.of(projectFolder);
    Files.createDirectories(projectPath);
    loadGitConfig(projectPath);

    return projectPath;
  }

  static void loadGitConfig(Path project) {
    GitTestUtil.execute(project, "init");
    GitTestUtil.execute(project, "config", "init.defaultBranch", "main");
    GitTestUtil.execute(project, "config", "user.email", "\"test@jhipster.com\"");
    GitTestUtil.execute(project, "config", "user.name", "\"Test\"");
  }

  static CommandLine commandLine(JHipsterModulesApplicationService modules, ProjectsApplicationService projects) {
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory);

    JHLiteCommandsFactory jhliteCommandsFactory = new JHLiteCommandsFactory(List.of(listModulesCommand, applyModuleCommand), "1", "2");

    return new CommandLine(jhliteCommandsFactory.buildCommandSpec());
  }
}
