package com.seed4j.cli.bootstrap.infrastructure.secondary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.ProcessCommandExecutor;
import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.util.List;

public class JavaChildProcessCommandExecutor implements ProcessCommandExecutor {

  @Override
  public int execute(List<String> command) {
    Process childProcess = startChildProcess(command);
    return waitForExitCode(childProcess);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Process spawn I/O failure path depends on operating system and environment")
  private static Process startChildProcess(List<String> command) {
    try {
      return new ProcessBuilder(command).inheritIO().start();
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not launch child process.", ioException);
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Process wait interruption path depends on thread scheduling and interruption timing")
  private static int waitForExitCode(Process childProcess) {
    try {
      return childProcess.waitFor();
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw InvalidRuntimeConfigurationException.technicalError("Child process execution was interrupted.", interruptedException);
    }
  }
}
