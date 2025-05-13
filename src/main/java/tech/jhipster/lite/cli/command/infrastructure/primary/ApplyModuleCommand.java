package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterModuleSlug;
import tech.jhipster.lite.module.domain.JHipsterModuleToApply;
import tech.jhipster.lite.module.domain.properties.JHipsterModuleProperties;

@Component
class ApplyModuleCommand implements Callable<Integer> {

  private static final String END_OF_LINE_PARAMETER = "endOfLine";

  private final JHipsterModulesApplicationService modules;
  private CommandSpec spec;

  public ApplyModuleCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  public CommandSpec buildCommandSpec() {
    CommandSpec commandSpec = createSpec();
    addOptions(commandSpec);
    addPositional(commandSpec);

    this.spec = commandSpec;

    return commandSpec;
  }

  private CommandSpec createSpec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("apply");
    spec.usageMessage().description("Apply jhipster-lite specific module");

    return spec;
  }

  private void addOptions(CommandSpec spec) {
    spec.addOption(
      OptionSpec.builder("--project-path")
        .description("Project Path Folder")
        .paramLabel("<projectpath>")
        .defaultValue(".")
        .type(String.class)
        .build()
    );

    spec.addOption(OptionSpec.builder("--commit").description("Commit changes").negatable(true).type(Boolean.class).build());

    spec.addOption(
      OptionSpec.builder("--base-name")
        .description("Project short name (only letters and numbers)")
        .paramLabel("<basename>")
        .type(String.class)
        .required(true)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--project-name")
        .description("Project full name")
        .paramLabel("<projectname>")
        .type(String.class)
        .required(true)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--end-of-line")
        .description("Type of line break (lf or crlf)")
        .paramLabel("<endofline>")
        .type(String.class)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--indentation")
        .description("Number of spaces in indentation")
        .paramLabel("<indentation>")
        .type(Integer.class)
        .build()
    );
  }

  private void addPositional(CommandSpec spec) {
    spec.addPositional(
      PositionalParamSpec.builder()
        .description("Module Slug to be applied")
        .paramLabel("<moduleslug>")
        .type(String.class)
        .required(true)
        .build()
    );
  }

  @Override
  public Integer call() {
    String moduleSlug = moduleSlug();

    JHipsterModuleProperties properties = new JHipsterModuleProperties(projectPath(), commitEnabled(), parameters());
    JHipsterModuleToApply moduleToApply = new JHipsterModuleToApply(new JHipsterModuleSlug(moduleSlug), properties);
    modules.apply(moduleToApply);

    return CommandLine.ExitCode.OK;
  }

  private String moduleSlug() {
    return spec.positionalParameters().getFirst().getValue();
  }

  private String projectPath() {
    return optionValue("--project-path").map(ArgSpec::getValue).map(Object::toString).orElse(".");
  }

  private Optional<OptionSpec> optionValue(String optionName) {
    return spec.options().stream().filter(option -> option.longestName().equals(optionName)).findFirst();
  }

  private boolean commitEnabled() {
    return optionValue("--commit").map(ArgSpec::getValue).map(Object::toString).map(Boolean::parseBoolean).orElse(true);
  }

  private Map<String, Object> parameters() {
    HashMap<String, Object> map = new HashMap<>();

    if (baseName() != null) {
      map.put(JHipsterModuleProperties.PROJECT_BASE_NAME_PARAMETER, baseName());
    }

    if (projectName() != null) {
      map.put(JHipsterModuleProperties.PROJECT_NAME_PARAMETER, projectName());
    }

    if (endOfLine() != null) {
      map.put(END_OF_LINE_PARAMETER, endOfLine());
    }

    if (indentation() != null) {
      map.put(JHipsterModuleProperties.INDENTATION_PARAMETER, indentation());
    }

    return map;
  }

  private String projectName() {
    return optionValue("--project-name").map(ArgSpec::getValue).map(Object::toString).orElse(null);
  }

  private String baseName() {
    return optionValue("--base-name").map(ArgSpec::getValue).map(Object::toString).orElse(null);
  }

  private String endOfLine() {
    return optionValue("--end-of-line").map(ArgSpec::getValue).map(Object::toString).orElse(null);
  }

  private Integer indentation() {
    return optionValue("--indentation").map(ArgSpec::getValue).map(Object::toString).map(Integer::parseInt).orElse(null);
  }
}
