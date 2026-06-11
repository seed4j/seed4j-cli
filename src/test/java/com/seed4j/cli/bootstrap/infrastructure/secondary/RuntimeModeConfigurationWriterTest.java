package com.seed4j.cli.bootstrap.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeModeConfigurationDocument;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@UnitTest
class RuntimeModeConfigurationWriterTest {

  @Test
  void shouldDeleteTemporaryFileAndRethrowWhenWritingTemporaryContentFails() {
    Path configPath = Path.of("/tmp/seed4j-cli/config.yml");
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    AtomicReference<Path> temporaryPath = new AtomicReference<>();

    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.createDirectories(any(Path.class))).thenAnswer(invocation -> invocation.getArgument(0));
      files
        .when(() -> Files.writeString(any(Path.class), anyString()))
        .thenAnswer(invocation -> {
          Path path = invocation.getArgument(0);
          temporaryPath.set(path);
          throw new IOException("cannot write temporary file");
        });
      files.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

      assertThatThrownBy(() -> RuntimeModeConfigurationWriter.writeMode(configPath, currentConfiguration, RuntimeMode.STANDARD))
        .isExactlyInstanceOf(IOException.class)
        .hasMessage("cannot write temporary file");

      files.verify(() -> Files.deleteIfExists(temporaryPath.get()));
    }
  }

  @Test
  void shouldFallbackToReplaceExistingMoveWhenAtomicMoveIsNotSupported() throws IOException {
    Path configPath = Path.of("/tmp/seed4j-cli/config.yml");
    RuntimeModeConfigurationDocument currentConfiguration = new RuntimeModeConfigurationDocument(new LinkedHashMap<>());
    AtomicReference<Path> temporaryPath = new AtomicReference<>();

    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.createDirectories(any(Path.class))).thenAnswer(invocation -> invocation.getArgument(0));
      files
        .when(() -> Files.writeString(any(Path.class), anyString()))
        .thenAnswer(invocation -> {
          Path path = invocation.getArgument(0);
          temporaryPath.set(path);
          return path;
        });
      files
        .when(() ->
          Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.ATOMIC_MOVE), eq(StandardCopyOption.REPLACE_EXISTING))
        )
        .thenThrow(new AtomicMoveNotSupportedException("source", "target", "atomic move not supported"));
      files
        .when(() -> Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.REPLACE_EXISTING)))
        .thenAnswer(invocation -> invocation.getArgument(1));

      RuntimeModeConfigurationWriter.writeMode(configPath, currentConfiguration, RuntimeMode.EXTENSION);

      files.verify(() -> Files.move(temporaryPath.get(), configPath, StandardCopyOption.REPLACE_EXISTING));
    }
  }
}
