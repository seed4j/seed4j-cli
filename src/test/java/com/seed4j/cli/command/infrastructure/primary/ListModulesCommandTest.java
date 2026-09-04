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
  void shouldDescribeListCommandAsListingDependencies() {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    ListModulesCommand command = new ListModulesCommand(modules);

    String[] description = command.spec().usageMessage().description();

    assertThat(description).containsExactly("List available seed4j modules and their dependencies");
  }

  @Test
  void shouldRenderWrappedDependenciesAsExactAlignedLinesInAlphabeticalModuleOrder(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithDependencyWrappingBoundaries());
    ListModulesCommand command = new ListModulesCommand(modules);
    List<String> expectedLines = List.of(
      "Available seed4j modules (3):",
      "  %-7s  %-60s  %s".formatted("Module", "Dependencies", "Description"),
      "  %-7s  %-60s  %s".formatted("alpha", "module:" + "a".repeat(53), "Alpha"),
      "  %-7s  %-60s  ".formatted("", "a".repeat(51) + " (hidden)"),
      "  %-7s  %-60s  ".formatted("", "module:" + "b".repeat(53)),
      "  %-7s  %-60s  ".formatted("", "b".repeat(51) + " (hidden)"),
      "  %-7s  %-60s  %s".formatted("bravo", "module:" + "c".repeat(53), "Bravo"),
      "  %-7s  %-60s  ".formatted("", "c".repeat(60)),
      "  %-7s  %-60s  ".formatted("", "c".repeat(51) + " (hidden)"),
      "  %-7s  %-60s  %s".formatted("charlie", "module:" + "d".repeat(13) + " (hidden), module:" + "e".repeat(13) + " (hidden)", "Charlie")
    );

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    assertThat(output.getOut().lines()).containsExactlyElementsOf(expectedLines);
  }

  @Test
  void shouldAppendHiddenMarkerToModuleDependencyNotVisibleInResources(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithHiddenModuleDependency());
    ListModulesCommand command = new ListModulesCommand(modules);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    assertThat(output).containsPattern("(?m)^\\s{2}visible-module\\s{2,}module:missing-module \\(hidden\\)\\s{2,}Visible module\\s*$");
  }

  private static Seed4JModulesResources resourcesWithDependencyWrappingBoundaries() {
    Seed4JModuleResource alpha = module(
      WrapModuleSlug.ALPHA,
      "Alpha",
      Seed4JModuleOrganization.builder()
        .addDependency(WrapModuleSlug.ALPHA_FIRST_LONG_DEPENDENCY)
        .addDependency(WrapModuleSlug.ALPHA_SECOND_LONG_DEPENDENCY)
        .build()
    );
    Seed4JModuleResource bravo = module(
      WrapModuleSlug.BRAVO,
      "Bravo",
      Seed4JModuleOrganization.builder().addDependency(WrapModuleSlug.BRAVO_MULTICHUNK_DEPENDENCY).build()
    );
    Seed4JModuleResource charlie = module(
      WrapModuleSlug.CHARLIE,
      "Charlie",
      Seed4JModuleOrganization.builder()
        .addDependency(WrapModuleSlug.CHARLIE_FIRST_EXACT_WIDTH_DEPENDENCY)
        .addDependency(WrapModuleSlug.CHARLIE_SECOND_EXACT_WIDTH_DEPENDENCY)
        .build()
    );
    Seed4JHiddenModules hiddenModules = new Seed4JHiddenModules(List.of(), List.of());

    return new Seed4JModulesResources(List.of(charlie, bravo, alpha), hiddenModules);
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

  private enum WrapModuleSlug implements Seed4JModuleSlugFactory {
    ALPHA("alpha"),
    BRAVO("bravo"),
    CHARLIE("charlie"),
    ALPHA_FIRST_LONG_DEPENDENCY("a".repeat(104)),
    ALPHA_SECOND_LONG_DEPENDENCY("b".repeat(104)),
    BRAVO_MULTICHUNK_DEPENDENCY("c".repeat(164)),
    CHARLIE_FIRST_EXACT_WIDTH_DEPENDENCY("d".repeat(13)),
    CHARLIE_SECOND_EXACT_WIDTH_DEPENDENCY("e".repeat(13));

    private final String slug;

    WrapModuleSlug(String slug) {
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
