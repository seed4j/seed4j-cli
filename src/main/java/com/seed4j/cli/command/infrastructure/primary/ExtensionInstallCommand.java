package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

@Component
class ExtensionInstallCommand implements Callable<Integer> {

  private static final String DISTRIBUTION_ID_OPTION = "--distribution-id";
  private static final String DISTRIBUTION_VERSION_OPTION = "--distribution-version";

  private final RuntimeExtensionApplicationService runtimeExtensionApplicationService;
  private final CommandSpec commandSpec;

  ExtensionInstallCommand(RuntimeExtensionApplicationService runtimeExtensionApplicationService) {
    this.runtimeExtensionApplicationService = runtimeExtensionApplicationService;
    this.commandSpec = buildCommandSpec();
  }

  CommandSpec spec() {
    return commandSpec;
  }

  String name() {
    return "install";
  }

  @Override
  public Integer call() {
    String extensionJarPath = commandSpec.positionalParameters().getFirst().getValue();
    String distributionId = commandSpec.findOption(DISTRIBUTION_ID_OPTION).getValue();
    String distributionVersion = commandSpec.findOption(DISTRIBUTION_VERSION_OPTION).getValue();
    RuntimeExtensionInstallRequest request = new RuntimeExtensionInstallRequest(
      RuntimeExtensionJarPath.from(extensionJarPath),
      new RuntimeDistributionId(distributionId),
      new RuntimeDistributionVersion(distributionVersion)
    );

    try {
      RuntimeExtensionInstallResult installationResult = runtimeExtensionApplicationService.install(request);
      printSuccess(installationResult);
      return ExitCode.OK;
    } catch (InvalidRuntimeConfigurationException runtimeConfigurationException) {
      System.err.println(runtimeConfigurationException.getMessage());
      return ExitCode.SOFTWARE;
    }
  }

  private CommandSpec buildCommandSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("install").mixinStandardHelpOptions(true);
    spec.usageMessage().description("Install active runtime extension");
    spec.addPositional(
      PositionalParamSpec.builder()
        .index("0")
        .paramLabel("<jar*>")
        .description("Path to the runtime extension jar")
        .type(String.class)
        .build()
    );
    spec.addOption(
      OptionSpec.builder(DISTRIBUTION_ID_OPTION)
        .required(true)
        .paramLabel("<id*>")
        .description("Runtime extension distribution id")
        .type(String.class)
        .build()
    );
    spec.addOption(
      OptionSpec.builder(DISTRIBUTION_VERSION_OPTION)
        .required(true)
        .paramLabel("<version*>")
        .description("Runtime extension distribution version")
        .type(String.class)
        .build()
    );

    return spec;
  }

  private void printSuccess(RuntimeExtensionInstallResult installationResult) {
    if (installationResult.runtimeReplaced()) {
      System.out.println("Replaced active runtime extension.");
    }
    System.out.println("Extension runtime installed successfully.");
    System.out.printf("Runtime jar: %s%n", installationResult.extensionJarPath());
    System.out.printf("Metadata: %s%n", installationResult.metadataPath());
    System.out.printf("Config: %s%n", installationResult.configPath());
    System.out.println("Validate installation with:");
    System.out.println("  seed4j --version");
    System.out.println("  seed4j list");
  }
}
