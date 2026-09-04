package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionOverlayCacheResourceFilteringTest {

  @Test
  void shouldFilterGlobalRuntimeResourcesAndKeepFunctionalResourcesInOverlay() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestResource("config/application.yml", "name: ext"),
      new TestResource("config/application-prod.yaml", "name: ext"),
      new TestResource("config/application.properties", "name=ext"),
      new TestResource("logback-spring.xml", "<configuration/>"),
      new TestResource("generator/runtime-extension/messages/template.yaml", "template-content")
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("config/application.yml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("config/application-prod.yaml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("config/application.properties")).doesNotExist();
    assertThat(overlayClassesPath.resolve("logback-spring.xml")).doesNotExist();
    assertThat(overlayClassesPath.resolve("generator/runtime-extension/messages/template.yaml")).exists().hasContent("template-content");
  }

  private static Path createExtensionJar(Path jarPath, TestResource... resources) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      for (TestResource resource : resources) {
        jarOutputStream.putNextEntry(new JarEntry("BOOT-INF/classes/" + resource.path()));
        jarOutputStream.write(resource.content().getBytes(StandardCharsets.UTF_8));
        jarOutputStream.closeEntry();
      }
    }
    return jarPath;
  }

  private record TestResource(String path, String content) {}

  @Test
  void shouldKeepNonXmlRootLogbackResourcesInOverlay() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestResource("logback-spring.txt", "not-xml-logback-resource")
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("logback-spring.txt")).exists().hasContent("not-xml-logback-resource");
  }

  @Test
  void shouldKeepApplicationPrefixedResourceWithNonConfigurationSuffix() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-");
    Path extensionJarPath = createExtensionJar(
      Files.createTempFile("company-extension-", ".jar"),
      new TestResource("config/application-prod.txt", "not-configuration")
    );
    RuntimeExtensionOverlayCache overlayCache = new RuntimeExtensionOverlayCache(new Seed4JCliHome(userHome));

    Path overlayClassesPath = overlayCache.materialize(extensionJarPath);

    assertThat(overlayClassesPath.resolve("config/application-prod.txt")).exists().hasContent("not-configuration");
  }
}
