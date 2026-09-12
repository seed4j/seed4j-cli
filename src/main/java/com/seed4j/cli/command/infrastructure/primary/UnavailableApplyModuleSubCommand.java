package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import java.util.concurrent.Callable;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

final class UnavailableApplyModuleSubCommand implements Callable<Integer> {

  private final String moduleSlug;
  private final ReleaseChannel channel;
  private final CommandSpec commandSpec;

  UnavailableApplyModuleSubCommand(Seed4JModuleResource module, ReleaseChannel channel) {
    moduleSlug = module.slug().get();
    this.channel = channel;
    commandSpec = new ApplyModuleCommandSpecFactory().create(this, module);
    commandSpec.usageMessage().hidden(true);
  }

  CommandSpec commandSpec() {
    return commandSpec;
  }

  @Override
  public Integer call() {
    commandSpec.commandLine().getErr().print(new UnavailableModuleDiagnosticRenderer().render(moduleSlug, channel));
    commandSpec.commandLine().getErr().flush();
    return ExitCode.USAGE;
  }
}
