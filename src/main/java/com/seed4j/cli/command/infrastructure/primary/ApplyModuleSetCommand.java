package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.ModuleSetPlanningApplicationService;
import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.RequestedModuleSet;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

@Component
class ApplyModuleSetCommand implements Seed4JCommand {

  private static final String PROJECT_PATH_OPTION = "--project-path";
  private static final String PLAN_OPTION = "--plan";

  private final ModuleSetPlanningApplicationService planning;

  public ApplyModuleSetCommand(ModuleSetPlanningApplicationService planning) {
    Assert.notNull("planning", planning);
    this.planning = planning;
  }

  @Override
  public CommandSpec spec() {
    return new ApplyModuleSetInvocation(planning, name()).spec();
  }

  @Override
  public String name() {
    return "apply-set";
  }

  private static final class ApplyModuleSetInvocation implements Callable<Integer> {

    private final ModuleSetPlanningApplicationService planning;
    private final Map<String, ModuleSetPropertyKey> propertyKeysByOption = new LinkedHashMap<>();
    private final CommandSpec commandSpec;

    private ApplyModuleSetInvocation(ModuleSetPlanningApplicationService planning, String commandName) {
      this.planning = planning;
      commandSpec = buildCommandSpec(commandName);
    }

    private CommandSpec buildCommandSpec(String commandName) {
      CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(commandName).mixinStandardHelpOptions(true);
      spec.usageMessage().description("Plan a set of Seed4J modules without applying changes");
      spec.addPositional(
        PositionalParamSpec.builder().arity("1..*").paramLabel("<module-slug>").type(List.class).auxiliaryTypes(String.class).build()
      );
      spec.addOption(
        OptionSpec.builder(PROJECT_PATH_OPTION)
          .description("Project Path Folder")
          .paramLabel("<projectpath>")
          .defaultValue(".")
          .completionCandidates(List.of("."))
          .type(String.class)
          .build()
      );
      spec.addOption(
        OptionSpec.builder(PLAN_OPTION)
          .description("Print the validated module set plan without applying changes")
          .required(true)
          .type(Boolean.class)
          .build()
      );
      addModulePropertyOptions(spec);

      return spec;
    }

    private void addModulePropertyOptions(CommandSpec spec) {
      ModulePropertyOptionSpecFactory optionsFactory = new ModulePropertyOptionSpecFactory();
      for (ModuleSetPropertyDefinition definition : planning.availableProperties()) {
        OptionSpec option = optionsFactory.moduleSetOption(definition);
        propertyKeysByOption.put(option.longestName(), definition.key());
        spec.addOption(option);
      }
    }

    private CommandSpec spec() {
      return commandSpec;
    }

    @Override
    public Integer call() {
      if (requestedModuleSlugs().isEmpty()) {
        System.err.print("Missing required parameter: '<module-slug>...'\nNo changes were applied.\n");
        return ExitCode.USAGE;
      }
      ModuleSetPlan plan = planning.plan(request());
      if (!plan.valid()) {
        System.err.print(new ApplyModuleSetPlanRenderer().render(plan));
        return ExitCode.USAGE;
      }

      System.out.print(new ApplyModuleSetPlanRenderer().render(plan));
      return ExitCode.OK;
    }

    private ModuleSetPlanningRequest request() {
      RequestedModuleSet requestedModules = new RequestedModuleSet(requestedModuleSlugs().stream().map(ModuleSetSlug::new).toList());
      String projectPath = commandSpec.findOption(PROJECT_PATH_OPTION).getValue();
      return new ModuleSetPlanningRequest(requestedModules, new ModuleSetProjectPath(Path.of(projectPath)), explicitParameters());
    }

    private List<String> requestedModuleSlugs() {
      List<String> rawSlugs = commandSpec.positionalParameters().getFirst().getValue();
      return rawSlugs == null ? List.of() : List.copyOf(rawSlugs);
    }

    private ExplicitModuleSetParameters explicitParameters() {
      Map<ModuleSetPropertyKey, Object> parameters = new LinkedHashMap<>();
      propertyKeysByOption.forEach((optionName, key) -> {
        if (commandSpec.commandLine().getParseResult().hasMatchedOption(optionName)) {
          parameters.put(key, commandSpec.findOption(optionName).getValue());
        }
      });
      return new ExplicitModuleSetParameters(parameters);
    }
  }
}
