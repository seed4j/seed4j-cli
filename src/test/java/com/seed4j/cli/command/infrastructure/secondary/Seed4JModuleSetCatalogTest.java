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

  @Test
  void shouldRejectConflictingPropertyTypesAcrossExternalResources() {
    Seed4JModulePropertyDefinition stringProperty = Seed4JModulePropertyDefinition.optionalStringProperty("shared").build();
    Seed4JModulePropertyDefinition integerProperty = Seed4JModulePropertyDefinition.optionalIntegerProperty("shared").build();
    Seed4JModulesApplicationService modules = modulesWith(
      module(TestModuleSlug.STRING_PROPERTY, stringProperty),
      module(TestModuleSlug.INTEGER_PROPERTY, integerProperty)
    );
    Seed4JModuleSetCatalog catalog = new Seed4JModuleSetCatalog(modules);

    assertThatThrownBy(catalog::modules)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Conflicting module set property types for shared: INTEGER, STRING");
  }

  private static Seed4JModulesApplicationService modulesWith(Seed4JModulePropertyDefinition property) {
    return modulesWith(module(TestModuleSlug.BOOLEAN_DEFAULT, property));
  }

  private static Seed4JModuleResource module(TestModuleSlug slug, Seed4JModulePropertyDefinition property) {
    Seed4JModuleFactory noOpFactory = properties -> null;
    return Seed4JModuleResource.builder()
      .slug(slug)
      .propertiesDefinition(Seed4JModulePropertiesDefinition.builder().add(property).build())
      .apiDoc("Test", "Property definition")
      .standalone()
      .tags("test")
      .factory(noOpFactory);
  }

  private static Seed4JModulesApplicationService modulesWith(Seed4JModuleResource... resources) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(new Seed4JModulesResources(List.of(resources), new Seed4JHiddenModules(List.of(), List.of())));
    return modules;
  }

  private enum TestModuleSlug implements Seed4JModuleSlugFactory {
    BOOLEAN_DEFAULT(Seed4JModuleRank.RANK_D, "boolean-default"),
    STRING_PROPERTY(Seed4JModuleRank.RANK_C, "string-property"),
    INTEGER_PROPERTY(Seed4JModuleRank.RANK_B, "integer-property");

    private final Seed4JModuleRank rank;
    private final String slug;

    TestModuleSlug(Seed4JModuleRank rank, String slug) {
      this.rank = rank;
      this.slug = slug;
    }

    @Override
    public String get() {
      return slug;
    }

    @Override
    public Seed4JModuleRank rank() {
      return rank;
    }
  }
}
