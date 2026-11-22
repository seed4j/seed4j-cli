package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.ConfiguredRuntimeMode;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationReader;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public class FileSystemRuntimeModeConfigurationReader implements RuntimeModeConfigurationReader {

  private static final String CONFIG_LOCATION = "~/.config/seed4j-cli/config.yml";
  private final Seed4JCliHome cliHome;

  public FileSystemRuntimeModeConfigurationReader(Seed4JCliHome cliHome) {
    Assert.notNull("cliHome", cliHome);
    this.cliHome = cliHome;
  }

  @Override
  public ConfiguredRuntimeMode configuredMode() {
    Path configPath = cliHome.configPath();
    if (Files.notExists(configPath)) {
      return ConfiguredRuntimeMode.notConfigured();
    }

    try (InputStream input = Files.newInputStream(configPath)) {
      return configuredMode(new Yaml().load(input));
    } catch (IOException | YAMLException exception) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not read " + CONFIG_LOCATION + ".", exception);
    }
  }

  private static ConfiguredRuntimeMode configuredMode(Object configuration) {
    if (!(configuration instanceof Map<?, ?> root)) {
      throw invalidConfiguration("YAML root must be an object.");
    }
    if (root.containsKey("seed4j")) {
      return seed4jSectionMode(root.get("seed4j"));
    }
    return ConfiguredRuntimeMode.notConfigured();
  }

  private static InvalidRuntimeConfigurationException invalidConfiguration(String details) {
    return new InvalidRuntimeConfigurationException("Invalid " + CONFIG_LOCATION + ": " + details);
  }

  private static ConfiguredRuntimeMode seed4jSectionMode(Object section) {
    if (!(section instanceof Map<?, ?> seed4j)) {
      throw invalidConfiguration("seed4j must be an object.");
    }
    if (seed4j.get("runtime") instanceof Map<?, ?> runtime && runtime.containsKey("mode")) {
      return runtimeMode(runtime.get("mode"));
    }
    return ConfiguredRuntimeMode.notConfigured();
  }

  private static ConfiguredRuntimeMode runtimeMode(Object value) {
    if (!(value instanceof String mode)) {
      throw invalidConfiguration("seed4j.runtime.mode must be a string. Valid value: standard.");
    }
    return ConfiguredRuntimeMode.configured(mode);
  }
}
