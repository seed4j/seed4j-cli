package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.Seed4JCliApp;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironment;
import com.seed4j.cli.bootstrap.application.PreSpringRuntimeEnvironmentReader;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public class CurrentProcessPreSpringRuntimeEnvironmentReader implements PreSpringRuntimeEnvironmentReader {

  private static final String CHILD_MODE_PROPERTY = "seed4j.cli.runtime.child";
  private static final String DEFAULT_CLI_VERSION = "0.0.0-SNAPSHOT";

  @Override
  public PreSpringRuntimeEnvironment current() {
    try {
      Path codeSourcePath = Path.of(Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return new PreSpringRuntimeEnvironment(
        Path.of(System.getProperty("user.home")),
        resolveExecutablePath(
          codeSourcePath,
          System.getProperty("sun.java.command", ""),
          System.getProperty("java.class.path", ""),
          Path.of(System.getProperty("user.dir"))
        ),
        currentSeed4JVersion(),
        Boolean.parseBoolean(System.getProperty(CHILD_MODE_PROPERTY)),
        Path.of(System.getProperty("java.home"), "bin", "java")
      );
    } catch (URISyntaxException uriSyntaxException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not resolve executable path.", uriSyntaxException);
    }
  }

  private static String currentSeed4JVersion() {
    return resolveCurrentSeed4JVersion(Seed4JApp.class.getPackage().getImplementationVersion());
  }

  static String resolveCurrentSeed4JVersion(String implementationVersion) {
    return Optional.ofNullable(implementationVersion)
      .filter(version -> !version.isBlank())
      .orElse(DEFAULT_CLI_VERSION);
  }

  static Path resolveExecutablePath(Path codeSourcePath, String javaCommand, String javaClassPath, Path workingDirectory) {
    if (Files.isRegularFile(codeSourcePath) && codeSourcePath.getFileName().toString().endsWith(".jar")) {
      return codeSourcePath;
    }

    Optional<Path> executablePathFromCommand = executablePathFromJavaCommand(javaCommand, workingDirectory);
    if (executablePathFromCommand.isPresent()) {
      return executablePathFromCommand.orElseThrow();
    }

    return Optional.ofNullable(javaClassPath)
      .filter(classPath -> !classPath.isBlank())
      .stream()
      .flatMap(classPath -> Arrays.stream(classPath.split(java.util.regex.Pattern.quote(File.pathSeparator))))
      .map(String::trim)
      .map(CurrentProcessPreSpringRuntimeEnvironmentReader::regularJarPath)
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(codeSourcePath);
  }

  private static Optional<Path> executablePathFromJavaCommand(String javaCommand, Path workingDirectory) {
    return Optional.ofNullable(javaCommand)
      .map(String::trim)
      .filter(command -> !command.isEmpty())
      .map(command -> command.split("\\s+", 2)[0])
      .map(Path::of)
      .map(path -> path.isAbsolute() ? path : workingDirectory.resolve(path).normalize())
      .map(Path::toString)
      .flatMap(CurrentProcessPreSpringRuntimeEnvironmentReader::regularJarPath);
  }

  private static Optional<Path> regularJarPath(String candidatePath) {
    return Optional.ofNullable(candidatePath)
      .filter(path -> path.endsWith(".jar"))
      .map(Path::of)
      .filter(Files::isRegularFile);
  }
}
