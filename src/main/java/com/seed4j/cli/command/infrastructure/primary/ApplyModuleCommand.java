package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.DistributionMetadataApplicationService;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import java.util.Comparator;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ApplyModuleCommand implements Seed4JCommand {

  private final Seed4JModulesApplicationService modules;
  private final ApplyModuleSubCommandsFactory subCommandsFactory;
  private final DistributionMetadataApplicationService distribution;

  public ApplyModuleCommand(
    Seed4JModulesApplicationService modules,
    ApplyModuleSubCommandsFactory subCommandsFactory,
    DistributionMetadataApplicationService distribution
  ) {
    this.modules = modules;
    this.subCommandsFactory = subCommandsFactory;
    this.distribution = distribution;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = createSpec();
    addModuleSlugSubcommands(spec);

    return spec;
  }

  @Override
  public String name() {
    return "apply";
  }

  private CommandSpec createSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("apply").mixinStandardHelpOptions(true);
    spec.usageMessage().description("Apply seed4j specific module");

    return spec;
  }

  private void addModuleSlugSubcommands(CommandSpec spec) {
    modules
      .resources()
      .stream()
      .sorted(byModuleSlug())
      .forEach(module -> addModuleSubcommand(spec, module));
  }

  private void addModuleSubcommand(CommandSpec spec, Seed4JModuleResource module) {
    if (distribution.metadata().moduleAvailability().available(module.slug().get())) {
      spec.addSubcommand(module.slug().get(), subCommandsFactory.create(module).commandSpec());
      return;
    }

    spec.addSubcommand(
      module.slug().get(),
      subCommandsFactory.createUnavailable(module, distribution.metadata().identity().channel()).commandSpec()
    );
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(moduleResource -> moduleResource.slug().get());
  }
}
