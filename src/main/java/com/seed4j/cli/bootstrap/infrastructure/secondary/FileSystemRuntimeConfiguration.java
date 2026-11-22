package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeConfiguration;
import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public class FileSystemRuntimeConfiguration implements RuntimeConfiguration {

  private static final String CONFIG_LOCATION = "~/.config/seed4j-cli/config.yml";
  private final Seed4JCliHome cliHome;

  public FileSystemRuntimeConfiguration(Seed4JCliHome cliHome) {
    this.cliHome = cliHome;
  }

  @Override
  public void requireStandardMode() {
    Path configPath = cliHome.configPath();
    if (Files.notExists(configPath)) {
      return;
    }

    try (InputStream input = Files.newInputStream(configPath)) {
      validateConfiguration(new Yaml().load(input));
    } catch (IOException | YAMLException exception) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not read " + CONFIG_LOCATION + ".", exception);
    }
  }

  private static void validateConfiguration(Object configuration) {
    if (!(configuration instanceof Map<?, ?> root)) {
      throw invalidConfiguration("YAML root must be an object.");
    }
    if (root.containsKey("seed4j")) {
      validateSeed4jSection(root.get("seed4j"));
    }
  }

  private static InvalidRuntimeConfigurationException invalidConfiguration(String details) {
    return new InvalidRuntimeConfigurationException("Invalid " + CONFIG_LOCATION + ": " + details);
  }

  private static void validateSeed4jSection(Object section) {
    if (!(section instanceof Map<?, ?> seed4j)) {
      throw invalidConfiguration("seed4j must be an object.");
    }
    if (seed4j.get("runtime") instanceof Map<?, ?> runtime && runtime.containsKey("mode")) {
      validateMode(runtime.get("mode"));
    }
  }

  private static void validateMode(Object value) {
    if (!(value instanceof String mode)) {
      throw invalidConfiguration("seed4j.runtime.mode must be a string. Valid value: standard.");
    }
    String normalizedMode = mode.trim();
    if ("extension".equalsIgnoreCase(normalizedMode)) {
      throw new InvalidRuntimeConfigurationException(
        "Loading runtime extension JARs is no longer supported. Set seed4j.runtime.mode: standard in " + CONFIG_LOCATION + "."
      );
    }
    if (!"standard".equalsIgnoreCase(normalizedMode)) {
      throw invalidConfiguration("Invalid seed4j.runtime.mode '%s'. Valid value: standard.".formatted(mode));
    }
  }
}
