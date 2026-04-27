package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeExtensionLoaderPathResolverTest {

  @Test
  void shouldResolveLoaderPathWithBootInfClassesAndLibEntriesForExtensionFatJar() {
    Path extensionJarPath = Path.of("/opt/seed4j/runtime/active/extension.jar");
    RuntimeExtensionLoaderPathResolver resolver = new RuntimeExtensionLoaderPathResolver();

    String loaderPath = resolver.resolve(extensionJarPath);

    assertThat(loaderPath).isEqualTo(
      "jar:file:///opt/seed4j/runtime/active/extension.jar!/BOOT-INF/classes,jar:file:///opt/seed4j/runtime/active/extension.jar!/BOOT-INF/lib/"
    );
  }
}
