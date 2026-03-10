package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleSlug;
import com.seed4j.module.domain.Seed4JModuleToApply;
import com.seed4j.module.domain.properties.Seed4JModuleProperties;
import com.seed4j.module.domain.properties.Seed4JPropertyDescription;
import com.seed4j.module.domain.properties.Seed4JPropertyKey;
import com.seed4j.module.domain.properties.Seed4JPropertyType;
import com.seed4j.module.domain.resource.Seed4JModuleOperation;
import com.seed4j.module.domain.resource.Seed4JModulePropertiesDefinition;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.project.application.ProjectsApplicationService;
import com.seed4j.project.domain.ProjectPath;
import com.seed4j.project.domain.history.ModuleParameters;
import java.util.HashMap;
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

  private static final String PROJECT_PATH_OPTION = "--project-path";
  private static final String COMMIT_OPTION = "--commit";

  private final Seed4JModulesApplicationService modules;
  private final Seed4JModuleResource module;
  private final CommandSpec commandSpec;
  private final ProjectsApplicationService projects;

  public ApplyModuleSubCommand(Seed4JModulesApplicationService modules, Seed4JModuleResource module, ProjectsApplicationService projects) {
    this.modules = modules;
    this.module = module;
    this.projects = projects;
    this.commandSpec = buildCommandSpec(module.slug(), module.apiDoc().operation(), module.propertiesDefinition());
  }

  private CommandSpec buildCommandSpec(
    Seed4JModuleSlug moduleSlug,
    Seed4JModuleOperation operation,
    Seed4JModulePropertiesDefinition properties
  ) {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name(moduleSlug.get()).mixinStandardHelpOptions(true);
    spec.usageMessage().description(escape(operation));

    addOptions(spec, properties);

    return spec;
  }

  private String escape(Seed4JModuleOperation operation) {
    return operation.get().replace("%", "%%");
  }

  private void addOptions(CommandSpec spec, Seed4JModulePropertiesDefinition properties) {
    spec.addOption(
      OptionSpec.builder(PROJECT_PATH_OPTION)
        .description("Project Path Folder")
        .paramLabel("<projectpath>")
        .defaultValue(".")
        .type(String.class)
        .build()
    );

    spec.addOption(OptionSpec.builder(COMMIT_OPTION).description("Commit changes").negatable(true).type(Boolean.class).build());

    properties
      .stream()
      .forEach(property ->
        spec.addOption(
          OptionSpec.builder(toDashedFormat(property.key()))
            .description(
              "%s%s".formatted(
                property.description().map(Seed4JPropertyDescription::get).orElse(""),
                property.isMandatory() ? " (required)" : ""
              )
            )
            .paramLabel("<%s%s>".formatted(property.key().get().toLowerCase(), property.isMandatory() ? "*" : ""))
            .type(toOptionType(property.type()))
            .build()
        )
      );
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "There is no Seed4J module using a property with the BOOLEAN type")
  private static Class<?> toOptionType(Seed4JPropertyType type) {
    return switch (type) {
      case BOOLEAN -> boolean.class;
      case INTEGER -> int.class;
      case STRING -> String.class;
    };
  }

  private static String toDashedFormat(Seed4JPropertyKey key) {
    StringBuilder dashed = new StringBuilder("--");
    for (char c : key.get().toCharArray()) {
      if (Character.isUpperCase(c)) {
        dashed.append('-').append(Character.toLowerCase(c));
      } else {
        dashed.append(c);
      }
    }
    return dashed.toString();
  }

  private static String toCamelCaseFormat(String dashed) {
    Assert.notBlank("dashed", dashed);

    String withoutPrefix = dashed.substring(2);
    StringBuilder camelCase = new StringBuilder();

    boolean capitalizeNext = false;
    for (char c : withoutPrefix.toCharArray()) {
      if (c == '-') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        camelCase.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        camelCase.append(c);
      }
    }

    return camelCase.toString();
  }

  public CommandSpec commandSpec() {
    return commandSpec;
  }

  @Override
  public Integer call() {
    Seed4JModuleProperties properties = new Seed4JModuleProperties(projectPath(), commitEnabled(), parameters());
    Seed4JModuleToApply moduleToApply = new Seed4JModuleToApply(new Seed4JModuleSlug(module.slug().get()), properties);
    modules.apply(moduleToApply);

    return ExitCode.OK;
  }

  private String projectPath() {
    return commandSpec.findOption(PROJECT_PATH_OPTION).getValue();
  }

  private boolean commitEnabled() {
    Boolean commit = commandSpec.findOption(COMMIT_OPTION).getValue();

    return commit == null || commit;
  }

  private Map<String, Object> parameters() {
    ModuleParameters moduleParameters = projects
      .getHistory(new ProjectPath(projectPath()))
      .latestProperties()
      .merge(new ModuleParameters(parametersFromOptions()));

    validateRequiredOptions(moduleParameters);

    return moduleParameters.get();
  }

  private Map<String, Object> parametersFromOptions() {
    Map<String, Object> map = new HashMap<>();

    commandSpec
      .options()
      .stream()
      .filter(option -> option.getValue() != null)
      .forEach(option -> map.put(toCamelCaseFormat(option.longestName()), option.getValue()));

    return map;
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
