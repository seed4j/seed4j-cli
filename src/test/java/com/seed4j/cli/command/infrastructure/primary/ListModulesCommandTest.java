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
import java.util.regex.Pattern;
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
  void shouldWrapDependenciesColumnAtSixtyCharactersWithoutRepeatingDescription(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithLongDependenciesForWrap());
    ListModulesCommand command = new ListModulesCommand(modules);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    assertThat(output)
      .containsPattern("(?m)^\\s{2}module-a\\s{2,}module:first-dependency, module:second-dependency\\s{2,}Module A\\s*$")
      .containsPattern("(?m)^\\s{2}\\s+module:third-dependency\\s{2,}$")
      .doesNotContainPattern("(?m)^\\s{2}\\s+module:third-dependency\\s{2,}Module A\\s*$");
  }

  @Test
  void shouldHardWrapSingleDependencyTokenLongerThanSixtyCharacters(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithSingleLongDependencyTokenForHardWrap());
    ListModulesCommand command = new ListModulesCommand(modules);

    int exitCode = command.call();
    String longDependencyToken = "module:" + WrapModuleSlug.EXTREMELY_LONG_DEPENDENCY.get();
    String firstChunk = longDependencyToken.substring(0, 60);
    String remainingChunk = longDependencyToken.substring(60) + ", module:short-dependency";

    assertThat(exitCode).isZero();
    assertThat(output)
      .containsPattern("(?m)^\\s{2}hard-wrap-module\\s{2,}" + Pattern.quote(firstChunk) + "\\s{2,}Hard wrap module\\s*$")
      .containsPattern("(?m)^\\s{2}\\s+" + Pattern.quote(remainingChunk) + "\\s{2,}$")
      .doesNotContainPattern("(?m)^\\s{2}\\s+" + Pattern.quote(remainingChunk) + "\\s{2,}Hard wrap module\\s*$");
  }

  @Test
  void shouldHardWrapConsecutiveLongDependencyTokensWithoutBlankContinuationLine(CapturedOutput output) {
    Seed4JModulesApplicationService modules = mock(Seed4JModulesApplicationService.class);
    when(modules.resources()).thenReturn(resourcesWithConsecutiveLongDependencyTokensForHardWrap());
    ListModulesCommand command = new ListModulesCommand(modules);

    int exitCode = command.call();

    assertThat(exitCode).isZero();
    assertThat(output)
      .containsPattern("(?m)^\\s{2}double-hard-wrap-module\\s{2,}.+\\s{2,}Double hard wrap module\\s*$")
      .containsPattern("(?m)^\\s{2}\\s+.+\\s{2,}$")
      .doesNotContainPattern("(?m)^\\s{2}\\s+\\s{2,}$");
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

  private static Seed4JModulesResources resourcesWithLongDependenciesForWrap() {
    Seed4JModuleResource moduleA = module(
      WrapModuleSlug.MODULE_A,
      "Module A",
      Seed4JModuleOrganization.builder()
        .addDependency(WrapModuleSlug.FIRST_DEPENDENCY)
        .addDependency(WrapModuleSlug.SECOND_DEPENDENCY)
        .addDependency(WrapModuleSlug.THIRD_DEPENDENCY)
        .build()
    );
    Seed4JModuleResource firstDependency = module(WrapModuleSlug.FIRST_DEPENDENCY, "First dependency", Seed4JModuleOrganization.STANDALONE);
    Seed4JModuleResource secondDependency = module(
      WrapModuleSlug.SECOND_DEPENDENCY,
      "Second dependency",
      Seed4JModuleOrganization.STANDALONE
    );
    Seed4JModuleResource thirdDependency = module(WrapModuleSlug.THIRD_DEPENDENCY, "Third dependency", Seed4JModuleOrganization.STANDALONE);
    Seed4JHiddenModules hiddenModules = new Seed4JHiddenModules(List.of(), List.of());

    return new Seed4JModulesResources(List.of(moduleA, firstDependency, secondDependency, thirdDependency), hiddenModules);
  }

  private static Seed4JModulesResources resourcesWithSingleLongDependencyTokenForHardWrap() {
    Seed4JModuleResource hardWrapModule = module(
      WrapModuleSlug.HARD_WRAP_MODULE,
      "Hard wrap module",
      Seed4JModuleOrganization.builder()
        .addDependency(WrapModuleSlug.EXTREMELY_LONG_DEPENDENCY)
        .addDependency(WrapModuleSlug.SHORT_DEPENDENCY)
        .build()
    );
    Seed4JModuleResource longDependency = module(
      WrapModuleSlug.EXTREMELY_LONG_DEPENDENCY,
      "Very long dependency",
      Seed4JModuleOrganization.STANDALONE
    );
    Seed4JModuleResource shortDependency = module(WrapModuleSlug.SHORT_DEPENDENCY, "Short dependency", Seed4JModuleOrganization.STANDALONE);
    Seed4JHiddenModules hiddenModules = new Seed4JHiddenModules(List.of(), List.of());

    return new Seed4JModulesResources(List.of(hardWrapModule, longDependency, shortDependency), hiddenModules);
  }

  private static Seed4JModulesResources resourcesWithConsecutiveLongDependencyTokensForHardWrap() {
    Seed4JModuleResource hardWrapModule = module(
      WrapModuleSlug.DOUBLE_HARD_WRAP_MODULE,
      "Double hard wrap module",
      Seed4JModuleOrganization.builder()
        .addDependency(WrapModuleSlug.EXTREMELY_LONG_DEPENDENCY)
        .addDependency(WrapModuleSlug.SECOND_EXTREMELY_LONG_DEPENDENCY)
        .build()
    );
    Seed4JModuleResource firstLongDependency = module(
      WrapModuleSlug.EXTREMELY_LONG_DEPENDENCY,
      "First very long dependency",
      Seed4JModuleOrganization.STANDALONE
    );
    Seed4JModuleResource secondLongDependency = module(
      WrapModuleSlug.SECOND_EXTREMELY_LONG_DEPENDENCY,
      "Second very long dependency",
      Seed4JModuleOrganization.STANDALONE
    );
    Seed4JHiddenModules hiddenModules = new Seed4JHiddenModules(List.of(), List.of());

    return new Seed4JModulesResources(List.of(hardWrapModule, firstLongDependency, secondLongDependency), hiddenModules);
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
    MODULE_A("module-a"),
    HARD_WRAP_MODULE("hard-wrap-module"),
    DOUBLE_HARD_WRAP_MODULE("double-hard-wrap-module"),
    EXTREMELY_LONG_DEPENDENCY("dependency-token-with-a-very-long-slug-that-is-longer-than-sixty-characters"),
    SECOND_EXTREMELY_LONG_DEPENDENCY("another-dependency-token-with-a-very-long-slug-that-is-over-sixty-characters"),
    SHORT_DEPENDENCY("short-dependency"),
    FIRST_DEPENDENCY("first-dependency"),
    SECOND_DEPENDENCY("second-dependency"),
    THIRD_DEPENDENCY("third-dependency");

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
