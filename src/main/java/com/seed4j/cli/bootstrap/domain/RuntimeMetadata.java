package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

record RuntimeMetadata(RuntimeDistributionId distributionId, RuntimeDistributionVersion distributionVersion) {
  static RuntimeMetadata read(Path metadataPath) {
    try (InputStream metadataInputStream = Files.newInputStream(metadataPath)) {
      Object loadedMetadata = new Yaml().load(metadataInputStream);
      if (!(loadedMetadata instanceof Map<?, ?> metadata)) {
        throw invalidMetadata(metadataPath);
      }

      Map<?, ?> distribution = mapValue(metadata, "distribution", "distribution", metadataPath);

      return new RuntimeMetadata(
        new RuntimeDistributionId(stringValue(distribution, "id", "distribution.id", metadataPath)),
        new RuntimeDistributionVersion(stringValue(distribution, "version", "distribution.version", metadataPath))
      );
    } catch (IOException | YAMLException exception) {
      throw InvalidRuntimeConfigurationException.technicalError(invalidMetadataMessage(metadataPath), exception);
    } catch (RuntimeException runtimeException) {
      if (runtimeException instanceof InvalidRuntimeConfigurationException invalidRuntimeConfigurationException) {
        throw invalidRuntimeConfigurationException;
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
    return new InvalidRuntimeConfigurationException(invalidMetadataMessage(metadataPath));
  }

  private static String invalidMetadataMessage(Path metadataPath) {
    return "Invalid runtime metadata file: " + metadataPath;
  }

  private static InvalidRuntimeConfigurationException invalidMetadata(String fieldName, Path metadataPath) {
    return new InvalidRuntimeConfigurationException("Invalid " + fieldName + " in runtime metadata file: " + metadataPath);
  }
}
