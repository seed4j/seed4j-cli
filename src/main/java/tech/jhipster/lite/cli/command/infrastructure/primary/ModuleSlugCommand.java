package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterModuleSlug;
import tech.jhipster.lite.module.domain.JHipsterModuleToApply;
import tech.jhipster.lite.module.domain.properties.JHipsterModuleProperties;

class ModuleSlugCommand implements Callable<Integer> {

  private static final String END_OF_LINE_PARAMETER = "endOfLine";

  private final JHipsterModulesApplicationService modules;
  private final String moduleSlug;
  private final CommandSpec spec;

  public ModuleSlugCommand(JHipsterModulesApplicationService modules, String moduleSlug) {
    this.modules = modules;
    this.moduleSlug = moduleSlug;
    this.spec = buildCommandSpec(moduleSlug);
  }

  private CommandSpec buildCommandSpec(String moduleSlug) {
    CommandSpec commandSpec = CommandSpec.wrapWithoutInspection(this).name(moduleSlug).mixinStandardHelpOptions(true);
    commandSpec.usageMessage().description("Init project");

    addOptions(commandSpec);

    return commandSpec;
  }

  private void addOptions(CommandSpec commandSpec) {
    commandSpec.addOption(
      OptionSpec.builder("--project-path")
        .description("Project Path Folder")
        .paramLabel("<projectpath>")
        .defaultValue(".")
        .type(String.class)
        .build()
    );

    commandSpec.addOption(OptionSpec.builder("--commit").description("Commit changes").negatable(true).type(Boolean.class).build());

    commandSpec.addOption(
      OptionSpec.builder("--base-name")
        .description("Project short name (only letters and numbers)")
        .paramLabel("<basename>")
        .type(String.class)
        .required(true)
        .build()
    );

    commandSpec.addOption(
      OptionSpec.builder("--project-name")
        .description("Project full name")
        .paramLabel("<projectname>")
        .type(String.class)
        .required(true)
        .build()
    );

    commandSpec.addOption(
      OptionSpec.builder("--end-of-line")
        .description("Type of line break (lf or crlf)")
        .paramLabel("<endofline>")
        .type(String.class)
        .build()
    );

    commandSpec.addOption(
      OptionSpec.builder("--indentation")
        .description("Number of spaces in indentation")
        .paramLabel("<indentation>")
        .type(Integer.class)
        .build()
    );
  }

  public CommandSpec commandSpec() {
    return spec;
  }

  @Override
  public Integer call() {
    JHipsterModuleProperties properties = new JHipsterModuleProperties(projectPath(), commitEnabled(), parameters());
    JHipsterModuleToApply moduleToApply = new JHipsterModuleToApply(new JHipsterModuleSlug(moduleSlug), properties);
    modules.apply(moduleToApply);

    return ExitCode.OK;
  }

  private String projectPath() {
    return spec.findOption("--project-path").getValue();
  }

  private boolean commitEnabled() {
    Boolean commit = spec.findOption("--commit").getValue();

    return commit == null || commit;
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
    return spec.findOption("--project-name").getValue();
  }

  private String baseName() {
    return spec.findOption("--base-name").getValue();
  }

  private String endOfLine() {
    return spec.findOption("--end-of-line").getValue();
  }

  private Integer indentation() {
    return spec.findOption("--indentation").getValue();
  }
}
