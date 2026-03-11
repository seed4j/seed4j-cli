package com.seed4j.cli.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

record RuntimeMetadata(
  String distributionId,
  String distributionVersion,
  String distributionVendor,
  String distributionKind,
  String artifactFilename,
  String compatibilityCli
) {
  static RuntimeMetadata read(Path metadataPath) {
    try {
      Object loadedMetadata = new Yaml().load(Files.newInputStream(metadataPath));
      if (!(loadedMetadata instanceof Map<?, ?> metadata)) {
        throw invalidMetadata(metadataPath);
      }

      Map<?, ?> distribution = mapValue(metadata, "distribution", "distribution.kind", metadataPath);
      Map<?, ?> artifact = mapValue(metadata, "artifact", "artifact.filename", metadataPath);
      Map<?, ?> compatibility = mapValue(metadata, "compatibility", "compatibility.cli", metadataPath);

      return new RuntimeMetadata(
        stringValue(distribution, "id", "distribution.id", metadataPath),
        stringValue(distribution, "version", "distribution.version", metadataPath),
        stringValue(distribution, "vendor", "distribution.vendor", metadataPath),
        stringValue(distribution, "kind", "distribution.kind", metadataPath),
        stringValue(artifact, "filename", "artifact.filename", metadataPath),
        stringValue(compatibility, "cli", "compatibility.cli", metadataPath)
      );
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidRuntimeConfigurationException runtimeConfigurationException) {
        throw runtimeConfigurationException;
      }

      throw invalidMetadata(metadataPath);
    }
  }

  private static Map<?, ?> mapValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof Map<?, ?> mapValue)) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return mapValue;
  }

  private static String stringValue(Map<?, ?> source, String key, String fieldName, Path metadataPath) {
    Object value = source.get(key);
    if (!(value instanceof String stringValue) || stringValue.isBlank()) {
      throw invalidMetadata(fieldName, metadataPath);
    }

    return stringValue;
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid runtime metadata file: " + metadataPath);
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(String fieldName, Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid " + fieldName + " in runtime metadata file: " + metadataPath);
  }
}
