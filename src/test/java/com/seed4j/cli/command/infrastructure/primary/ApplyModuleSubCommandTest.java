package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleSlug;
import com.seed4j.module.domain.properties.Seed4JPropertyKey;
import com.seed4j.module.domain.properties.Seed4JPropertyType;
import com.seed4j.module.domain.resource.Seed4JModuleApiDoc;
import com.seed4j.module.domain.resource.Seed4JModuleOperation;
import com.seed4j.module.domain.resource.Seed4JModulePropertiesDefinition;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.project.application.ProjectsApplicationService;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Model.CommandSpec;

class ApplyModuleSubCommandTest {

  @Test
  void shouldBuildCommandSpecWithPropertyWithoutCandidates() {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    ProjectsApplicationService projects = mock(ProjectsApplicationService.class);

    Seed4JModuleResource module = mock(Seed4JModuleResource.class);
    when(module.slug()).thenReturn(new Seed4JModuleSlug("test-module"));

    Seed4JModuleApiDoc apiDoc = mock(Seed4JModuleApiDoc.class);
    when(apiDoc.operation()).thenReturn(new Seed4JModuleOperation("Test operation"));
    when(module.apiDoc()).thenReturn(apiDoc);

    Seed4JModulePropertyDefinition property = mock(Seed4JModulePropertyDefinition.class);
    when(property.key()).thenReturn(new Seed4JPropertyKey("unknownProperty"));
    when(property.type()).thenReturn(Seed4JPropertyType.STRING);
    when(property.isMandatory()).thenReturn(false);
    when(property.defaultValue()).thenReturn(Optional.empty());

    Seed4JModulePropertiesDefinition properties = mock(Seed4JModulePropertiesDefinition.class);
    when(properties.stream()).thenAnswer(i -> Stream.of(property));
    when(module.propertiesDefinition()).thenReturn(properties);

    ApplyModuleSubCommand command = new ApplyModuleSubCommand(modules, module, projects);
    CommandSpec spec = command.commandSpec();

    assertThat(spec).isNotNull();
    assertThat(spec.findOption("unknown-property").description()[0]).isEqualTo("");
  }
}
