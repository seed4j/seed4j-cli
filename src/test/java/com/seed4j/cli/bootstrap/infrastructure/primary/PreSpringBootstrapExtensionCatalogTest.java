package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.assertBaselineRuntimePropertiesRestored;
import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.baselineRuntimeProperties;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.infrastructure.primary.ModuleCatalogOutput.Comparison;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.ScopedRuntimeProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@UnitTest
class PreSpringBootstrapExtensionCatalogTest {

  private static final String EXTENSION_ONLY_SLUG = "runtime-extension-list-only";
  private static final String CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG = "runtime-extension-custom-package-list-only";
  private static final String CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE = "gradle-java";
  private static final String SPRING_BOOT_BANNER_MARKER = " :: Spring Boot :: ";
  private static final String STARTUP_INFO_MARKER = "Starting Seed4JCliApp";
  private static final String EXTENSION_LOGBACK_OVERRIDE_MARKER = "[EXT-LOGBACK-OVERRIDE]";
  private static final String EXTENSION_APPLICATION_OVERRIDE_MARKER = "[EXT-APPLICATION-OVERRIDE]";

  @Test
  void shouldExecuteVersionCommandInExtensionMode() throws IOException {
    ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withExtension();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      CliLaunchResult versionLaunch = fixture.launch("--version");

      assertThat(versionLaunch.exitCode()).isZero();
      assertThat(versionLaunch.output())
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0");
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldKeepStandardCatalogAndAddOnlyTheExtensionOnlySlug() throws IOException {
    ExtensionCatalogFixture standardRuntime = ExtensionCatalogFixture.standard();
    ExtensionCatalogFixture extensionRuntime = ExtensionCatalogFixture.withListExtension();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      Comparison catalogs = ModuleCatalogOutput.compare(standardRuntime.launch("list"), extensionRuntime.launch("list"));

      assertThat(catalogs.exitCodes()).containsExactly(0, 0);
      assertThat(catalogs.standardSlugs()).doesNotContain(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertThat(catalogs.extensionSlugs()).contains(EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertThat(catalogs.addedSlugs()).containsExactly(EXTENSION_ONLY_SLUG);
      assertThat(catalogs.removedSlugs()).isEmpty();
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldCompleteExtensionOnlySlugInExtensionMode() throws IOException {
    ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withListExtension();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      CliLaunchResult completionLaunch = fixture.launch("completion", "bash");

      assertThat(completionLaunch.exitCode()).isZero();
      assertThat(completionLaunch.output()).contains(EXTENSION_ONLY_SLUG);
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldListCustomPackageExtensionOnlySlug() throws IOException {
    ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withCustomPackageListExtension();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      CliLaunchResult listLaunch = fixture.launch("list");

      assertThat(listLaunch.exitCode()).isZero();
      assertThat(ModuleCatalogOutput.slugsIn(listLaunch.output())).contains(CUSTOM_PACKAGE_EXTENSION_ONLY_SLUG).doesNotHaveDuplicates();
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldKeepCoreModulesVisibleWhenExtensionPublishesHiddenResourceOverrides() throws IOException {
    ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withHiddenResourceOverrides();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      CliLaunchResult listLaunch = fixture.launch("list");

      assertThat(listLaunch.exitCode()).isZero();
      assertThat(listLaunch.output()).contains(EXTENSION_ONLY_SLUG).contains(CORE_SLUG_THAT_EXTENSION_TRIES_TO_HIDE);
      assertBaselineRuntimePropertiesRestored();
    }
  }

  @Test
  void shouldKeepOperationalOutputCleanWhenExtensionPublishesLoggingOverrides() throws IOException {
    ExtensionCatalogFixture fixture = ExtensionCatalogFixture.withRegressionOverrides();

    try (ScopedRuntimeProperties _ = baselineRuntimeProperties()) {
      CliLaunchResult versionLaunch = fixture.launch("--version");
      CliLaunchResult listLaunch = fixture.launch("list");

      assertThat(versionLaunch.exitCode()).isZero();
      assertThat(versionLaunch.output())
        .contains("Runtime mode: extension")
        .contains("Distribution ID: company-extension")
        .contains("Distribution version: 1.0.0")
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER)
        .doesNotContain("Missing watchable .xml or .properties files")
        .doesNotContain("Watching .xml files requires that the main configuration file is reachable as a URL");
      assertThat(listLaunch.exitCode()).isZero();
      assertThat(listLaunch.output())
        .contains(EXTENSION_ONLY_SLUG)
        .doesNotContain(SPRING_BOOT_BANNER_MARKER)
        .doesNotContain(STARTUP_INFO_MARKER)
        .doesNotContain(EXTENSION_LOGBACK_OVERRIDE_MARKER)
        .doesNotContain(EXTENSION_APPLICATION_OVERRIDE_MARKER)
        .doesNotContain("Missing watchable .xml or .properties files")
        .doesNotContain("Watching .xml files requires that the main configuration file is reachable as a URL");
      assertBaselineRuntimePropertiesRestored();
    }
  }
}
