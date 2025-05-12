package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterModuleSlug;
import tech.jhipster.lite.module.domain.JHipsterModuleToApply;
import tech.jhipster.lite.module.domain.properties.JHipsterModuleProperties;

@Component
class ApplyModuleCommand implements Callable<Integer> {

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
        .paramLabel("PROJECT_PATH")
        .defaultValue(".")
        .type(String.class)
        .build()
    );

    spec.addOption(OptionSpec.builder("--commit").description("Commit changes").negatable(true).type(Boolean.class).build());

    spec.addOption(
      OptionSpec.builder("--package-name")
        .description("Base java package")
        .paramLabel("PACKAGE_NAME")
        .defaultValue("com.mycompany.myapp")
        .type(String.class)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--project-name")
        .description("Project full name")
        .paramLabel("PROJECT_NAME")
        .defaultValue("JHipster Sample Application")
        .type(String.class)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--base-name")
        .description("Project short name (only letters and numbers)")
        .paramLabel("BASE_NAME")
        .defaultValue("jhipsterSampleApplication")
        .type(String.class)
        .build()
    );

    spec.addOption(
      OptionSpec.builder("--indentation")
        .description("Number of spaces in indentation")
        .paramLabel("INDENTATION")
        .defaultValue("2")
        .type(Integer.class)
        .build()
    );
  }

  private void addPositional(CommandSpec spec) {
    spec.addPositional(
      PositionalParamSpec.builder()
        .description("Module Slug to be applied")
        .paramLabel("MODULE_SLUG")
        .type(String.class)
        .required(true)
        .build()
    );
  }

  @Override
  public Integer call() {
    String moduleSlug = moduleSlug();

    JHipsterModuleProperties properties = new JHipsterModuleProperties(getProjectPath(), isCommitEnabled(), parameters());
    JHipsterModuleToApply moduleToApply = new JHipsterModuleToApply(new JHipsterModuleSlug(moduleSlug), properties);
    modules.apply(moduleToApply);

    return CommandLine.ExitCode.OK;
  }

  private Map<String, Object> parameters() {
    return Map.of(
      JHipsterModuleProperties.BASE_PACKAGE_PARAMETER,
      packageName(),
      JHipsterModuleProperties.PROJECT_NAME_PARAMETER,
      projectName(),
      JHipsterModuleProperties.PROJECT_BASE_NAME_PARAMETER,
      baseName(),
      JHipsterModuleProperties.INDENTATION_PARAMETER,
      indentation()
    );
  }

  private String getProjectPath() {
    return optionValue("--project-path", ".");
  }

  private String optionValue(String optionName, String defaultValue) {
    return spec
      .options()
      .stream()
      .filter(option -> option.longestName().equals(optionName))
      .findFirst()
      .map(option -> option.getValue() != null ? option.getValue().toString() : defaultValue)
      .orElse(defaultValue);
  }

  private boolean isCommitEnabled() {
    Optional<OptionSpec> commitOption = spec.options().stream().filter(option -> option.longestName().equals("--commit")).findFirst();

    if (commitOption.isPresent() && commitOption.get().getValue() != null) {
      return commitOption.get().getValue();
    }
    return true;
  }

  private String packageName() {
    return optionValue("--package-name", "com.mycompany.myapp");
  }

  private String projectName() {
    return optionValue("--project-name", "JHipster Sample Application");
  }

  private String baseName() {
    return optionValue("--base-name", "jhipsterSampleApplication");
  }

  private Integer indentation() {
    String value = optionValue("--indentation", "2");
    return Integer.parseInt(value);
  }

  private String moduleSlug() {
    return spec.positionalParameters().getFirst().getValue().toString();
  }
}
