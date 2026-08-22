package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetBooleanParameterValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetModule;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyDefaultValue;
import com.seed4j.cli.command.domain.moduleset.ModuleSetPropertyType;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleFactory;
import com.seed4j.module.domain.resource.Seed4JHiddenModules;
import com.seed4j.module.domain.resource.Seed4JModulePropertiesDefinition;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import com.seed4j.module.domain.resource.Seed4JModuleRank;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModuleSlugFactory;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class Seed4JModuleSetCatalogTest {

  @ParameterizedTest
  @CsvSource({ "true, true", "false, false" })
  void shouldConvertBooleanPropertyDefaults(String literal, boolean expectedValue) {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.optionalBooleanProperty("featureEnabled")
      .defaultValue(literal)
      .build();
    Seed4JModulesApplicationService modules = modulesWith(property);
    Seed4JModuleSetCatalog catalog = new Seed4JModuleSetCatalog(modules);

    List<ModuleSetModule> catalogModules = catalog.modules();

    assertThat(catalogModules)
      .singleElement()
      .satisfies(module ->
        assertThat(module.properties())
          .singleElement()
          .satisfies(definition -> {
            assertThat(definition.type()).isEqualTo(ModuleSetPropertyType.BOOLEAN);
            assertThat(definition.defaultValue()).contains(
              new ModuleSetPropertyDefaultValue(new ModuleSetBooleanParameterValue(expectedValue), literal)
            );
          })
      );
  }

  @Test
  void shouldRejectInvalidBooleanPropertyDefault() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.optionalBooleanProperty("featureEnabled")
      .defaultValue("yes")
      .build();
    Seed4JModulesApplicationService modules = modulesWith(property);
    Seed4JModuleSetCatalog catalog = new Seed4JModuleSetCatalog(modules);

    assertThatThrownBy(catalog::modules)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid BOOLEAN module set property default: yes");
  }

  private static Seed4JModulesApplicationService modulesWith(Seed4JModulePropertyDefinition property) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    Seed4JModuleFactory noOpFactory = properties -> null;
    Seed4JModuleResource module = Seed4JModuleResource.builder()
      .slug(TestModuleSlug.BOOLEAN_DEFAULT)
      .propertiesDefinition(Seed4JModulePropertiesDefinition.builder().add(property).build())
      .apiDoc("Test", "Boolean default")
      .standalone()
      .tags("test")
      .factory(noOpFactory);
    when(modules.resources()).thenReturn(new Seed4JModulesResources(List.of(module), new Seed4JHiddenModules(List.of(), List.of())));
    return modules;
  }

  private enum TestModuleSlug implements Seed4JModuleSlugFactory {
    BOOLEAN_DEFAULT;

    @Override
    public String get() {
      return "boolean-default";
    }

    @Override
    public Seed4JModuleRank rank() {
      return Seed4JModuleRank.RANK_D;
    }
  }
}
