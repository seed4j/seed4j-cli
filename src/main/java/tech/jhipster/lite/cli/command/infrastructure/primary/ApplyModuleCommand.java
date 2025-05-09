package tech.jhipster.lite.cli.command.infrastructure.primary;

import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.jhipster.lite.module.application.JHipsterModulesApplicationService;
import tech.jhipster.lite.module.domain.JHipsterModuleSlug;
import tech.jhipster.lite.module.domain.JHipsterModuleToApply;
import tech.jhipster.lite.module.domain.properties.JHipsterModuleProperties;

@Component
@Command(name = "apply", description = "Apply jhipster-lite specific module")
class ApplyModuleCommand implements Callable<Integer> {

  private final JHipsterModulesApplicationService modules;

  @Option(names = "--project-path", description = "Project Path Folder", defaultValue = ".")
  private String projectPath;

  @Option(names = "--commit", description = "Commit changes", negatable = true)
  private Boolean commit;

  @Option(names = "--package-name", description = "Base java package", defaultValue = "com.mycompany.myapp")
  private String packageName;

  @Option(names = "--project-name", description = "Project full name", defaultValue = "JHipster Sample Application")
  private String projectName;

  @Option(names = "--base-name", description = "Project short name (only letters and numbers)", defaultValue = "jhipsterSampleApplication")
  private String baseName;

  @Option(names = "--indentation", description = "Number of spaces in indentation", defaultValue = "2")
  private Integer indentation;

  @Parameters(description = "Module Slug to be applied")
  private String moduleSlug;

  public ApplyModuleCommand(JHipsterModulesApplicationService modules) {
    this.modules = modules;
  }

  @Override
  public Integer call() {
    JHipsterModuleProperties properties = new JHipsterModuleProperties(projectPath, commit(), parameters());
    JHipsterModuleToApply moduleToApply = new JHipsterModuleToApply(new JHipsterModuleSlug(moduleSlug), properties);
    modules.apply(moduleToApply);

    return ExitCode.OK;
  }

  private boolean commit() {
    return commit == null || commit;
  }

  private Map<String, Object> parameters() {
    return Map.of(
      JHipsterModuleProperties.BASE_PACKAGE_PARAMETER,
      packageName,
      JHipsterModuleProperties.PROJECT_NAME_PARAMETER,
      projectName,
      JHipsterModuleProperties.PROJECT_BASE_NAME_PARAMETER,
      baseName,
      JHipsterModuleProperties.INDENTATION_PARAMETER,
      indentation
    );
  }
}
