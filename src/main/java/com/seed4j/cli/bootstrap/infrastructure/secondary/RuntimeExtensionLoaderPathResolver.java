package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.RuntimeExtensionMissingLibrariesSelector;
import com.seed4j.cli.bootstrap.domain.RuntimeLibraryEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeExtensionLoaderPathResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExtensionLoaderPathResolver.class);
  private final RuntimeExtensionMissingLibrariesSelector missingLibrariesSelector = new RuntimeExtensionMissingLibrariesSelector();
  private final NestedRuntimeLibraryReader nestedRuntimeLibraryReader = new NestedRuntimeLibraryReader();

  public String resolve(Path overlayClassesPath, Path extensionJarPath, Path executableJarPath) {
    List<RuntimeLibraryEntry> extensionLibraries = nestedRuntimeLibraryReader.extensionLibraries(extensionJarPath);
    Set<RuntimeLibraryEntry> cliLibraries = Set.copyOf(nestedRuntimeLibraryReader.cliLibraries(executableJarPath));
    List<String> missingExtensionLibraries = missingLibrariesSelector.select(extensionLibraries, cliLibraries);
    if (missingExtensionLibraries.isEmpty()) {
      LOGGER.debug("No extension runtime libraries were added to loader.path from {}", extensionJarPath);
      return overlayClassesPath.toString();
    }

    LOGGER.debug("Extension runtime libraries added to loader.path from {}: {}", extensionJarPath, missingExtensionLibraries);

    return Stream.concat(
      Stream.of(overlayClassesPath.toString()),
      missingExtensionLibraries
        .stream()
        .map(
          missingExtensionLibrary ->
            "jar:" + extensionJarPath.toUri() + "!/" + NestedRuntimeLibraryReader.BOOT_INF_LIB_DIRECTORY + missingExtensionLibrary
        )
    ).collect(Collectors.joining(","));
  }
}
