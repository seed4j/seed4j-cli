package com.seed4j.cli.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

record RuntimeMetadata(String distributionKind, String artifactFilename) {
  static RuntimeMetadata read(Path metadataPath) {
    try {
      Object loadedMetadata = new Yaml().load(Files.newInputStream(metadataPath));
      if (!(loadedMetadata instanceof Map<?, ?> metadata)) {
        throw invalidMetadata(metadataPath);
      }

      Map<?, ?> distribution = mapValue(metadata, "distribution", metadataPath);
      Map<?, ?> artifact = mapValue(metadata, "artifact", metadataPath);

      return new RuntimeMetadata(stringValue(distribution, "kind", metadataPath), stringValue(artifact, "filename", metadataPath));
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidRuntimeConfigurationException runtimeConfigurationException) {
        throw runtimeConfigurationException;
      }

      throw invalidMetadata(metadataPath);
    }
  }

  private static Map<?, ?> mapValue(Map<?, ?> source, String key, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof Map<?, ?> mapValue)) {
      throw invalidMetadata(metadataPath);
    }

    return mapValue;
  }

  private static String stringValue(Map<?, ?> source, String key, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw invalidMetadata(metadataPath);
    }

    return stringValue;
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
  }
}
