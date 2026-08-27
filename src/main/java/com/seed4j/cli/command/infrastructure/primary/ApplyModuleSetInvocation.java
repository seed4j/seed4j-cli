package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.application.ModuleSetExecutionApplicationService;
import com.seed4j.cli.command.application.ModuleSetPlanningApplicationService;
import com.seed4j.cli.command.domain.moduleset.ExplicitModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetExecutionResult;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlan;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPlanningRequest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefinition;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.cli.command.domain.moduleset.RequestedModuleSet;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

final class ApplyModuleSetInvocation implements Callable<Integer> {

  private static final String PROJECT_PATH_OPTION = ProjectPathOptionSpecFactory.OPTION_NAME;
  private static final String COMMIT_OPTION = "--commit";
  private static final String PLAN_OPTION = "--plan";

  private final ModuleSetPlanningApplicationService planning;
  private final ModuleSetExecutionApplicationService execution;
  private final Map<String, ModuleSetPropertyDefinition> propertyDefinitionsByOption = new LinkedHashMap<>();
  private final CommandSpec commandSpec;

  ApplyModuleSetInvocation(
    ModuleSetPlanningApplicationService planning,
    ModuleSetExecutionApplicationService execution,
    String commandName
  ) {
    this.planning = planning;
    this.execution = execution;
    commandSpec = buildCommandSpec(commandName);
  }

  private CommandSpec buildCommandSpec(String commandName) {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(commandName).mixinStandardHelpOptions(true);
    spec.usageMessage().description("Apply a validated set of Seed4J modules sequentially");
    spec.addPositional(
      PositionalParamSpec.builder().arity("1..*").paramLabel("<module-slug>").type(List.class).auxiliaryTypes(String.class).build()
    );
    spec.addOption(new ProjectPathOptionSpecFactory().create());
    spec.addOption(
      OptionSpec.builder(COMMIT_OPTION)
        .description("Initialize Git if needed and create one commit per succeeded module; --no-commit skips Git init and commits")
        .negatable(true)
        .type(Boolean.class)
        .build()
    );
    spec.addOption(
      OptionSpec.builder(PLAN_OPTION)
        .description("Print the validated module set plan without applying changes")
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
      propertyDefinitionsByOption.put(option.longestName(), definition);
      spec.addOption(option);
    }
  }

  CommandSpec spec() {
    return commandSpec;
  }

  @Override
  public Integer call() {
    if (requestedModuleSlugs().isEmpty()) {
      printError("Missing required parameter: '<module-slug>...'\nNo changes were applied.\n");
      return ExitCode.USAGE;
    }
    return planAndRun();
  }

  private int planAndRun() {
    ModuleSetPlan plan;
    try {
      plan = planning.plan(request());
    } catch (RuntimeException exception) {
      printError("ERROR: Unable to complete module set preflight.\nNo changes were applied.\n");
      return ExitCode.SOFTWARE;
    }
    return run(plan);
  }

  private int run(ModuleSetPlan plan) {
    if (!plan.valid()) {
      printError(new ApplyModuleSetPlanRenderer().render(plan));
      return ExitCode.USAGE;
    }

    printError(new ApplyModuleSetWarningRenderer().render(plan));
    boolean planOnly = Boolean.TRUE.equals(commandSpec.findOption(PLAN_OPTION).getValue());
    if (planOnly) {
      printOutput(new ApplyModuleSetPlanRenderer().render(plan));
      return ExitCode.OK;
    }
    return execute(plan);
  }

  private int execute(ModuleSetPlan plan) {
    printOutput(new ApplyModuleSetExecutionPreflightRenderer().render(plan));
    ApplyModuleSetExecutionRenderer renderer = new ApplyModuleSetExecutionRenderer(plan);
    printOutput(renderer.start());
    ModuleSetExecutionResult result = execution.execute(plan, event -> printOutput(renderer.event(event)));
    printOutput(renderer.summary(result));
    if (renderer.failed(result)) {
      printError(renderer.failure(result));
      return ExitCode.SOFTWARE;
    }
    return ExitCode.OK;
  }

  private void printOutput(String content) {
    commandSpec.commandLine().getOut().print(content);
    commandSpec.commandLine().getOut().flush();
  }

  private void printError(String content) {
    commandSpec.commandLine().getErr().print(content);
    commandSpec.commandLine().getErr().flush();
  }

  private ModuleSetPlanningRequest request() {
    RequestedModuleSet requestedModules = new RequestedModuleSet(requestedModuleSlugs().stream().map(ModuleSetSlug::new).toList());
    String projectPath = commandSpec.findOption(PROJECT_PATH_OPTION).getValue();
    return new ModuleSetPlanningRequest(
      requestedModules,
      new ModuleSetProjectPath(Path.of(projectPath)),
      explicitParameters(),
      commitMode()
    );
  }

  private ModuleSetCommitMode commitMode() {
    Boolean commit = commandSpec.findOption(COMMIT_OPTION).getValue();
    return Boolean.FALSE.equals(commit) ? ModuleSetCommitMode.DISABLED : ModuleSetCommitMode.ENABLED;
  }

  private List<String> requestedModuleSlugs() {
    List<String> rawSlugs = commandSpec.positionalParameters().getFirst().getValue();
    return rawSlugs == null ? List.of() : List.copyOf(rawSlugs);
  }

  private ExplicitModuleSetParameters explicitParameters() {
    Map<ModuleSetPropertyKey, ModuleSetParameterValue> parameters = new LinkedHashMap<>();
    propertyDefinitionsByOption.forEach((optionName, definition) -> {
      if (commandSpec.commandLine().getParseResult().hasMatchedOption(optionName)) {
        parameters.put(definition.key(), parameterValue(definition, commandSpec.findOption(optionName).getValue()));
      }
    });
    return new ExplicitModuleSetParameters(parameters);
  }

  private static ModuleSetParameterValue parameterValue(ModuleSetPropertyDefinition definition, Object value) {
    return switch (definition.type()) {
      case STRING -> new ModuleSetStringParameterValue((String) value);
      case INTEGER -> new ModuleSetIntegerParameterValue((Integer) value);
      case BOOLEAN -> new ModuleSetBooleanParameterValue((Boolean) value);
    };
  }
}
