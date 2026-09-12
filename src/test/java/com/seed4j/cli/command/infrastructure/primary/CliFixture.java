package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.BashCompletionInstallApplicationService;
import com.seed4j.cli.command.application.DistributionMetadataApplicationService;
import com.seed4j.cli.command.application.ModuleSetExecutionApplicationService;
import com.seed4j.cli.command.application.ModuleSetPlanningApplicationService;
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
import com.seed4j.cli.command.domain.distribution.DistributionIdentity;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionModuleSlug;
import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.command.domain.distribution.Seed4JDependencyCoordinate;
import com.seed4j.cli.command.domain.distribution.Seed4JModuleAvailability;
import com.seed4j.cli.command.domain.distribution.Seed4JUpstreamCommit;
import com.seed4j.cli.command.infrastructure.secondary.JGitModuleSetGitStateReader;
import com.seed4j.cli.command.infrastructure.secondary.NioModuleSetProjectPathValidator;
import com.seed4j.cli.command.infrastructure.secondary.ProjectsModuleSetPlanningHistoryReader;
import com.seed4j.cli.command.infrastructure.secondary.Seed4JModuleSetCatalog;
import com.seed4j.cli.command.infrastructure.secondary.Seed4JModuleSetModuleApplier;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.infrastructure.secondary.git.GitTestUtil;
import com.seed4j.project.application.ProjectsApplicationService;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import picocli.CommandLine;

class CliFixture {

  private static final Path HISTORY_FILE = Path.of(".seed4j", "modules", "history.json");
  private static final String EMPTY_HISTORY = """
  {
    "actions": []
  }
  """;

  static Path setupProjectTestFolder() throws IOException {
    Path projectPath = setupEmptyProjectTestFolder();
    loadGitConfig(projectPath);

    return projectPath;
  }

  static Path setupEmptyProjectTestFolder() throws IOException {
    Path projectPath = Files.createTempDirectory("seed4j-cli-");
    setupSeed4JHistory(projectPath);

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
      ),
      DistributionMetadata.stable()
    );
  }

  static CommandLine experimentalCommandLine(Seed4JModulesApplicationService modules, ProjectsApplicationService projects) {
    return commandLine(
      modules,
      projects,
      RuntimeDisplay.standard(),
      "3.0.0-experimental.1",
      "2.2.1-main.20260907.055800-SNAPSHOT",
      new BashCompletionInstallApplicationService(script ->
        new com.seed4j.cli.command.domain.BashCompletionInstallationResult(
          new com.seed4j.cli.command.domain.BashCompletionInstallationPath(
            Path.of(System.getProperty("user.home")).resolve(".local/share/bash-completion/completions/seed4j")
          )
        )
      ),
      experimentalMetadata()
    );
  }

  private static DistributionMetadata experimentalMetadata() {
    return new DistributionMetadata(
      new DistributionIdentity(
        ReleaseChannel.EXPERIMENTAL,
        Seed4JDependencyCoordinate.versioned(
          "io.github.renanfranca",
          "seed4j-main-snapshot",
          "2.2.1-main.20260907.055800.0123456789ab-SNAPSHOT"
        ),
        Optional.of(new Seed4JUpstreamCommit("0123456789abcdef0123456789abcdef01234567"))
      ),
      new Seed4JModuleAvailability(Set.of(new DistributionModuleSlug("seed4j-extension")))
    );
  }

  static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    BashCompletionInstallApplicationService bashCompletionInstallApplicationService
  ) {
    return commandLine(
      modules,
      projects,
      RuntimeDisplay.standard(),
      "1",
      "2",
      bashCompletionInstallApplicationService,
      DistributionMetadata.stable()
    );
  }

  private static CommandLine commandLine(
    Seed4JModulesApplicationService modules,
    ProjectsApplicationService projects,
    RuntimeDisplay runtimeDisplay,
    String projectCliVersion,
    String projectSeed4JVersion,
    BashCompletionInstallApplicationService bashCompletionInstallApplicationService,
    DistributionMetadata distributionMetadata
  ) {
    DistributionMetadataApplicationService distribution = new DistributionMetadataApplicationService(() -> distributionMetadata);
    ListModulesCommand listModulesCommand = new ListModulesCommand(modules, distribution);
    ApplyModuleSubCommandsFactory subCommandsFactory = new ApplyModuleSubCommandsFactory(modules, projects);
    ApplyModuleCommand applyModuleCommand = new ApplyModuleCommand(modules, subCommandsFactory, distribution);
    ModuleSetPlanningApplicationService moduleSetPlanningApplicationService = new ModuleSetPlanningApplicationService(
      new Seed4JModuleSetCatalog(modules, () -> distributionMetadata),
      new ProjectsModuleSetPlanningHistoryReader(projects),
      new NioModuleSetProjectPathValidator(),
      new JGitModuleSetGitStateReader(),
      distribution
    );
    ApplyModuleSetCommand applyModuleSetCommand = new ApplyModuleSetCommand(
      moduleSetPlanningApplicationService,
      new ModuleSetExecutionApplicationService(new Seed4JModuleSetModuleApplier(modules))
    );
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
    Seed4JVersionProvider versionProvider = new Seed4JVersionProvider(
      projectCliVersion,
      projectSeed4JVersion,
      runtimeDisplayApplicationService,
      distribution
    );

    Seed4JCommandsFactory seed4JCommandsFactory = new Seed4JCommandsFactory(
      List.of(listModulesCommand, applyModuleCommand, applyModuleSetCommand, extensionCommand, completionCommand),
      versionProvider,
      distribution
    );

    CommandLine commandLine = new CommandLine(seed4JCommandsFactory.buildCommandSpec());
    commandLine.setOut(new PrintWriter(System.out, true));
    commandLine.setErr(new PrintWriter(System.err, true));
    return commandLine;
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
