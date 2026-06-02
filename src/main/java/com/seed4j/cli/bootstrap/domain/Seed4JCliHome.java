package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;

public record Seed4JCliHome(Path path) {
  public Seed4JCliHome {
    Assert.notNull("path", path);
  }

  public static Seed4JCliHome from(String path) {
    Assert.notBlank("path", path);
    return new Seed4JCliHome(Path.of(path));
  }

  public Path configPath() {
    return path.resolve(".config/seed4j-cli/config.yml");
  }

  public RuntimeExtensionConfiguration runtimeExtensionConfiguration() {
    return new RuntimeExtensionConfiguration(
      path.resolve(".config/seed4j-cli/runtime/active/extension.jar"),
      path.resolve(".config/seed4j-cli/runtime/active/metadata.yml")
    );
  }

  public Path runtimeCacheDirectory() {
    return path.resolve(".config/seed4j-cli/runtime/cache");
  }
}
