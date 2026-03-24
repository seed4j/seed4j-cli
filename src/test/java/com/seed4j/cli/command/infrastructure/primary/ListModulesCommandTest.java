package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seed4j.cli.UnitTest;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.Seed4JModuleFactory;
import com.seed4j.module.domain.resource.Seed4JHiddenModules;
import com.seed4j.module.domain.resource.Seed4JModuleOrganization;
import com.seed4j.module.domain.resource.Seed4JModuleRank;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModuleSlugFactory;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@UnitTest
class ListModulesCommandTest {

  @Test
  void shouldAppendHiddenMarkerToModuleDependencyNotVisibleInResources(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithHiddenModuleDependency());
    ListModulesCommand command = new ListModulesCommand(modules);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    assertThat(output).containsPattern("(?m)^\\s{2}visible-module\\s{2,}module:missing-module \\(hidden\\)\\s{2,}Visible module\\s*$");
  }

  private static Seed4JModulesResources resourcesWithHiddenModuleDependency() {
    Seed4JModuleResource visibleModule = module(
      TestModuleSlug.VISIBLE_MODULE,
      "Visible module",
      Seed4JModuleOrganization.builder().addDependency(TestModuleSlug.MISSING_MODULE).build()
    );
    Seed4JHiddenModules hiddenModules = new Seed4JHiddenModules(List.of(), List.of());

    return new Seed4JModulesResources(List.of(visibleModule), hiddenModules);
  }

  private static Seed4JModuleResource module(Seed4JModuleSlugFactory slug, String operation, Seed4JModuleOrganization organization) {
    Seed4JModuleFactory noOpFactory = properties -> null;
    return Seed4JModuleResource.builder()
      .slug(slug)
      .withoutProperties()
      .apiDoc("Test", operation)
      .organization(organization)
      .tags("custom")
      .factory(noOpFactory);
  }

  private enum TestModuleSlug implements Seed4JModuleSlugFactory {
    VISIBLE_MODULE("visible-module"),
    MISSING_MODULE("missing-module");

    private final String slug;

    TestModuleSlug(String slug) {
      this.slug = slug;
    }

    @Override
    public String get() {
      return slug;
    }

    @Override
    public Seed4JModuleRank rank() {
      return Seed4JModuleRank.RANK_D;
    }
  }
}
