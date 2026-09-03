package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleToApply;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import com.seed4j.module.domain.properties.Seed4JPropertyKey;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ModuleParameters;
import com.seed4j.project.domain.history.ProjectHistory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

class ApplyModuleSubCommand implements Callable<Integer> {

  private static final String PROJECT_PATH_OPTION = ApplyModuleCommandSpecFactory.PROJECT_PATH_OPTION;
  private static final String COMMIT_OPTION = ApplyModuleCommandSpecFactory.COMMIT_OPTION;
  private static final String PLAN_OPTION = ApplyModuleCommandSpecFactory.PLAN_OPTION;
  private final Seed4JModulesApplicationService modules;
  private final Seed4JModuleResource module;
  private final CommandSpec commandSpec;
  private final ProjectsApplicationService projects;

  public ApplyModuleSubCommand(Seed4JModulesApplicationService modules, Seed4JModuleResource module, ProjectsApplicationService projects) {
    this.modules = modules;
    this.module = module;
    this.projects = projects;
    this.commandSpec = new ApplyModuleCommandSpecFactory().create(this, module);
  }

  static String toDashedFormat(Seed4JPropertyKey key) {
    return ModulePropertyOptionSpecFactory.toDashedFormat(key);
  }

  public CommandSpec commandSpec() {
    return commandSpec;
  }

  @Override
  public Integer call() {
    String projectPath = projectPath();
    ProjectHistory history = projects.getHistory(new ProjectPath(projectPath));
    return executionMode() == ApplyModuleExecutionMode.PLAN ? plan(projectPath, history) : apply(projectPath, history);
  }

  private Integer plan(String projectPath, ProjectHistory history) {
    ResolvedModuleParameters resolvedParameters = new ApplyModuleParameterResolver().resolve(
      module.propertiesDefinition(),
      parametersFromOptions(),
      history.latestProperties().get()
    );
    System.out.print(new ApplyModulePlanRenderer().render(module.slug().get(), projectPath, dependencyPlan(history), resolvedParameters));
    return ExitCode.OK;
  }

  private ApplyModuleDependencyPlan dependencyPlan(ProjectHistory history) {
    return new ApplyModuleDependencyPlanner().plan(module, modules, history);
  }

  private Integer apply(String projectPath, ProjectHistory history) {
    ApplyModuleDependencyPlan dependencyPlan = dependencyPlan(history);
    if (dependencyPlan.notReady()) {
      return missingDependencies(dependencyPlan);
    }

    ModuleParameters parameters = history.latestProperties().merge(new ModuleParameters(parametersFromOptions()));
    validateRequiredOptions(parameters);
    applyModule(projectPath, parameters);
    return ExitCode.OK;
  }

  private Integer missingDependencies(ApplyModuleDependencyPlan dependencyPlan) {
    System.err.print(new MissingApplyModuleDependenciesRenderer().render(module.slug().get(), dependencyPlan));
    return ExitCode.USAGE;
  }

  private void applyModule(String projectPath, ModuleParameters parameters) {
    Seed4JModuleProperties properties = new Seed4JModuleProperties(projectPath, commitEnabled(), parameters.get());
    modules.apply(new Seed4JModuleToApply(module.slug(), properties));
  }

  private ApplyModuleExecutionMode executionMode() {
    Boolean plan = commandSpec.findOption(PLAN_OPTION).getValue();

    if (Boolean.TRUE.equals(plan)) {
      return ApplyModuleExecutionMode.PLAN;
    }

    return ApplyModuleExecutionMode.APPLY;
  }

  private String projectPath() {
    return commandSpec.findOption(PROJECT_PATH_OPTION).getValue();
  }

  private boolean commitEnabled() {
    Boolean commit = commandSpec.findOption(COMMIT_OPTION).getValue();

    return commit == null || commit;
  }

  private Map<String, Object> parametersFromOptions() {
    return module
      .propertiesDefinition()
      .stream()
      .filter(property -> optionValue(property) != null)
      .collect(Collectors.toMap(property -> property.key().get(), this::optionValue));
  }

  private Object optionValue(Seed4JModulePropertyDefinition property) {
    return commandSpec.findOption(toDashedFormat(property.key())).getValue();
  }

  private void validateRequiredOptions(ModuleParameters moduleParameters) {
    List<OptionSpec> missingOptions = module
      .propertiesDefinition()
      .stream()
      .filter(Seed4JModulePropertyDefinition::isMandatory)
      .filter(property -> !moduleParameters.get().containsKey(property.key().get()))
      .map(property -> commandSpec.findOption(toDashedFormat(property.key())))
      .toList();

    if (!missingOptions.isEmpty()) {
      String missingOptionsDescription = missingOptions
        .stream()
        .map(option -> "'%s=%s'".formatted(option.longestName(), option.paramLabel()))
        .collect(Collectors.joining(", "));

      throw new MissingParameterException(
        commandSpec.commandLine(),
        missingOptions.stream().map(ArgSpec.class::cast).toList(),
        "Missing required options: %s".formatted(missingOptionsDescription)
      );
    }
  }
}
