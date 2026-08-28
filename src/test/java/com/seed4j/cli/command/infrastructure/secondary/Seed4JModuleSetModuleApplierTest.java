package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.EffectiveModuleSetParameters;
import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetCommitMode;
import com.seed4j.cli.command.domain.moduleset.ModuleSetIntegerParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModuleApplication;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyKey;
import com.seed4j.cli.command.domain.moduleset.ModuleSetSlug;
import com.seed4j.cli.command.domain.moduleset.ModuleSetStringParameterValue;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleToApply;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@UnitTest
class Seed4JModuleSetModuleApplierTest {

  @Test
  void shouldConvertApplicationExactlyToIndividualSeed4JRequest() {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    Seed4JModuleSetModuleApplier applier = new Seed4JModuleSetModuleApplier(modules);
    Path projectPath = Path.of("target", "project");
    ModuleSetModuleApplication application = new ModuleSetModuleApplication(
      new ModuleSetSlug("sample-module"),
      new ModuleSetProjectPath(projectPath),
      ModuleSetCommitMode.DISABLED,
      new EffectiveModuleSetParameters(
        Map.of(
          new ModuleSetPropertyKey("stringValue"),
          new ModuleSetStringParameterValue("value"),
          new ModuleSetPropertyKey("integerValue"),
          new ModuleSetIntegerParameterValue(42),
          new ModuleSetPropertyKey("booleanValue"),
          new ModuleSetBooleanParameterValue(true)
        )
      )
    );

    applier.apply(application);

    ArgumentCaptor<Seed4JModuleToApply> request = ArgumentCaptor.forClass(Seed4JModuleToApply.class);
    verify(modules).apply(request.capture());
    assertThat(request.getValue().slug().get()).isEqualTo("sample-module");
    assertThat(request.getValue().properties().projectFolder().get()).isEqualTo(projectPath.toString());
    assertThat(request.getValue().properties().commitNeeded()).isFalse();
    assertThat(request.getValue().properties().getParameters()).containsExactlyInAnyOrderEntriesOf(
      Map.of("stringValue", "value", "integerValue", 42, "booleanValue", true)
    );
  }
}
