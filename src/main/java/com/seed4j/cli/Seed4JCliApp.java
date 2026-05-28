package com.seed4j.cli;

import com.seed4j.Seed4JApp;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapEntryPoint;
import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringLauncherAssembler;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackageClasses = { Seed4JApp.class, Seed4JCliApp.class })
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class Seed4JCliApp {

  private static final String CHILD_MODE_PROPERTY = "seed4j.cli.runtime.child";
  private static final String DEFAULT_CLI_VERSION = "0.0.0-SNAPSHOT";

  interface BootstrapEntryPoint {
    int launch(String[] args);
  }

  interface ExitHandler {
    void exit(int exitCode);
  }

  interface ProductionBootstrapEntryPointFactory {
    BootstrapEntryPoint create();
  }

  interface PreSpringBootstrapEntryPointFactory {
    BootstrapEntryPoint create(Path userHomePath, boolean childMode);
  }

  static void main(String[] args) {
    runProductionPath(args, () -> productionBootstrapEntryPoint(userHomePath(), childMode()), System::exit);
  }

  static void runProductionPath(String[] args, ProductionBootstrapEntryPointFactory bootstrapEntryPointFactory, ExitHandler exitHandler) {
    int exitCode = bootstrapEntryPointFactory.create().launch(args);
    exitHandler.exit(exitCode);
  }

  static BootstrapEntryPoint productionBootstrapEntryPoint(Path userHomePath, boolean childMode) {
    return productionBootstrapEntryPoint(userHomePath, childMode, (requestedUserHomePath, requestedChildMode) ->
      toBootstrapEntryPoint(
        new PreSpringLauncherAssembler().assemble(requestedUserHomePath, executablePath(), currentSeed4JVersion(), requestedChildMode)
      )
    );
  }

  static BootstrapEntryPoint productionBootstrapEntryPoint(
    Path userHomePath,
    boolean childMode,
    PreSpringBootstrapEntryPointFactory preSpringBootstrapEntryPointFactory
  ) {
    return preSpringBootstrapEntryPointFactory.create(userHomePath, childMode);
  }

  private static BootstrapEntryPoint toBootstrapEntryPoint(PreSpringBootstrapEntryPoint preSpringBootstrapEntryPoint) {
    return preSpringBootstrapEntryPoint::launch;
  }

  private static Path userHomePath() {
    return Path.of(System.getProperty("user.home"));
  }

  private static Path executablePath() {
    try {
      Path codeSourcePath = Path.of(Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return resolveExecutablePath(
        codeSourcePath,
        System.getProperty("sun.java.command", ""),
        System.getProperty("java.class.path", ""),
        currentWorkingDirectory()
      );
    } catch (URISyntaxException uriSyntaxException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not resolve executable path.", uriSyntaxException);
    }
  }

  static Path resolveExecutablePath(Path codeSourcePath, String javaCommand, String javaClassPath, Path workingDirectory) {
    if (Files.isRegularFile(codeSourcePath) && codeSourcePath.getFileName().toString().endsWith(".jar")) {
      return codeSourcePath;
    }

    Optional<Path> executablePathFromCommand = executablePathFromJavaCommand(javaCommand, workingDirectory);
    if (executablePathFromCommand.isPresent()) {
      return executablePathFromCommand.orElseThrow();
    }

    String pathSeparator = System.getProperty("path.separator");
    return Optional.ofNullable(javaClassPath)
      .filter(classPath -> !classPath.isBlank())
      .stream()
      .flatMap(classPath -> Arrays.stream(classPath.split(java.util.regex.Pattern.quote(pathSeparator))))
      .map(String::trim)
      .map(Seed4JCliApp::regularJarPath)
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
      .flatMap(Seed4JCliApp::regularJarPath);
  }

  private static Optional<Path> regularJarPath(String candidatePath) {
    return Optional.ofNullable(candidatePath)
      .filter(path -> path.endsWith(".jar"))
      .map(Path::of)
      .filter(Files::isRegularFile);
  }

  private static Path currentWorkingDirectory() {
    return Path.of(System.getProperty("user.dir"));
  }

  private static String currentSeed4JVersion() {
    return Optional.ofNullable(Seed4JApp.class.getPackage().getImplementationVersion())
      .filter(version -> !version.isBlank())
      .orElse(DEFAULT_CLI_VERSION);
  }

  private static boolean childMode() {
    return Boolean.parseBoolean(System.getProperty(CHILD_MODE_PROPERTY));
  }
}
