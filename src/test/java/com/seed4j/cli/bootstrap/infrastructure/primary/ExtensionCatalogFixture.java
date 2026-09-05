package com.seed4j.cli.bootstrap.infrastructure.primary;

import static com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.launchCapturingOutput;

import com.seed4j.cli.bootstrap.fixture.ExtensionRuntimeFixture;
import com.seed4j.cli.bootstrap.infrastructure.primary.ModuleCatalogOutput.Comparison;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ExtensionCatalogFixture {

  private final PreSpringBootstrapRunner runner;

  private ExtensionCatalogFixture(Path userHome) throws IOException {
    runner = InProcessChildRuntimeLauncher.runner(userHome);
  }

  static ExtensionCatalogFixture standard() throws IOException {
    return new ExtensionCatalogFixture(Files.createTempDirectory("seed4j-cli-standard-catalog-primary-"));
  }

  static Comparison compareStandardWithListExtensionCatalogs() throws IOException {
    ExtensionCatalogFixture standardRuntime = standard();
    ExtensionCatalogFixture extensionRuntime = withListExtension();
    return ModuleCatalogOutput.compare(standardRuntime.launch("list"), extensionRuntime.launch("list"));
  }

  static ExtensionCatalogFixture withExtension() throws IOException {
    return withRuntime("version", ExtensionRuntimeFixture::install);
  }

  static ExtensionCatalogFixture withListExtension() throws IOException {
    return withRuntime("extension-catalog", ExtensionRuntimeFixture::installWithListExtensionModule);
  }

  static ExtensionCatalogFixture withCustomPackageListExtension() throws IOException {
    return withRuntime("custom-extension-catalog", ExtensionRuntimeFixture::installWithCustomPackageListExtensionModule);
  }

  static ExtensionCatalogFixture withHiddenResourceOverrides() throws IOException {
    return withRuntime("extension-hidden-resources", ExtensionRuntimeFixture::installWithListExtensionModuleAndHiddenResourcesOverrides);
  }

  static ExtensionCatalogFixture withRegressionOverrides() throws IOException {
    return withRuntime("extension-logging", ExtensionRuntimeFixture::installWithListExtensionModuleAndRegressionOverrides);
  }

  private static ExtensionCatalogFixture withRuntime(String temporaryDirectoryName, RuntimeInstaller runtimeInstaller) throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-" + temporaryDirectoryName + "-primary-");
    runtimeInstaller.install(userHome);
    return new ExtensionCatalogFixture(userHome);
  }

  CliLaunchResult launch(String... arguments) {
    return launchCapturingOutput(runner, arguments);
  }

  VersionAndListLaunch launchVersionAndList() {
    return new VersionAndListLaunch(launch("--version"), launch("list"));
  }

  record VersionAndListLaunch(CliLaunchResult version, CliLaunchResult list) {
    List<Integer> exitCodes() {
      return List.of(version.exitCode(), list.exitCode());
    }

    String combinedOutput() {
      return version.output() + list.output();
    }
  }

  @FunctionalInterface
  private interface RuntimeInstaller {
    void install(Path userHome) throws IOException;
  }
}
