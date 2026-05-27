package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class RuntimeMetadataTest {

  @Test
  void shouldMapUnexpectedRuntimeExceptionsToInvalidMetadataError() {
    assertThatThrownBy(() -> RuntimeMetadata.read(null))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessage("Invalid runtime metadata file: null");
  }

  @Test
  void shouldPreserveCauseWhenMetadataFileCannotBeOpened() throws IOException {
    Path missingMetadataPath = Files.createTempDirectory("seed4j-cli-runtime-metadata-missing-").resolve("metadata.yml");

    assertThatThrownBy(() -> RuntimeMetadata.read(missingMetadataPath))
      .isExactlyInstanceOf(InvalidRuntimeConfigurationException.class)
      .hasMessageContaining("Invalid runtime metadata file: " + missingMetadataPath)
      .hasMessageContaining("Details:")
      .hasCauseInstanceOf(IOException.class);
  }
}
